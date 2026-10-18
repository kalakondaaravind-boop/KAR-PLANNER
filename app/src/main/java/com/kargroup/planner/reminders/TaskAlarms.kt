package com.kargroup.planner.reminders

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kargroup.planner.KarApp
import com.kargroup.planner.MainActivity
import com.kargroup.planner.R
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.util.Ist
import com.kargroup.planner.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Exact reminders: one alarm per open activity with a target date + time, firing
 * [PlannerSettings.reminderLeadMinutes] before the target (IST).
 */
object TaskAlarms {
    private const val PREFS = "kar_task_alarms"
    private const val KEY_IDS = "scheduled_ids"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TARGET = "target"
    const val ACTION_FIRE = "com.kargroup.planner.action.TASK_ALARM"
    const val ACTION_SNOOZE = "com.kargroup.planner.action.TASK_ALARM_SNOOZE"
    private const val HORIZON_MS = 8L * 24 * 3_600_000L

    fun targetMillis(t: TaskEntity): Long? {
        val d = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val tm = t.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return null
        return d.atTime(tm).atZone(Ist.ZONE).toInstant().toEpochMilli()
    }

    fun rescheduleAll(context: Context, tasks: List<TaskEntity>, settings: PlannerSettings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = sp.getStringSet(KEY_IDS, emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        val wanted = HashMap<String, Pair<Long, TaskEntity>>()
        tasks.filter { !it.done }.forEach { t ->
            val target = targetMillis(t) ?: return@forEach
            val fireAt = target - settings.reminderLeadMinutes * 60_000L
            if (fireAt > now && fireAt < now + HORIZON_MS) wanted[t.id] = fireAt to t
        }
        // Cancel alarms for activities that no longer need one.
        (previous - wanted.keys).forEach { id -> am.cancel(pending(context, id, null, null, 0L, create = true)) }
        wanted.forEach { (id, pair) ->
            val (fireAt, t) = pair
            val pi = pending(context, id, t.text, t.dueTime, targetMillis(t) ?: fireAt, create = true)
            setAlarm(am, fireAt, pi, settings.exactReminders)
        }
        sp.edit().putStringSet(KEY_IDS, wanted.keys).apply()
    }

    fun snooze(context: Context, taskId: String, title: String?, target: Long, minutes: Long = 15) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context, taskId, title, null, target, create = true)
        setAlarm(am, System.currentTimeMillis() + minutes * 60_000L, pi, true)
    }

    @SuppressLint("MissingPermission")
    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent, exact: Boolean) {
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact && canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun pending(context: Context, id: String, title: String?, time: String?, target: Long, create: Boolean): PendingIntent {
        val intent = Intent(context, TaskAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(android.net.Uri.parse("kar://task/$id"))
            .putExtra(EXTRA_TASK_ID, id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_TARGET, target)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else 0)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
    }
}

/** Shows the "due soon" notification for one activity. */
class TaskAlarmReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(TaskAlarms.EXTRA_TASK_ID) ?: return
        val target = intent.getLongExtra(TaskAlarms.EXTRA_TARGET, 0L)
        if (intent.action == TaskAlarms.ACTION_SNOOZE) {
            NotificationManagerCompat.from(context).cancel(id.hashCode())
            TaskAlarms.snooze(context, id, intent.getStringExtra(TaskAlarms.EXTRA_TITLE), target)
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as KarApp
                val task = app.database.taskDao().getById(id)
                if (task == null || task.done) return@launch
                if (!ReminderNotifications.canPost(context)) return@launch
                val whenText = if (target > 0) {
                    val mins = ((target - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                    if (mins <= 1) "DUE NOW" else "DUE IN $mins MIN · ${task.dueTime ?: ""}"
                } else "REMINDER"
                val open = PendingIntent.getActivity(
                    context, id.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(ReminderNotifications.EXTRA_OPEN_REMINDERS, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val snooze = PendingIntent.getBroadcast(
                    context, id.hashCode() + 1,
                    Intent(context, TaskAlarmReceiver::class.java)
                        .setAction(TaskAlarms.ACTION_SNOOZE)
                        .setData(android.net.Uri.parse("kar://snooze/$id"))
                        .putExtra(TaskAlarms.EXTRA_TASK_ID, id)
                        .putExtra(TaskAlarms.EXTRA_TITLE, task.text)
                        .putExtra(TaskAlarms.EXTRA_TARGET, target),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_kar)
                    .setColor(0xFF0284C7.toInt())
                    .setContentTitle(task.text.uppercase())
                    .setContentText(whenText + (task.assignee?.let { " · ${it.uppercase()}" } ?: ""))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .addAction(0, "SNOOZE 15 MIN", snooze)
                    .build()
                NotificationManagerCompat.from(context).notify(id.hashCode(), n)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Restores exact reminders and the widget after the tablet restarts. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as KarApp
                TaskAlarms.rescheduleAll(context, app.repository.allTasksOnce(), app.settings.current)
                TodayWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
