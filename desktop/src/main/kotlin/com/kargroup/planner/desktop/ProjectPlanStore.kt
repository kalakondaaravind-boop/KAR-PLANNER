package com.kargroup.planner.desktop

import com.kargroup.planner.data.PlannerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/*
 * Special Projects (desktop): a project planner like Gantt Excel, kept separate from the app's
 * everyday activities and projects. Each special project has its own WBS task plan with dates,
 * baselines, actuals, % complete, effort, dependencies and constraints. Saved in special-projects.json.
 */

/** Dependency on another plan task. type: FS (finish→start), SS, FF, SF; lag in working days. */
data class Dependency(val taskId: String, val type: String = "FS", val lag: Int = 0)

object PlanPriority {
    const val LOW = "LOW"
    const val NORMAL = "NORMAL"
    const val HIGH = "HIGH"
    const val CRITICAL = "CRITICAL"
    val ALL = listOf(LOW, NORMAL, HIGH, CRITICAL)
    fun label(p: String) = p.lowercase().replaceFirstChar { it.titlecase() }
}

object ProjectHealth {
    const val ON_TRACK = "on_track"
    const val AT_RISK = "at_risk"
    const val OFF_TRACK = "off_track"
    const val ON_HOLD = "on_hold"
    const val COMPLETED = "completed"
    val ALL = listOf(ON_TRACK, AT_RISK, OFF_TRACK, ON_HOLD, COMPLETED)
    fun label(s: String?) = when (s) {
        AT_RISK -> "At risk"; OFF_TRACK -> "Off track"; ON_HOLD -> "On hold"; COMPLETED -> "Completed"; else -> "On track"
    }
}

data class SpecialProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lead: String = "",
    val health: String = ProjectHealth.ON_TRACK,
    val objective: String = "",
    val start: String? = null,
    val targetEnd: String? = null,
    val color: String = "#0EA5E9",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PlanTask(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val parentId: String? = null,
    val order: Double = 0.0,
    val title: String,
    val priority: String = PlanPriority.NORMAL,
    val resource: String = "",
    val start: String? = null,
    val finish: String? = null,
    val baselineStart: String? = null,
    val baselineEnd: String? = null,
    val actualStart: String? = null,
    val actualEnd: String? = null,
    val percent: Int = 0,
    val workHours: Double? = null,
    val predecessors: List<Dependency> = emptyList(),
    val constraint: String? = null,      // SNET = start no earlier than, FNLT = finish no later than
    val constraintDate: String? = null,
    val milestone: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

object PlanStatus {
    const val COMPLETED = "Completed"
    const val IN_PROGRESS = "In progress"
    const val PLANNED = "Planned"
    const val OVERDUE = "Overdue"
    val ALL = listOf(PLANNED, IN_PROGRESS, OVERDUE, COMPLETED)
}

/** A task as shown in the plan (groups roll up dates and % complete from their subtasks). */
data class PlanRow(
    val task: PlanTask,
    val wbs: String,
    val depth: Int,
    val isGroup: Boolean,
    val start: LocalDate?,
    val finish: LocalDate?,
    val duration: Int?,          // working days
    val percent: Int,
    val status: String,
)

class ProjectPlanStore(private val file: File = File(AppDirs.root, "special-projects.json")) {

    private val _projects = MutableStateFlow<List<SpecialProject>>(emptyList())
    val projects: StateFlow<List<SpecialProject>> = _projects

    private val _tasks = MutableStateFlow<List<PlanTask>>(emptyList())
    val tasks: StateFlow<List<PlanTask>> = _tasks

    init { load() }

    // ------------------------------------------------------------------ storage

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k).ifBlank { null } else null

    private fun load() {
        val o = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }
        val ps = o.optJSONArray("projects") ?: JSONArray()
        _projects.value = (0 until ps.length()).map { i ->
            val j = ps.getJSONObject(i)
            SpecialProject(
                id = j.optString("id"), name = j.optString("name"), lead = j.optString("lead", ""),
                health = j.optString("health", ProjectHealth.ON_TRACK), objective = j.optString("objective", ""),
                start = j.str("start"), targetEnd = j.str("targetEnd"), color = j.optString("color", "#0EA5E9"),
                archived = j.optBoolean("archived", false), createdAt = j.optLong("createdAt", 0L),
            )
        }
        val ts = o.optJSONArray("tasks") ?: JSONArray()
        _tasks.value = (0 until ts.length()).map { i ->
            val j = ts.getJSONObject(i)
            PlanTask(
                id = j.optString("id"), projectId = j.optString("projectId"), parentId = j.str("parentId"),
                order = j.optDouble("order", 0.0), title = j.optString("title"),
                priority = j.optString("priority", PlanPriority.NORMAL), resource = j.optString("resource", ""),
                start = j.str("start"), finish = j.str("finish"),
                baselineStart = j.str("baselineStart"), baselineEnd = j.str("baselineEnd"),
                actualStart = j.str("actualStart"), actualEnd = j.str("actualEnd"),
                percent = j.optInt("percent", 0).coerceIn(0, 100),
                workHours = if (j.has("workHours") && !j.isNull("workHours")) j.optDouble("workHours") else null,
                predecessors = j.optJSONArray("predecessors")?.let { a ->
                    (0 until a.length()).map { k -> a.getJSONObject(k).let { d -> Dependency(d.optString("taskId"), d.optString("type", "FS"), d.optInt("lag", 0)) } }
                } ?: emptyList(),
                constraint = j.str("constraint"), constraintDate = j.str("constraintDate"),
                milestone = j.optBoolean("milestone", false), notes = j.optString("notes", ""),
                createdAt = j.optLong("createdAt", 0L),
            )
        }
    }

    private fun save() {
        val ps = JSONArray(_projects.value.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("lead", p.lead).put("health", p.health).put("objective", p.objective)
                .put("start", p.start ?: JSONObject.NULL).put("targetEnd", p.targetEnd ?: JSONObject.NULL).put("color", p.color)
                .put("archived", p.archived).put("createdAt", p.createdAt)
        })
        val ts = JSONArray(_tasks.value.map { t ->
            JSONObject().put("id", t.id).put("projectId", t.projectId).put("parentId", t.parentId ?: JSONObject.NULL)
                .put("order", t.order).put("title", t.title).put("priority", t.priority).put("resource", t.resource)
                .put("start", t.start ?: JSONObject.NULL).put("finish", t.finish ?: JSONObject.NULL)
                .put("baselineStart", t.baselineStart ?: JSONObject.NULL).put("baselineEnd", t.baselineEnd ?: JSONObject.NULL)
                .put("actualStart", t.actualStart ?: JSONObject.NULL).put("actualEnd", t.actualEnd ?: JSONObject.NULL)
                .put("percent", t.percent).put("workHours", t.workHours ?: JSONObject.NULL)
                .put("predecessors", JSONArray(t.predecessors.map { JSONObject().put("taskId", it.taskId).put("type", it.type).put("lag", it.lag) }))
                .put("constraint", t.constraint ?: JSONObject.NULL).put("constraintDate", t.constraintDate ?: JSONObject.NULL)
                .put("milestone", t.milestone).put("notes", t.notes).put("createdAt", t.createdAt)
        })
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("version", 1).put("projects", ps).put("tasks", ts).toString(1), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            // dated copy for safety (newest 30 kept with the other backups)
            runCatching { tmp.delete(); file.copyTo(File(AppDirs.backupsDir, "Special-Projects-${LocalDate.now()}.json"), overwrite = true) }
        }
    }

    // --------------------------------------------------------------- projects

    fun saveProject(p: SpecialProject) {
        _projects.value = _projects.value.filter { it.id != p.id } + p
        save()
    }

    fun deleteProject(id: String) {
        _projects.value = _projects.value.filter { it.id != id }
        _tasks.value = _tasks.value.filter { it.projectId != id }
        save()
    }

    // ------------------------------------------------------------------ tasks

    fun tasksOf(projectId: String): List<PlanTask> = _tasks.value.filter { it.projectId == projectId }

    fun saveTask(t: PlanTask) {
        _tasks.value = _tasks.value.filter { it.id != t.id } + t
        save()
    }

    fun saveTasks(list: Collection<PlanTask>) {
        if (list.isEmpty()) return
        val ids = list.map { it.id }.toSet()
        _tasks.value = _tasks.value.filter { it.id !in ids } + list
        save()
    }

    /** Deletes a task with its subtasks and removes links to them. */
    fun deleteTask(id: String) {
        val all = _tasks.value
        val gone = HashSet<String>().apply { add(id) }
        var grew = true
        while (grew) { grew = false; all.forEach { if (it.parentId in gone && gone.add(it.id)) grew = true } }
        _tasks.value = all.filter { it.id !in gone }.map { t ->
            if (t.predecessors.any { it.taskId in gone }) t.copy(predecessors = t.predecessors.filter { it.taskId !in gone }) else t
        }
        save()
    }

    fun nextOrder(projectId: String, parentId: String?): Double =
        (_tasks.value.filter { it.projectId == projectId && it.parentId == parentId }.maxOfOrNull { it.order } ?: 0.0) + 1.0
}

// =============================================================================
// Calculations
// =============================================================================

object PlanCalc {

    fun parse(d: String?): LocalDate? = d?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun isWorking(d: LocalDate, s: PlannerSettings): Boolean = s.isWorkingDay(d)

    /** Working days from [a] to [b] inclusive (at least 1 for a valid range). */
    fun workdays(a: LocalDate, b: LocalDate, s: PlannerSettings): Int {
        if (b.isBefore(a)) return 0
        var n = 0
        var d = a
        var guard = 0
        while (!d.isAfter(b) && guard < 4000) { if (isWorking(d, s)) n++; d = d.plusDays(1); guard++ }
        return n.coerceAtLeast(1)
    }

    /** The date [n] working days after [d] (n may be negative). */
    fun addWorkdays(d: LocalDate, n: Int, s: PlannerSettings): LocalDate {
        var x = d
        var left = kotlin.math.abs(n)
        val step = if (n >= 0) 1L else -1L
        var guard = 0
        while (left > 0 && guard < 4000) { x = x.plusDays(step); if (isWorking(x, s)) left--; guard++ }
        return x
    }

    fun nextWorking(d: LocalDate, s: PlannerSettings): LocalDate {
        var x = d; var g = 0
        while (!isWorking(x, s) && g < 30) { x = x.plusDays(1); g++ }
        return x
    }

    /** Finish of a [days]-working-day task starting on [start]. */
    fun finishFor(start: LocalDate, days: Int, s: PlannerSettings): LocalDate = addWorkdays(nextWorking(start, s), (days - 1).coerceAtLeast(0), s)

    fun statusOf(pct: Int, start: LocalDate?, finish: LocalDate?, today: LocalDate): String = when {
        pct >= 100 -> PlanStatus.COMPLETED
        finish != null && finish.isBefore(today) -> PlanStatus.OVERDUE
        pct > 0 || (start != null && !start.isAfter(today)) -> PlanStatus.IN_PROGRESS
        else -> PlanStatus.PLANNED
    }

    /** Plan rows of one project in WBS order; groups roll up dates and % complete (weighted by duration). */
    fun rows(tasks: List<PlanTask>, s: PlannerSettings, today: LocalDate): List<PlanRow> {
        val ids = tasks.map { it.id }.toSet()
        val byParent = tasks.groupBy { it.parentId?.takeIf { p -> p in ids } }
        val out = ArrayList<PlanRow>()
        fun ordered(list: List<PlanTask>) = list.sortedWith(compareBy({ it.order }, { it.createdAt }))

        fun visit(t: PlanTask, wbs: String, depth: Int): PlanRow {
            val idx = out.size
            out.add(PlanRow(t, wbs, depth, false, null, null, null, 0, PlanStatus.PLANNED))
            val kids = ordered(byParent[t.id].orEmpty()).mapIndexed { i, k -> visit(k, "$wbs.${i + 1}", depth + 1) }
            val row = if (kids.isEmpty()) {
                val finish = parse(t.finish)
                val start = (parse(t.start) ?: finish)?.let { if (finish != null && it.isAfter(finish)) finish else it }
                val dur = if (t.milestone) 0 else if (start != null && finish != null) workdays(start, finish, s) else null
                PlanRow(t, wbs, depth, false, start, finish, dur, t.percent, statusOf(t.percent, start, finish, today))
            } else {
                val start = kids.mapNotNull { it.start }.minOrNull()
                val finish = kids.mapNotNull { it.finish }.maxOrNull()
                val w = kids.map { (it.duration ?: 1).coerceAtLeast(1) }
                val pct = kids.mapIndexed { i, r -> r.percent * w[i] }.sum() / w.sum().coerceAtLeast(1)
                val dur = if (start != null && finish != null) workdays(start, finish, s) else null
                PlanRow(t, wbs, depth, true, start, finish, dur, pct, statusOf(pct, start, finish, today))
            }
            out[idx] = row
            return row
        }
        ordered(byParent[null].orEmpty()).forEachIndexed { i, t -> visit(t, "${i + 1}", 0) }
        return out
    }

    /**
     * Moves tasks so every dependency and SNET / FNLT constraint is respected, keeping durations.
     * Returns the tasks that changed.
     */
    fun autoSchedule(rows: List<PlanRow>, s: PlannerSettings): List<PlanTask> {
        val leaf = rows.filter { !it.isGroup }
        val start = HashMap<String, LocalDate>()
        val finish = HashMap<String, LocalDate>()
        leaf.forEach { r -> if (r.start != null && r.finish != null) { start[r.task.id] = r.start; finish[r.task.id] = r.finish } }
        val changed = HashMap<String, PlanTask>()
        // group dependencies use the group's rolled-up dates
        rows.filter { it.isGroup }.forEach { r -> if (r.start != null && r.finish != null) { start.putIfAbsent(r.task.id, r.start); finish.putIfAbsent(r.task.id, r.finish) } }
        repeat(leaf.size + 2) {
            var moved = false
            leaf.forEach { r ->
                val id = r.task.id
                if (r.percent >= 100) return@forEach
                val s0 = start[id] ?: return@forEach
                val f0 = finish[id] ?: return@forEach
                val days = if (r.task.milestone) 1 else workdays(s0, f0, s)
                var earliest: LocalDate? = null
                for (dep in r.task.predecessors) {
                    val ps = start[dep.taskId] ?: continue
                    val pf = finish[dep.taskId] ?: continue
                    val c = when (dep.type) {
                        "SS" -> addWorkdays(ps, dep.lag, s)
                        "FF" -> addWorkdays(addWorkdays(pf, dep.lag, s), -(days - 1), s)
                        "SF" -> addWorkdays(addWorkdays(ps, dep.lag, s), -(days - 1), s)
                        else -> addWorkdays(pf, 1 + dep.lag, s)
                    }
                    val e = earliest
                    if (e == null || c.isAfter(e)) earliest = c
                }
                if (r.task.constraint == "SNET") parse(r.task.constraintDate)?.let { c -> val e = earliest; if (e == null || c.isAfter(e)) earliest = c }
                var ns = s0
                val e = earliest
                if (e != null && e.isAfter(s0)) ns = nextWorking(e, s)
                var nf = if (r.task.milestone) ns else finishFor(ns, days, s)
                if (r.task.constraint == "FNLT") parse(r.task.constraintDate)?.let { c ->
                    if (nf.isAfter(c)) { nf = c; ns = if (r.task.milestone) c else addWorkdays(c, -(days - 1), s) }
                }
                if (ns != s0 || nf != f0) {
                    start[id] = ns; finish[id] = nf
                    changed[id] = (changed[id] ?: r.task).copy(start = ns.toString(), finish = nf.toString())
                    moved = true
                }
            }
            if (!moved) return changed.values.toList()
        }
        return changed.values.toList()
    }

    /** Copies current dates into the baseline (the "Set baseline" button). */
    fun baseline(tasks: List<PlanTask>): List<PlanTask> = tasks.map { it.copy(baselineStart = it.start, baselineEnd = it.finish) }

    /** Variance of finish vs baseline finish in working days (+ = late). */
    fun slip(t: PlanTask, s: PlannerSettings): Int? {
        val f = parse(t.finish) ?: return null
        val b = parse(t.baselineEnd) ?: return null
        return when {
            f == b -> 0
            f.isAfter(b) -> workdays(b.plusDays(1), f, s)
            else -> -workdays(f.plusDays(1), b, s)
        }
    }
}
