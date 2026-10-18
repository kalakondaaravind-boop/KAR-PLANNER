package com.kargroup.planner.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kargroup.planner.KarApp
import com.kargroup.planner.MainActivity
import com.kargroup.planner.R
import com.kargroup.planner.data.Priority
import com.kargroup.planner.util.Ist
import java.util.concurrent.TimeUnit

object ReminderNotifications {
    const val CHANNEL_ID = "kar_reminders"
    const val NOTIFICATION_ID = 4101
    const val ACTION_SNOOZE = "com.kargroup.planner.action.SNOOZE"
    const val ACTION_DISMISS = "com.kargroup.planner.action.DISMISS"
    const val EXTRA_IDS = "task_ids"
    const val EXTRA_OPEN_REMINDERS = "open_reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Activity reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Due, overdue and high-priority activities" }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "kar_reminder_check",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS).build(),
        )
    }

    /** Re-check after a snooze expires. */
    fun scheduleOneOff(context: Context, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "kar_reminder_snooze",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delayMinutes, TimeUnit.MINUTES).build(),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as KarApp
        val today = Ist.todayStr()
        val alerts = Reminders.alerts(app.repository.allTasksOnce(), app.reminderPrefs.suppressedIds(), today)
        val nm = NotificationManagerCompat.from(applicationContext)

        if (alerts.isEmpty()) {
            nm.cancel(ReminderNotifications.NOTIFICATION_ID)
            return Result.success()
        }
        if (!ReminderNotifications.canPost(applicationContext)) return Result.success()

        val ids = alerts.map { it.id }.toTypedArray()
        val overdue = alerts.count { (it.dueDate ?: today) < today }
        val title = when {
            alerts.size == 1 -> alerts.first().text
            else -> "${alerts.size} activities need attention"
        }
        val summary = buildList {
            if (overdue > 0) add("$overdue overdue")
            val dueToday = alerts.count { it.dueDate == today }
            if (dueToday > 0) add("$dueToday due today")
            val high = alerts.count { Priority.isHighOrAbove(it.priority) }
            if (high > 0) add("$high high priority")
        }.joinToString(" · ")

        val style = NotificationCompat.InboxStyle().setSummaryText(summary.uppercase())
        alerts.take(6).forEach { t ->
            val tag = when {
                (t.dueDate ?: today) < today -> "Overdue"
                t.dueDate == today -> "Today"
                else -> Ist.friendly(t.dueDate)
            }
            style.addLine("• ${t.text}  ($tag)".uppercase())
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ReminderNotifications.EXTRA_OPEN_REMINDERS, true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentPi = PendingIntent.getActivity(applicationContext, 10, openIntent, flags)
        val snoozePi = PendingIntent.getBroadcast(
            applicationContext, 11,
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(ReminderNotifications.ACTION_SNOOZE)
                .putExtra(ReminderNotifications.EXTRA_IDS, ids),
            flags,
        )
        val dismissPi = PendingIntent.getBroadcast(
            applicationContext, 12,
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(ReminderNotifications.ACTION_DISMISS)
                .putExtra(ReminderNotifications.EXTRA_IDS, ids),
            flags,
        )

        val notification = NotificationCompat.Builder(applicationContext, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kar)
            .setColor(0xFF0284C7.toInt())
            .setContentTitle(title.uppercase())
            .setContentText(summary.ifBlank { "Tap to review your activities" }.uppercase())
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, "SNOOZE 1 HOUR", snoozePi)
            .addAction(0, "DISMISS", dismissPi)
            .build()

        nm.notify(ReminderNotifications.NOTIFICATION_ID, notification)
        return Result.success()
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as KarApp
        val ids = intent.getStringArrayExtra(ReminderNotifications.EXTRA_IDS)?.toList().orEmpty()
        when (intent.action) {
            ReminderNotifications.ACTION_SNOOZE -> {
                app.reminderPrefs.snooze(ids)
                ReminderNotifications.scheduleOneOff(context, 61)
            }
            ReminderNotifications.ACTION_DISMISS -> app.reminderPrefs.dismiss(ids)
        }
        NotificationManagerCompat.from(context).cancel(ReminderNotifications.NOTIFICATION_ID)
    }
}
