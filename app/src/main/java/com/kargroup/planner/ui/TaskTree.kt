package com.kargroup.planner.ui

import com.kargroup.planner.data.Priority
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

/** Ordering for top-level lists: open first, high → medium → none, earliest due date, oldest first. */
val rootOrder: Comparator<TaskEntity> = compareBy<TaskEntity>(
    { it.done },
    {
        when (it.priority) {
            Priority.HIGH -> 0
            Priority.MEDIUM -> 1
            else -> 2
        }
    },
    { it.dueDate ?: "9999-99-99" },
    { it.createdAt },
)

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
        Screen.Inbox -> single(index.topLevelMatching { it.projectId == null && it.contextId == null && openOrAll(it) })
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
        is Screen.Context -> single(index.rootsMatching { it.contextId == screen.id && openOrAll(it) })
        Screen.Projects, Screen.Contexts, Screen.Meetings -> emptyList()
    }
}
