package com.kargroup.planner.reminders

import android.content.Context
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.LocalDate

/** Stores per-task "Snooze 1 Hour" and "Dismiss (for today)" choices. */
class ReminderPrefs(context: Context) {
    private val sp = context.getSharedPreferences("kar_reminders", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0L)

    /** Bumps whenever snooze/dismiss state changes so the UI can recompute. */
    val version: StateFlow<Long> = _version

    @Synchronized
    fun snooze(ids: Collection<String>, hours: Long = 1) {
        val until = System.currentTimeMillis() + hours * 3_600_000L
        val o = read(KEY_SNOOZED)
        ids.forEach { o.put(it, until) }
        write(KEY_SNOOZED, o)
    }

    @Synchronized
    fun dismiss(ids: Collection<String>) {
        val today = Ist.todayStr()
        val o = read(KEY_DISMISSED)
        ids.forEach { o.put(it, today) }
        write(KEY_DISMISSED, o)
    }

    @Synchronized
    fun suppressedIds(nowMs: Long = System.currentTimeMillis(), today: String = Ist.todayStr()): Set<String> {
        val out = HashSet<String>()
        val snoozed = read(KEY_SNOOZED)
        snoozed.keys().forEach { k -> if (snoozed.optLong(k, 0L) > nowMs) out.add(k) }
        val dismissed = read(KEY_DISMISSED)
        dismissed.keys().forEach { k -> if (dismissed.optString(k, "") == today) out.add(k) }
        return out
    }

    @Synchronized
    fun clear() {
        sp.edit().clear().apply()
        _version.value = _version.value + 1
    }

    private fun read(key: String): JSONObject =
        runCatching { JSONObject(sp.getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())

    private fun write(key: String, value: JSONObject) {
        // Drop expired entries so the store stays small.
        val now = System.currentTimeMillis()
        val today = Ist.todayStr()
        val keys = value.keys().asSequence().toList()
        keys.forEach { k ->
            val stale = if (key == KEY_SNOOZED) value.optLong(k, 0L) <= now else value.optString(k, "") != today
            if (stale) value.remove(k)
        }
        sp.edit().putString(key, value.toString()).apply()
        _version.value = _version.value + 1
    }

    companion object {
        private const val KEY_SNOOZED = "snoozed"
        private const val KEY_DISMISSED = "dismissed"
    }
}

object Reminders {
    /**
     * Tasks that deserve a reminder: open tasks that are overdue or due today (IST),
     * plus high-priority tasks due tomorrow. Snoozed/dismissed tasks are skipped.
     */
    fun alerts(tasks: List<TaskEntity>, suppressed: Set<String>, today: String = Ist.todayStr()): List<TaskEntity> {
        val tomorrow = (Ist.parse(today) ?: LocalDate.now(Ist.ZONE)).plusDays(1).toString()
        return tasks.filter { t ->
            val due = t.dueDate
            !t.done && t.id !in suppressed && due != null &&
                (due <= today || (t.priority == Priority.HIGH && due <= tomorrow))
        }.sortedWith(
            compareBy<TaskEntity>(
                { (it.dueDate ?: "") >= today },
                { it.priority != Priority.HIGH },
                { it.dueDate ?: "" },
            )
        )
    }
}
