package com.kargroup.planner.ui

import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.QUICK_TASK_MINUTES
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.util.Ist
import java.time.LocalDate

data class TreeRow(
    val task: TaskEntity,
    val depth: Int,
    val childCount: Int,
    val doneChildCount: Int,
)

data class TaskSection(
    val key: String,
    val title: String?,
    val tone: SectionTone,
    val rows: List<TreeRow>,
)

enum class SectionTone { Normal, Danger, Accent }

/** Case-insensitive name match; a null name means "Unassigned". */
fun sameAssignee(assignee: String?, name: String?): Boolean {
    val a = assignee?.trim()?.ifBlank { null }
    return if (name == null) a == null else a != null && a.equals(name.trim(), ignoreCase = true)
}

/** Ordering for top-level lists: open first, very high → high → medium → none, earliest target date/time, oldest first. */
val rootOrder: Comparator<TaskEntity> = compareBy<TaskEntity>(
    { it.done },
    { Priority.rank(it.priority) },
    { it.dueDate ?: "9999-99-99" },
    { it.dueTime ?: "99:99" },
    { it.createdAt },
)

// =============================================================================
// Automated scheduler
// =============================================================================

/** Activities with an estimate under [QUICK_TASK_MINUTES] minutes. */
fun isQuickWin(t: TaskEntity): Boolean {
    val m = t.estimatedMinutes ?: return false
    return m in 1 until QUICK_TASK_MINUTES
}

/**
 * Automatic schedule order:
 *  1. Quick wins (estimated time under 10 minutes) first
 *  2. Then priority: Very High → High → Medium → Standard
 *  3. Then earliest target date, then earliest target time (no date = last)
 */
val scheduleOrder: Comparator<TaskEntity> = compareBy<TaskEntity>(
    { if (isQuickWin(it)) 0 else 1 },
    { Priority.rank(it.priority) },
    { it.dueDate ?: "9999-99-99" },
    { it.dueTime ?: "99:99" },
    { it.createdAt },
)

/** Manual order: saved positions first (in order), anything new falls in after them by the automatic rules. */
val manualScheduleOrder: Comparator<TaskEntity> = compareBy<TaskEntity>(
    { if (it.manualOrder == null) 1 else 0 },
    { it.manualOrder ?: 0L },
).then(scheduleOrder)

/** Minutes assumed for planning when an activity has no estimate. */
const val DEFAULT_PLAN_MINUTES = 30

data class ScheduledItem(
    val task: TaskEntity,
    val path: String?,
    val startMillis: Long,
    val endMillis: Long,
    val estimated: Boolean,
)

/**
 * Builds the run-sheet: every open, actionable activity (open tasks without open subtasks),
 * ordered automatically or manually, with planned IST start/end times laid back-to-back from now.
 */
fun buildSchedule(index: TaskIndex, manual: Boolean, nowMillis: Long = System.currentTimeMillis()): List<ScheduledItem> {
    val actionable = index.all.filter { t -> !t.done && index.children(t.id).none { !it.done } }
    val ordered = actionable.sortedWith(if (manual) manualScheduleOrder else scheduleOrder)
    // Start at the next 5-minute mark.
    val step = 5 * 60_000L
    var cursor = ((nowMillis + step - 1) / step) * step
    return ordered.map { t ->
        val mins = t.estimatedMinutes?.takeIf { it > 0 } ?: DEFAULT_PLAN_MINUTES
        val start = cursor
        val end = start + mins * 60_000L
        cursor = end
        val ancestors = index.ancestors(t.id)
        ScheduledItem(
            task = t,
            path = if (ancestors.isEmpty()) null else ancestors.joinToString(" › ") { it.text },
            startMillis = start,
            endMillis = end,
            estimated = t.estimatedMinutes != null,
        )
    }
}

private val time12 = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH)

/** "14:30" -> "02:30 PM". */
fun formatTime12(hhmm: String): String =
    runCatching { java.time.LocalTime.parse(hhmm).format(time12) }.getOrDefault(hhmm)

fun formatMillisTime(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(Ist.ZONE).format(time12)

/** 5 -> "5 min", 60 -> "1 h", 90 -> "1 h 30". */
fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60}"
}

/** Subtasks keep the order they were created in. */
val childOrder: Comparator<TaskEntity> = compareBy<TaskEntity>({ it.done }, { it.createdAt })

class TaskIndex(val all: List<TaskEntity>) {
    val byId: Map<String, TaskEntity> = all.associateBy { it.id }
    val byParent: Map<String?, List<TaskEntity>> = all.groupBy { it.parentId }

    fun children(id: String): List<TaskEntity> = byParent[id].orEmpty()

    fun isTopLevel(t: TaskEntity): Boolean = t.parentId == null || byId[t.parentId] == null

    /** Ancestor chain from the top-most ancestor down to the direct parent. */
    fun ancestors(id: String): List<TaskEntity> {
        val chain = ArrayList<TaskEntity>()
        val seen = HashSet<String>()
        var cur = byId[id]?.parentId
        while (cur != null && seen.add(cur)) {
            val p = byId[cur] ?: break
            chain.add(p)
            cur = p.parentId
        }
        return chain.asReversed()
    }

    fun openDescendantCount(id: String): Int {
        var count = 0
        val seen = HashSet<String>()
        val stack = ArrayDeque<String>()
        stack.addLast(id)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            children(cur).forEach { c ->
                if (seen.add(c.id)) {
                    if (!c.done) count++
                    stack.addLast(c.id)
                }
            }
        }
        return count
    }

    /** Tasks matching [predicate] that have no ancestor which also matches (so subtrees are not repeated). */
    fun rootsMatching(predicate: (TaskEntity) -> Boolean): List<TaskEntity> {
        val matched = all.filter(predicate)
        val matchedIds = matched.map { it.id }.toHashSet()
        return matched.filter { t -> ancestors(t.id).none { it.id in matchedIds } }.sortedWith(rootOrder)
    }

    fun topLevelMatching(predicate: (TaskEntity) -> Boolean): List<TaskEntity> =
        all.filter { isTopLevel(it) && predicate(it) }.sortedWith(rootOrder)

    fun buildRows(roots: List<TaskEntity>, showCompleted: Boolean, flat: Boolean = false): List<TreeRow> {
        val out = ArrayList<TreeRow>()
        val visited = HashSet<String>()
        fun visit(t: TaskEntity, depth: Int) {
            if (!visited.add(t.id)) return
            val kids = children(t.id)
            out.add(TreeRow(t, depth, kids.size, kids.count { it.done }))
            if (flat || t.isCollapsed) return
            kids.filter { showCompleted || !it.done }.sortedWith(childOrder).forEach { visit(it, depth + 1) }
        }
        roots.forEach { visit(it, 0) }
        return out
    }
}

fun buildSections(
    screen: Screen,
    index: TaskIndex,
    today: String,
    showCompleted: Boolean,
    drillRootId: String?,
): List<TaskSection> {
    val drillRoot = drillRootId?.let { index.byId[it] }
    if (drillRoot != null) {
        return listOf(TaskSection("drill", null, SectionTone.Normal, index.buildRows(listOf(drillRoot), showCompleted)))
    }
    val openOrAll: (TaskEntity) -> Boolean = { showCompleted || !it.done }
    fun single(roots: List<TaskEntity>) =
        listOf(TaskSection("main", null, SectionTone.Normal, index.buildRows(roots, showCompleted)))

    return when (screen) {
        Screen.Today -> {
            val overdue = index.rootsMatching { !it.done && it.dueDate != null && it.dueDate < today }
            val dueToday = index.rootsMatching { !it.done && it.dueDate == today }
            listOf(
                TaskSection("overdue", "Overdue", SectionTone.Danger, index.buildRows(overdue, showCompleted)),
                TaskSection("today", "Due Today", SectionTone.Accent, index.buildRows(dueToday, showCompleted)),
            ).filter { it.rows.isNotEmpty() }
        }
        Screen.Overdue -> single(index.rootsMatching { !it.done && it.dueDate != null && it.dueDate < today })
        Screen.Upcoming -> {
            val start = Ist.parse(today) ?: LocalDate.now(Ist.ZONE)
            (1..7).map { offset ->
                val date = start.plusDays(offset.toLong())
                val ds = date.toString()
                val label = (if (offset == 1) "Tomorrow · " else "") + date.format(Ist.longDayFormat)
                TaskSection(ds, label, SectionTone.Normal, index.buildRows(index.rootsMatching { !it.done && it.dueDate == ds }, showCompleted))
            }.filter { it.rows.isNotEmpty() }
        }
        Screen.Recurring -> single(index.rootsMatching { !it.done && it.isRecurring })
        Screen.Inbox -> single(index.topLevelMatching { it.projectId == null && openOrAll(it) })
        Screen.AllTasks -> single(index.topLevelMatching(openOrAll))
        Screen.Completed -> listOf(
            TaskSection(
                "completed", null, SectionTone.Normal,
                index.buildRows(
                    index.all.filter { it.done }.sortedByDescending { it.completedAt ?: it.createdAt },
                    showCompleted = true,
                    flat = true,
                ),
            )
        )
        is Screen.Project -> single(index.topLevelMatching { it.projectId == screen.id && openOrAll(it) })
        is Screen.Person -> {
            val mine: (TaskEntity) -> Boolean = { sameAssignee(it.assignee, screen.name) }
            val isOverdue: (TaskEntity) -> Boolean = { !it.done && it.dueDate != null && it.dueDate < today }
            listOf(
                TaskSection("p-overdue", "Overdue", SectionTone.Danger,
                    index.buildRows(index.rootsMatching { mine(it) && isOverdue(it) }, showCompleted)),
                TaskSection("p-initiated", "Initiated", SectionTone.Accent,
                    index.buildRows(index.rootsMatching { mine(it) && !it.done && !isOverdue(it) && it.status == TaskStatus.INITIATED }, showCompleted)),
                TaskSection("p-notstarted", "Not Started", SectionTone.Normal,
                    index.buildRows(index.rootsMatching { mine(it) && !it.done && !isOverdue(it) && it.status != TaskStatus.INITIATED }, showCompleted)),
                TaskSection("p-done", "Completed", SectionTone.Normal,
                    if (showCompleted) index.buildRows(index.rootsMatching { mine(it) && it.done }.sortedByDescending { it.completedAt ?: 0L }, true, flat = true) else emptyList()),
            ).filter { it.rows.isNotEmpty() }
        }
        Screen.Projects, Screen.Meetings, Screen.Schedule, Screen.Journal, Screen.People -> emptyList()
    }
}
