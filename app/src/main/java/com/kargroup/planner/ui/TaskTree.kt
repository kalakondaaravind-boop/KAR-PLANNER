package com.kargroup.planner.ui

import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.QUICK_TASK_MINUTES
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.MeetingMinuteEntity
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
 *  2. Then earliest target date, then earliest target time (no date = last)
 *  3. Then priority: Very High → High → Medium → Standard
 */
val scheduleOrder: Comparator<TaskEntity> = compareBy<TaskEntity>(
    { if (isQuickWin(it)) 0 else 1 },
    { it.dueDate ?: "9999-99-99" },
    { it.dueTime ?: "99:99" },
    { Priority.rank(it.priority) },
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
    /** IST date ('YYYY-MM-DD') the activity is planned on. */
    val day: String,
    /** True when the user fixed the start time (drag-and-drop or "Set time"). */
    val pinned: Boolean = false,
    /** True when the activity is longer than any free slot and had to run over a break/meeting. */
    val overflow: Boolean = false,
)

/** Time that can't be used for activities: meetings and breaks. */
data class ScheduleBlock(
    val kind: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val day: String,
    val meetingId: String? = null,
)

data class ScheduleResult(val items: List<ScheduledItem>, val blocks: List<ScheduleBlock>)

object BlockKind {
    const val MEETING = "meeting"
    const val BREAK = "break"
}

/**
 * Builds the plan: every open, actionable activity (open tasks without open subtasks), ordered
 * automatically or manually, booked inside the working hours of working days (IST), around
 * meetings, the break and any activities whose time the user fixed. Today starts from "now".
 */
fun buildSchedule(
    index: TaskIndex,
    manual: Boolean,
    settings: PlannerSettings,
    meetings: List<MeetingMinuteEntity>,
    nowMillis: Long = System.currentTimeMillis(),
): ScheduleResult {
    val zone = Ist.ZONE
    fun at(d: LocalDate, minutes: Int): Long = d.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()
    fun dayOf(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val today = dayOf(nowMillis)

    // Meetings block their slot.
    val blocks = ArrayList<ScheduleBlock>()
    meetings.forEach { m ->
        val d = Ist.parse(m.meetingDate) ?: return@forEach
        val t = runCatching { java.time.LocalTime.parse(m.meetingTime) }.getOrNull() ?: return@forEach
        val start = at(d, t.hour * 60 + t.minute)
        val end = start + m.durationMinutes.coerceAtLeast(5) * 60_000L
        blocks.add(ScheduleBlock(BlockKind.MEETING, m.title, start, end, d.toString(), m.id))
    }
    val busy = HashMap<LocalDate, MutableList<Pair<Long, Long>>>()
    fun busyOn(d: LocalDate): MutableList<Pair<Long, Long>> = busy.getOrPut(d) {
        val list = ArrayList<Pair<Long, Long>>()
        blocks.filter { it.day == d.toString() }.forEach { list.add(it.startMillis to it.endMillis) }
        if (settings.breakEnabled && settings.breakEnd > settings.breakStart) {
            list.add(at(d, settings.breakStart) to at(d, settings.breakEnd))
        }
        list
    }

    val actionable = index.all.filter { t -> !t.done && index.children(t.id).none { !it.done } }
    fun minsOf(t: TaskEntity) = t.estimatedMinutes?.takeIf { it > 0 } ?: DEFAULT_PLAN_MINUTES
    fun pathOf(t: TaskEntity): String? = index.ancestors(t.id).takeIf { it.isNotEmpty() }?.joinToString(" › ") { it.text }

    val result = ArrayList<ScheduledItem>()

    // 1. Activities the user fixed in time (today or later) keep their slot.
    val pinned = actionable.filter { t -> t.plannedStart?.let { !dayOf(it).isBefore(today) } == true }
    pinned.forEach { t ->
        val start = t.plannedStart!!
        val end = start + minsOf(t) * 60_000L
        busyOn(dayOf(start)).add(start to end)
        result.add(ScheduledItem(t, pathOf(t), start, end, t.estimatedMinutes != null, dayOf(start).toString(), pinned = true))
    }

    // 2. Everything else flows into the free working time, in priority order.
    val pinnedIds = pinned.map { it.id }.toHashSet()
    val ordered = actionable.filter { it.id !in pinnedIds }.sortedWith(if (manual) manualScheduleOrder else scheduleOrder)

    fun freeSegments(d: LocalDate, from: Long): List<Pair<Long, Long>> {
        if (!settings.isWorkingDay(d)) return emptyList()
        val winStart = maxOf(at(d, settings.workStart), from)
        val winEnd = at(d, settings.workEnd)
        if (winStart >= winEnd) return emptyList()
        val taken = busyOn(d).filter { it.second > winStart && it.first < winEnd }.sortedBy { it.first }
        val out = ArrayList<Pair<Long, Long>>()
        var c = winStart
        taken.forEach { (s, e) ->
            if (s > c) out.add(c to minOf(s, winEnd))
            if (e > c) c = e
        }
        if (c < winEnd) out.add(c to winEnd)
        return out
    }

    val step = 5 * 60_000L
    var day = today
    var cursor = ((nowMillis + step - 1) / step) * step
    ordered.forEach { t ->
        val dur = minsOf(t) * 60_000L
        var placed: Pair<Long, Long>? = null
        var overflow = false
        var d = day
        var from = cursor
        var guard = 0
        while (placed == null && guard < 400) {
            val seg = freeSegments(d, from).firstOrNull { it.second - it.first >= dur }
            if (seg != null) placed = seg.first to seg.first + dur
            else {
                d = d.plusDays(1)
                from = at(d, 0)
                guard++
            }
        }
        if (placed == null) {
            // Longer than any free slot: start at the next free moment and run over.
            d = day
            from = cursor
            guard = 0
            while (placed == null && guard < 400) {
                val seg = freeSegments(d, from).firstOrNull()
                if (seg != null) placed = seg.first to seg.first + dur
                else {
                    d = d.plusDays(1)
                    from = at(d, 0)
                    guard++
                }
            }
            overflow = true
        }
        val (start, end) = placed ?: (cursor to cursor + dur)
        busyOn(dayOf(start)).add(start to end)
        day = dayOf(start)
        cursor = end
        result.add(ScheduledItem(t, pathOf(t), start, end, t.estimatedMinutes != null, day.toString(), overflow = overflow))
    }
    return ScheduleResult(result.sortedBy { it.startMillis }, blocks)
}

private val time12 = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH)

/** "14:30" -> "02:30 PM". */
fun formatTime12(hhmm: String): String =
    runCatching { java.time.LocalTime.parse(hhmm).format(time12) }.getOrDefault(hhmm)

private val time24 = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH)

fun formatMillisTime24(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(Ist.ZONE).format(time24)

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
        Screen.Projects, Screen.Meetings, Screen.Schedule, Screen.Journal, Screen.People,
        Screen.Reports, Screen.Search, Screen.Settings, Screen.Emails,
        Screen.Dashboard, Screen.Board, Screen.Table, Screen.Templates, Screen.Tracker, Screen.Commercials -> emptyList()
    }
}
