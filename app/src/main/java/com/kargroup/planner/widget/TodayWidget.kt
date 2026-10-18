package com.kargroup.planner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kargroup.planner.KarApp
import com.kargroup.planner.MainActivity
import com.kargroup.planner.R
import com.kargroup.planner.ui.TaskIndex
import com.kargroup.planner.ui.buildSchedule
import com.kargroup.planner.ui.formatMillisTime24
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: the next three activities from the Scheduler. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Re-draws every placed widget (called whenever activities change). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, TodayWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as KarApp
            val tasks = app.repository.allTasksOnce()
            val meetings = app.repository.meetings.first()
            val now = System.currentTimeMillis()
            val plan = buildSchedule(TaskIndex(tasks), app.appPrefs.getBoolean("schedule_manual", false), app.settings.current, meetings, now)
            val next = plan.items.filter { it.endMillis > now }.take(3)
            val today = Ist.todayStr()
            val lines = next.map { si ->
                val dayLabel = if (si.day == today) "" else Ist.friendly(si.day) + " "
                "${dayLabel}${formatMillisTime24(si.startMillis)}  ${si.task.text}".uppercase()
            }
            val open = PendingIntent.getActivity(
                context, 77,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            ids.forEach { id ->
                val v = RemoteViews(context.packageName, R.layout.widget_today)
                v.setTextViewText(R.id.widget_date, Ist.now().format(Ist.headerDateFormat).uppercase())
                val slots = listOf(R.id.widget_item1, R.id.widget_item2, R.id.widget_item3)
                slots.forEachIndexed { i, viewId ->
                    val line = lines.getOrNull(i)
                    v.setViewVisibility(viewId, if (line == null) View.GONE else View.VISIBLE)
                    if (line != null) v.setTextViewText(viewId, "• $line")
                }
                v.setViewVisibility(R.id.widget_empty, if (lines.isEmpty()) View.VISIBLE else View.GONE)
                v.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}
