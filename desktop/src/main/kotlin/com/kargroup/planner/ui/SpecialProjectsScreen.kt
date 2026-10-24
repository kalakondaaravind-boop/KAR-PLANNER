package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlagCircle
import androidx.compose.material.icons.outlined.FormatIndentDecrease
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.desktop.FileDialogs
import com.kargroup.planner.desktop.PlanCalc
import com.kargroup.planner.desktop.PlanPriority
import com.kargroup.planner.desktop.PlanRow
import com.kargroup.planner.desktop.PlanStatus
import com.kargroup.planner.desktop.PlanTask
import com.kargroup.planner.desktop.ProjectHealth
import com.kargroup.planner.desktop.ProjectPlanStore
import com.kargroup.planner.desktop.SpecialProject
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class PlanTab(val label: String) { OVERVIEW("Overview"), GANTT("Gantt chart"), SHEET("Task sheet"), WORKLOAD("Workload") }
private enum class Zoom(val label: String, val days: Int) { FIT("Fit project", 0), WEEKS("4 weeks", 28), QUARTER("3 months", 91), HALF("6 months", 182), YEAR("12 months", 364) }

/** Initial selection (remembered across visits; also used by the smoke test). */
object PlanScreenState { var projectId: String? = null; var tab: String = "GANTT" }

/** Special Projects: a project planner (like Gantt Excel) separate from everyday activities. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecialProjectsScreen(
    store: ProjectPlanStore,
    persons: List<PersonEntity>,
    settings: PlannerSettings,
    onMessage: (String) -> Unit,
) {
    val projects by store.projects.collectAsState()
    val allTasks by store.tasks.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf(PlanScreenState.projectId) }
    var tab by rememberSaveable { mutableStateOf(PlanTab.entries.firstOrNull { it.name == PlanScreenState.tab } ?: PlanTab.GANTT) }
    var projectDialog by remember { mutableStateOf<Pair<SpecialProject, Boolean>?>(null) }
    val today = Ist.today()
    val project = projects.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        // ------------------------------------------------------------ project bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Special Projects", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
                Text("Plan, track and report special projects — separate from your everyday activities.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
            }
            Surface(onClick = { projectDialog = SpecialProject(name = "", start = today.toString()) to true },
                color = KarColors.SkyDeep, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New special project", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProjectChip("Portfolio (all projects)", null, selectedId == null) { selectedId = null; PlanScreenState.projectId = null }
            projects.filter { !it.archived }.sortedBy { it.name }.forEach { p ->
                ProjectChip(p.name, p.color, selectedId == p.id) { selectedId = p.id; PlanScreenState.projectId = p.id }
            }
            val archived = projects.count { it.archived }
            if (archived > 0) Text("$archived archived", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))

        if (project == null) {
            PortfolioView(projects, allTasks, settings, today, onOpen = { selectedId = it.id; tab = PlanTab.OVERVIEW; PlanScreenState.projectId = it.id },
                onEdit = { projectDialog = it to false })
        } else {
            val tasks = allTasks.filter { it.projectId == project.id }
            ProjectView(
                project = project, tasks = tasks, store = store, persons = persons, settings = settings, today = today,
                tab = tab, onTab = { tab = it; PlanScreenState.tab = it.name }, onEditProject = { projectDialog = project to false }, onMessage = onMessage,
            )
        }
    }

    projectDialog?.let { (p, isNew) ->
        SpecialProjectDialog(
            project = p, persons = persons, isNew = isNew,
            onDismiss = { projectDialog = null },
            onSave = {
                store.saveProject(it)
                projectDialog = null
                if (isNew) { selectedId = it.id; tab = PlanTab.GANTT }
                onMessage(if (isNew) "Special project created · add its tasks" else "Project saved")
            },
            onDelete = if (isNew) null else ({
                store.deleteProject(p.id); projectDialog = null; selectedId = null; onMessage("Project deleted")
            }),
        )
    }
}

@Composable
private fun ProjectChip(label: String, color: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) KarColors.SkyDeep else KarColors.Card,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (selected) KarColors.SkyDeep else KarColors.Border),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (color != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(parseHexColor(color))); Spacer(Modifier.width(6.dp)) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else KarColors.Ink, maxLines = 1)
        }
    }
}

// =============================================================================
// Portfolio
// =============================================================================

@Composable
private fun PortfolioView(
    projects: List<SpecialProject>,
    allTasks: List<PlanTask>,
    settings: PlannerSettings,
    today: LocalDate,
    onOpen: (SpecialProject) -> Unit,
    onEdit: (SpecialProject) -> Unit,
) {
    val active = projects.filter { !it.archived }.sortedBy { it.name }
    val summaries = active.map { p -> p to summarize(PlanCalc.rows(allTasks.filter { it.projectId == p.id }, settings, today), p, today, settings) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("Projects", "${active.size}", "${active.count { it.health == ProjectHealth.ON_TRACK }} on track", KarColors.SkyDeep, Modifier.weight(1f))
            Kpi("At risk / off track", "${active.count { it.health == ProjectHealth.AT_RISK || it.health == ProjectHealth.OFF_TRACK }}", "need attention", KarColors.Amber, Modifier.weight(1f))
            Kpi("Tasks", "${summaries.sumOf { it.second.total }}", "${summaries.sumOf { it.second.completed }} completed", KarColors.Navy2, Modifier.weight(1f))
            Kpi("Overdue tasks", "${summaries.sumOf { it.second.overdue }}", "across all projects", if (summaries.sumOf { it.second.overdue } > 0) KarColors.Red else KarColors.Green, Modifier.weight(1f))
            Kpi("Milestones in 30 days", "${summaries.sumOf { it.second.milestones30 }}", "upcoming key dates", KarColors.Green, Modifier.weight(1f))
        }
        DepthCard(Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().background(KarColors.SkyWash).padding(horizontal = 12.dp, vertical = 9.dp)) {
                    H("Project", 2.4f); H("Lead", 1.2f); H("Status", 1f); H("Progress", 1.6f); H("Start", 0.9f); H("End", 0.9f)
                    H("Tasks", 0.8f); H("Overdue", 0.7f); H("Next milestone", 1.6f); H("vs baseline", 0.9f); H("", 0.4f)
                }
                if (summaries.isEmpty()) Text("No special projects yet — click \"New special project\".", color = KarColors.SlateLight, modifier = Modifier.padding(20.dp))
                summaries.forEachIndexed { i, (p, s) ->
                    HorizontalDivider(color = KarColors.Border)
                    Row(
                        Modifier.fillMaxWidth().background(if (i % 2 == 1) KarColors.Tint else Color.Transparent).clickable { onOpen(p) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(p.color)))
                            Spacer(Modifier.width(8.dp))
                            Text(p.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        C(p.lead.ifBlank { "–" }, 1.2f)
                        Box(Modifier.weight(1f)) { HealthChip(p.health) }
                        Row(Modifier.weight(1.6f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(s.percent / 100f, Modifier.weight(1f))
                            Text(" ${s.percent}%", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                        }
                        C(planDate(s.start), 0.9f); C(planDate(s.finish), 0.9f, if (s.finish != null && p.targetEnd != null && s.finish.isAfter(LocalDate.parse(p.targetEnd))) KarColors.Red else KarColors.Ink)
                        C("${s.completed}/${s.total}", 0.8f)
                        C("${s.overdue}", 0.7f, if (s.overdue > 0) KarColors.Red else KarColors.Slate)
                        C(s.nextMilestone ?: "–", 1.6f)
                        C(s.slip?.let { if (it > 0) "+$it d late" else if (it < 0) "${-it} d early" else "on plan" } ?: "–", 0.9f,
                            if ((s.slip ?: 0) > 0) KarColors.Red else KarColors.Slate)
                        IconButton(onClick = { onEdit(p) }, modifier = Modifier.weight(0.4f).size(28.dp)) {
                            Icon(Icons.Outlined.Edit, "Edit", tint = KarColors.SlateLight, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        // portfolio timeline
        if (summaries.any { it.second.start != null && it.second.finish != null }) {
            DepthCard(Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Portfolio timeline", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Spacer(Modifier.height(8.dp))
                    val starts = summaries.mapNotNull { it.second.start }
                    val ends = summaries.mapNotNull { it.second.finish }
                    val from = starts.minOrNull()!!.minusDays(3)
                    val to = ends.maxOrNull()!!.plusDays(3)
                    val span = ChronoUnit.DAYS.between(from, to).coerceAtLeast(1).toFloat()
                    summaries.filter { it.second.start != null && it.second.finish != null }.forEach { (p, s) ->
                        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.labelSmall, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(180.dp))
                            val c = parseHexColor(p.color)
                            val todayC = KarColors.Red
                            Canvas(Modifier.weight(1f).height(16.dp)) {
                                val x1 = ChronoUnit.DAYS.between(from, s.start) / span * size.width
                                val x2 = (ChronoUnit.DAYS.between(from, s.finish) + 1) / span * size.width
                                drawRect(c.copy(alpha = 0.25f), Offset(x1, 2f), Size(x2 - x1, size.height - 4f))
                                drawRect(c, Offset(x1, 2f), Size((x2 - x1) * s.percent / 100f, size.height - 4f))
                                val tx = ChronoUnit.DAYS.between(from, today) / span * size.width
                                if (tx in 0f..size.width) drawLine(todayC, Offset(tx, 0f), Offset(tx, size.height), 1.5f)
                            }
                        }
                    }
                    Text("${planDate(from)} — ${planDate(to)} · red line = today", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
                }
            }
        }
    }
}

private data class Summary(
    val total: Int, val completed: Int, val inProgress: Int, val planned: Int, val overdue: Int,
    val percent: Int, val start: LocalDate?, val finish: LocalDate?, val nextMilestone: String?, val milestones30: Int, val slip: Int?,
)

private fun summarize(rows: List<PlanRow>, p: SpecialProject, today: LocalDate, s: PlannerSettings): Summary {
    val leaf = rows.filter { !it.isGroup }
    val top = rows.filter { it.depth == 0 }
    val w = top.map { (it.duration ?: 1).coerceAtLeast(1) }
    val pct = if (top.isEmpty()) 0 else top.mapIndexed { i, r -> r.percent * w[i] }.sum() / w.sum().coerceAtLeast(1)
    val ms = leaf.filter { it.task.milestone && it.percent < 100 && it.finish != null }.sortedBy { it.finish }
    val next = ms.firstOrNull()?.let { "${it.task.title} · ${planDate(it.finish)}" }
    val slips = leaf.mapNotNull { PlanCalc.slip(it.task, s) }
    return Summary(
        total = leaf.size,
        completed = leaf.count { it.status == PlanStatus.COMPLETED },
        inProgress = leaf.count { it.status == PlanStatus.IN_PROGRESS },
        planned = leaf.count { it.status == PlanStatus.PLANNED },
        overdue = leaf.count { it.status == PlanStatus.OVERDUE },
        percent = pct,
        start = rows.mapNotNull { it.start }.minOrNull() ?: PlanCalc.parse(p.start),
        finish = rows.mapNotNull { it.finish }.maxOrNull() ?: PlanCalc.parse(p.targetEnd),
        nextMilestone = next,
        milestones30 = ms.count { !it.finish!!.isAfter(today.plusDays(30)) },
        slip = slips.maxOrNull(),
    )
}

// =============================================================================
// One project
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectView(
    project: SpecialProject,
    tasks: List<PlanTask>,
    store: ProjectPlanStore,
    persons: List<PersonEntity>,
    settings: PlannerSettings,
    today: LocalDate,
    tab: PlanTab,
    onTab: (PlanTab) -> Unit,
    onEditProject: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val rows = remember(tasks, settings, today) { PlanCalc.rows(tasks, settings, today) }
    val summary = remember(rows, project) { summarize(rows, project, today, settings) }
    var selected by remember(project.id) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pair<PlanTask, Boolean>?>(null) }
    var zoom by rememberSaveable { mutableStateOf(Zoom.FIT) }
    var offsetDays by remember(project.id) { mutableStateOf(0) }
    var showBaseline by rememberSaveable { mutableStateOf(true) }
    var showLinks by rememberSaveable { mutableStateOf(true) }
    val sel = rows.firstOrNull { it.task.id == selected }

    fun newTask(parent: PlanTask?): PlanTask {
        val start = PlanCalc.nextWorking(today, settings)
        return PlanTask(
            projectId = project.id, parentId = parent?.id, order = store.nextOrder(project.id, parent?.id),
            title = "", start = start.toString(), finish = PlanCalc.finishFor(start, 5, settings).toString(),
            resource = parent?.resource ?: "",
        )
    }
    fun siblings(t: PlanTask) = tasks.filter { it.parentId == t.parentId }.sortedWith(compareBy({ it.order }, { it.createdAt }))
    fun indent(t: PlanTask) {
        val sib = siblings(t); val i = sib.indexOfFirst { it.id == t.id }
        if (i <= 0) { onMessage("Nothing above to indent under"); return }
        val newParent = sib[i - 1]
        store.saveTask(t.copy(parentId = newParent.id, order = store.nextOrder(project.id, newParent.id)))
    }
    fun outdent(t: PlanTask) {
        val parent = tasks.firstOrNull { it.id == t.parentId } ?: run { onMessage("Already at the top level"); return }
        store.saveTask(t.copy(parentId = parent.parentId, order = parent.order + 0.5))
    }
    fun move(t: PlanTask, dir: Int) {
        val sib = siblings(t); val i = sib.indexOfFirst { it.id == t.id }; val j = i + dir
        if (j !in sib.indices) return
        val other = sib[j]
        val (a, b) = if (t.order == other.order) (t.order + dir * 0.5) to other.order else other.order to t.order
        store.saveTasks(listOf(t.copy(order = a), other.copy(order = b)))
    }
    fun shift(r: PlanRow, days: Int) {
        val s0 = PlanCalc.parse(r.task.start) ?: r.start ?: return
        val f0 = PlanCalc.parse(r.task.finish) ?: return
        val dur = PlanCalc.workdays(s0, f0, settings)
        val ns = PlanCalc.nextWorking(s0.plusDays(days.toLong()), settings)
        val nf = if (r.task.milestone) ns else PlanCalc.finishFor(ns, dur, settings)
        store.saveTask(r.task.copy(start = ns.toString(), finish = nf.toString()))
    }
    fun resize(r: PlanRow, days: Int) {
        val f0 = PlanCalc.parse(r.task.finish) ?: return
        val s0 = PlanCalc.parse(r.task.start) ?: f0
        val nf = f0.plusDays(days.toLong()).let { if (it.isBefore(s0)) s0 else it }
        store.saveTask(r.task.copy(finish = nf.toString()))
    }

    // project header
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(parseHexColor(project.color)))
        Spacer(Modifier.width(8.dp))
        Text(project.name, style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
        Spacer(Modifier.width(10.dp))
        HealthChip(project.health)
        Spacer(Modifier.width(10.dp))
        Text(
            listOfNotNull(project.lead.ifBlank { null }?.let { "Lead: $it" }, "${planDate(summary.start)} – ${planDate(summary.finish)}", "${summary.percent}% complete")
                .joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onEditProject) { Icon(Icons.Outlined.Edit, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Edit project") }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PlanTab.entries.forEach { t ->
            val on = t == tab
            Column(Modifier.clickable { onTab(t) }.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (on) KarColors.SkyDeep else KarColors.Slate, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.width(40.dp).height(2.dp).background(if (on) KarColors.SkyDeep else Color.Transparent))
            }
        }
    }
    HorizontalDivider(color = KarColors.Border)
    Spacer(Modifier.height(8.dp))

    if (tab == PlanTab.GANTT || tab == PlanTab.SHEET) {
        // toolbar
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Tool("Add task", Icons.Outlined.Add) { editing = newTask(sel?.task?.let { t -> tasks.firstOrNull { it.id == t.parentId } }) to true }
            Tool("Add subtask", Icons.Outlined.SubdirectoryArrowRight, enabled = sel != null) { sel?.let { editing = newTask(it.task) to true } }
            Tool("Indent", Icons.Outlined.FormatIndentIncrease, enabled = sel != null) { sel?.let { indent(it.task) } }
            Tool("Outdent", Icons.Outlined.FormatIndentDecrease, enabled = sel != null) { sel?.let { outdent(it.task) } }
            Tool("Up", Icons.Outlined.ArrowUpward, enabled = sel != null) { sel?.let { move(it.task, -1) } }
            Tool("Down", Icons.Outlined.ArrowDownward, enabled = sel != null) { sel?.let { move(it.task, 1) } }
            Tool("Edit", Icons.Outlined.Edit, enabled = sel != null) { sel?.let { editing = it.task to false } }
            Tool("Delete", Icons.Outlined.Delete, enabled = sel != null) { sel?.let { store.deleteTask(it.task.id); selected = null; onMessage("Task deleted") } }
            Spacer(Modifier.width(10.dp))
            Tool("Auto-schedule", Icons.Outlined.AutoFixHigh) {
                val changed = PlanCalc.autoSchedule(rows, settings)
                store.saveTasks(changed)
                onMessage(if (changed.isEmpty()) "Schedule already respects all dependencies" else "${changed.size} task(s) moved to respect dependencies")
            }
            Tool("Set baseline", Icons.Outlined.FlagCircle) {
                store.saveTasks(PlanCalc.baseline(tasks.filter { rows.firstOrNull { r -> r.task.id == it.id }?.isGroup != true }))
                onMessage("Baseline saved for ${tasks.size} task(s) — later changes show as variance")
            }
            Tool("Export (Excel)", Icons.Outlined.Download) {
                FileDialogs.save("Export project plan for Excel", "${project.name.replace(Regex("[^A-Za-z0-9 _-]"), "")}-plan.csv")?.let { f ->
                    runCatching { f.writeText(planCsv(project, rows, settings), Charsets.UTF_8) }
                        .onSuccess { onMessage("Plan exported") }.onFailure { onMessage("Export failed: ${it.message}") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }

    when (tab) {
        PlanTab.OVERVIEW -> OverviewTab(project, rows, summary, settings, today, persons) { r -> selected = r.task.id; editing = r.task to false }
        PlanTab.GANTT -> {
            val start0 = summary.start ?: today.minusDays(3)
            val finish0 = summary.finish ?: today.plusDays(30)
            val (wStart, wDays) = if (zoom == Zoom.FIT) {
                val a = start0.minusDays(3); val b = finish0.plusDays(5)
                a to ChronoUnit.DAYS.between(a, b).toInt().coerceIn(14, 730)
            } else {
                val base = (if (today.isAfter(start0) && today.isBefore(finish0)) today.minusDays(7) else start0.minusDays(3)).with(DayOfWeek.MONDAY)
                base.plusDays(offsetDays.toLong()) to zoom.days
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Zoom.entries.forEach { z ->
                    Surface(onClick = { zoom = z; offsetDays = 0 }, color = if (zoom == z) KarColors.SkyLight else Color.Transparent, shape = RoundedCornerShape(6.dp)) {
                        Text(z.label, style = MaterialTheme.typography.labelMedium, color = if (zoom == z) KarColors.SkyDeep else KarColors.Slate,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                if (zoom != Zoom.FIT) {
                    IconButton(onClick = { offsetDays -= zoom.days / 2 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Earlier", tint = KarColors.Slate) }
                    TextButton(onClick = { offsetDays = 0 }) { Text("Today") }
                    IconButton(onClick = { offsetDays += zoom.days / 2 }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Later", tint = KarColors.Slate) }
                }
                Spacer(Modifier.weight(1f))
                Toggle("Baseline", showBaseline) { showBaseline = it }
                Toggle("Dependencies", showLinks) { showLinks = it }
            }
            Legend()
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, KarColors.Border, RoundedCornerShape(8.dp))) {
                if (rows.isEmpty()) EmptyPlan { editing = newTask(null) to true }
                else GanttView(
                    rows = rows, settings = settings, windowStart = wStart, windowDays = wDays, today = today,
                    showBaseline = showBaseline, showLinks = showLinks, selectedId = selected,
                    onSelect = { selected = it.task.id }, onOpen = { selected = it.task.id; editing = it.task to false },
                    onMove = { r, d -> shift(r, d) }, onResize = { r, d -> resize(r, d) },
                )
            }
        }
        PlanTab.SHEET -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).border(1.dp, KarColors.Border, RoundedCornerShape(8.dp))) {
            if (rows.isEmpty()) EmptyPlan { editing = newTask(null) to true }
            else TaskSheet(rows, settings, selected, onSelect = { selected = it.task.id }, onOpen = { selected = it.task.id; editing = it.task to false })
        }
        PlanTab.WORKLOAD -> WorkloadTab(rows, settings, today)
    }

    editing?.let { (t, isNew) ->
        PlanTaskDialog(
            task = t, isNew = isNew, allRows = rows, persons = persons, settings = settings,
            onDismiss = { editing = null },
            onSave = { store.saveTask(it); selected = it.id; editing = null },
            onDelete = if (isNew) null else ({ store.deleteTask(t.id); selected = null; editing = null; onMessage("Task deleted") }),
        )
    }
}

@Composable
private fun EmptyPlan(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Timeline, null, tint = KarColors.SkyMid, modifier = Modifier.size(42.dp))
        Text("No tasks in this plan yet", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
        Text("Add phases as tasks, then subtasks under them. Link tasks with dependencies and press Auto-schedule.",
            style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, textAlign = TextAlign.Center)
        TextButton(onClick = onAdd) { Text("+ Add the first task") }
    }
}

@Composable
private fun Tool(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = KarColors.Slate)
        Spacer(Modifier.width(4.dp))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 2.dp)) {
        PlanStatus.ALL.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(PlanStatusColors[s]!!))
                Text(" $s", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(width = 14.dp, height = 3.dp).background(KarColors.SlateLight)); Text(" Baseline", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate) }
        Text("◆ Milestone   ▬ Summary   │ Actual start/end   ┆ Today", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
        Text("Drag a bar to move it · drag its right edge to change the finish · double-click to edit",
            style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
    }
}

// =============================================================================
// Task sheet (all plan columns, like the Excel sheet)
// =============================================================================

@Composable
private fun TaskSheet(rows: List<PlanRow>, settings: PlannerSettings, selected: String?, onSelect: (PlanRow) -> Unit, onOpen: (PlanRow) -> Unit) {
    val wbs = rows.associate { it.task.id to it.wbs }
    Column(Modifier.fillMaxSize().background(KarColors.Card)) {
        Row(Modifier.fillMaxWidth().background(KarColors.SkyWash).padding(horizontal = 10.dp, vertical = 8.dp)) {
            H("WBS", 0.5f); H("Task", 2.6f); H("Priority", 0.7f); H("Status", 0.85f); H("Resource", 1f)
            H("Base start", 0.85f); H("Base end", 0.85f); H("Start", 0.85f); H("Finish", 0.85f); H("Days", 0.45f)
            H("Pred.", 0.7f); H("Work h", 0.55f); H("%", 0.45f); H("Act. start", 0.85f); H("Act. end", 0.85f); H("Var.", 0.55f); H("Notes", 1.4f)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { _, r -> r.task.id }) { i, r ->
                val t = r.task
                val slip = if (r.isGroup) null else PlanCalc.slip(t, settings)
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (t.id == selected) KarColors.SkyLight else if (i % 2 == 1) KarColors.Tint else Color.Transparent)
                        .clickable { onSelect(r) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    C(r.wbs, 0.5f, KarColors.Slate)
                    Text(
                        (if (t.milestone) "◆ " else "") + t.title,
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (r.isGroup) FontWeight.Bold else FontWeight.Normal,
                        color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(2.6f).padding(start = (r.depth * 12).dp, end = 6.dp).clickable { onOpen(r) },
                    )
                    Box(Modifier.weight(0.7f)) { Badge(PlanPriority.label(t.priority), priorityColorOf(t.priority)) }
                    Box(Modifier.weight(0.85f)) { Badge(r.status, PlanStatusColors[r.status] ?: KarColors.Slate) }
                    C(t.resource.ifBlank { "–" }, 1f)
                    C(planDate(PlanCalc.parse(t.baselineStart)), 0.85f, KarColors.Slate); C(planDate(PlanCalc.parse(t.baselineEnd)), 0.85f, KarColors.Slate)
                    C(planDate(r.start), 0.85f); C(planDate(r.finish), 0.85f, if (r.status == PlanStatus.OVERDUE) KarColors.Red else KarColors.Ink)
                    C(r.duration?.toString() ?: "–", 0.45f)
                    C(t.predecessors.joinToString(",") { d -> (wbs[d.taskId] ?: "?") + (if (d.type != "FS") d.type else "") + (if (d.lag != 0) (if (d.lag > 0) "+${d.lag}" else "${d.lag}") else "") }.ifBlank { "–" }, 0.7f)
                    C(t.workHours?.let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) } ?: "–", 0.55f)
                    C("${r.percent}%", 0.45f, PlanStatusColors[r.status] ?: KarColors.Ink)
                    C(planDate(PlanCalc.parse(t.actualStart)), 0.85f); C(planDate(PlanCalc.parse(t.actualEnd)), 0.85f)
                    C(slip?.let { if (it > 0) "+${it}d" else if (it < 0) "${it}d" else "0" } ?: "–", 0.55f, if ((slip ?: 0) > 0) KarColors.Red else if ((slip ?: 0) < 0) KarColors.Green else KarColors.Slate)
                    C(t.notes.ifBlank { "–" }, 1.4f, KarColors.Slate)
                }
                HorizontalDivider(color = KarColors.Border.copy(alpha = 0.6f))
            }
        }
    }
}

// =============================================================================
// Overview (dashboard with slicers, like the Excel project dashboard)
// =============================================================================

private enum class DueWindow(val label: String) { ALL("All dates"), WEEK("This week"), MONTH("This month"), NEXT30("Next 30 days"), QUARTER("This quarter") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    project: SpecialProject,
    rows: List<PlanRow>,
    summary: Summary,
    settings: PlannerSettings,
    today: LocalDate,
    persons: List<PersonEntity>,
    onOpen: (PlanRow) -> Unit,
) {
    var fPriority by remember { mutableStateOf(setOf<String>()) }
    var fStatus by remember { mutableStateOf(setOf<String>()) }
    var fResource by remember { mutableStateOf(setOf<String>()) }
    var fWindow by remember { mutableStateOf(DueWindow.ALL) }
    val leaf = rows.filter { !it.isGroup }
    val resources = leaf.map { it.task.resource.ifBlank { "Unassigned" } }.distinct().sorted()
    val (wFrom, wTo) = when (fWindow) {
        DueWindow.WEEK -> today.with(DayOfWeek.MONDAY) to today.with(DayOfWeek.SUNDAY)
        DueWindow.MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
        DueWindow.NEXT30 -> today to today.plusDays(30)
        DueWindow.QUARTER -> today.withMonth(((today.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1).let { it to it.plusMonths(3).minusDays(1) }
        DueWindow.ALL -> LocalDate.MIN to LocalDate.MAX
    }
    val filtered = leaf.filter { r ->
        (fPriority.isEmpty() || r.task.priority in fPriority) &&
            (fStatus.isEmpty() || r.status in fStatus) &&
            (fResource.isEmpty() || r.task.resource.ifBlank { "Unassigned" } in fResource) &&
            (fWindow == DueWindow.ALL || (r.finish != null && !r.finish.isBefore(wFrom) && !r.finish.isAfter(wTo)))
    }
    val anyFilter = fPriority.isNotEmpty() || fStatus.isNotEmpty() || fResource.isNotEmpty() || fWindow != DueWindow.ALL

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // project summary card
            DepthCard(Modifier.weight(1.1f), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Project summary", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Spacer(Modifier.height(8.dp))
                    InfoLine("Project status") { HealthChip(project.health) }
                    InfoLine("Lead") { Text(project.lead.ifBlank { "–" }, style = MaterialTheme.typography.bodySmall, color = KarColors.Ink) }
                    InfoLine("Start date") { Text(planDate(summary.start), style = MaterialTheme.typography.bodySmall, color = KarColors.Ink) }
                    InfoLine("End date") { Text(planDate(summary.finish), style = MaterialTheme.typography.bodySmall, color = KarColors.Ink) }
                    InfoLine("Target end") {
                        val late = project.targetEnd != null && summary.finish != null && summary.finish.isAfter(LocalDate.parse(project.targetEnd))
                        Text(planDate(PlanCalc.parse(project.targetEnd)) + if (late) "  (plan ends later)" else "", style = MaterialTheme.typography.bodySmall,
                            color = if (late) KarColors.Red else KarColors.Ink)
                    }
                    InfoLine("Next milestone") { Text(summary.nextMilestone ?: "–", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (project.objective.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(project.objective, style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Total tasks", "${summary.total}", "${leaf.count { it.task.milestone }} milestones", KarColors.SkyDeep, Modifier.weight(1f))
                    Kpi("Completed", "${summary.completed}", "done", KarColors.Green, Modifier.weight(1f))
                    Kpi("In progress", "${summary.inProgress}", "under way", KarColors.SkyMid, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kpi("Planned", "${summary.planned}", "not started", KarColors.Slate, Modifier.weight(1f))
                    Kpi("Overdue", "${summary.overdue}", "past finish date", if (summary.overdue > 0) KarColors.Red else KarColors.Green, Modifier.weight(1f))
                    Kpi("vs baseline", summary.slip?.let { if (it > 0) "+$it d" else "on plan" } ?: "–", "latest slip", if ((summary.slip ?: 0) > 0) KarColors.Red else KarColors.Green, Modifier.weight(1f))
                }
            }
            DepthCard(Modifier.weight(0.8f), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Project completion", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Spacer(Modifier.height(8.dp))
                    Donut(listOf(summary.percent.toFloat() to KarColors.Green, (100 - summary.percent).toFloat() to KarColors.Border), "${summary.percent}%", "complete")
                }
            }
        }

        // slicers
        DepthCard(Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, modifier = Modifier.weight(1f))
                    if (anyFilter) TextButton(onClick = { fPriority = emptySet(); fStatus = emptySet(); fResource = emptySet(); fWindow = DueWindow.ALL }) { Text("Clear filters") }
                }
                Slicer("Priority", PlanPriority.ALL.map { it to PlanPriority.label(it) }, fPriority) { fPriority = it }
                Slicer("Status", PlanStatus.ALL.map { it to it }, fStatus) { fStatus = it }
                if (resources.isNotEmpty()) Slicer("Resource", resources.map { it to it }, fResource) { fResource = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Task due date", style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.width(100.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DueWindow.entries.forEach { w -> SliceChip(w.label, fWindow == w) { fWindow = w } }
                    }
                }
            }
        }

        // charts
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartCard("Task status", Modifier.weight(1f)) {
                val parts = PlanStatus.ALL.map { s -> s to filtered.count { it.status == s } }
                Donut(parts.map { (s, n) -> n.toFloat() to PlanStatusColors[s]!! }, "${filtered.size}", "tasks")
                LegendList(parts.map { (s, n) -> Triple(s, n, PlanStatusColors[s]!!) })
            }
            ChartCard("Task priority", Modifier.weight(1f)) {
                val parts = PlanPriority.ALL.map { p -> p to filtered.count { it.task.priority == p } }
                Donut(parts.map { (p, n) -> n.toFloat() to priorityColorOf(p) }, "${filtered.size}", "tasks")
                LegendList(parts.map { (p, n) -> Triple(PlanPriority.label(p), n, priorityColorOf(p)) })
            }
            ChartCard("Tasks by resource", Modifier.weight(1.3f)) {
                val byRes = filtered.groupBy { it.task.resource.ifBlank { "Unassigned" } }.toList().sortedByDescending { it.second.size }.take(8)
                val max = (byRes.maxOfOrNull { it.second.size } ?: 1).coerceAtLeast(1)
                if (byRes.isEmpty()) Text("No tasks", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight)
                byRes.forEach { (name, list) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.labelSmall, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                        Row(Modifier.weight(1f).height(12.dp)) {
                            Row(Modifier.fillMaxWidth(list.size.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))) {
                                PlanStatus.ALL.forEach { s ->
                                    val n = list.count { it.status == s }
                                    if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(PlanStatusColors[s]!!))
                                }
                            }
                        }
                        Text(" ${list.size}", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, modifier = Modifier.width(30.dp))
                    }
                }
                Text("Bar colours = task status (see legend at left)", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
            }
        }

        // task list (filtered) + milestones
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartCard(if (anyFilter) "Filtered tasks (${filtered.size})" else "Open tasks by finish date", Modifier.weight(1.6f)) {
                val list = (if (anyFilter) filtered else filtered.filter { it.status != PlanStatus.COMPLETED }).sortedBy { it.finish ?: LocalDate.MAX }.take(40)
                if (list.isEmpty()) Text("Nothing to show", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight)
                list.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        C(r.wbs, 0.4f, KarColors.Slate)
                        Text((if (r.task.milestone) "◆ " else "") + r.task.title, style = MaterialTheme.typography.labelMedium, color = KarColors.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2.4f))
                        C(r.task.resource.ifBlank { "–" }, 1f, KarColors.Slate)
                        C(planDate(r.finish), 0.9f, if (r.status == PlanStatus.OVERDUE) KarColors.Red else KarColors.Ink)
                        Box(Modifier.weight(0.9f)) { Badge(r.status, PlanStatusColors[r.status]!!) }
                        C("${r.percent}%", 0.4f)
                    }
                    HorizontalDivider(color = KarColors.Border.copy(alpha = 0.6f))
                }
            }
            ChartCard("Milestones", Modifier.weight(1f)) {
                val ms = leaf.filter { it.task.milestone }.sortedBy { it.finish ?: LocalDate.MAX }
                if (ms.isEmpty()) Text("Mark key dates as milestones in a task to see them here.", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight)
                ms.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("◆", color = PlanStatusColors[r.status]!!, modifier = Modifier.width(18.dp))
                        Text(r.task.title, style = MaterialTheme.typography.labelMedium, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(planDate(r.finish), style = MaterialTheme.typography.labelSmall, color = if (r.status == PlanStatus.OVERDUE) KarColors.Red else KarColors.Slate)
                    }
                }
                val slipped = leaf.mapNotNull { r -> PlanCalc.slip(r.task, settings)?.takeIf { it > 0 }?.let { r to it } }.sortedByDescending { it.second }
                if (slipped.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Slipped vs baseline", style = MaterialTheme.typography.labelLarge, color = KarColors.Red)
                    slipped.take(8).forEach { (r, d) ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(vertical = 3.dp)) {
                            Text(r.task.title, style = MaterialTheme.typography.labelSmall, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("+$d d", style = MaterialTheme.typography.labelSmall, color = KarColors.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    @Suppress("UNUSED_VARIABLE") val p = persons
}

@Composable
private fun InfoLine(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, modifier = Modifier.width(110.dp))
        value()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Slicer(label: String, options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.width(100.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (k, l) -> SliceChip(l, k in selected) { onChange(if (k in selected) selected - k else selected + k) } }
        }
    }
}

@Composable
private fun SliceChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) Color.White else KarColors.Ink,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (on) KarColors.SkyDeep else KarColors.SkyWash)
            .border(1.dp, if (on) KarColors.SkyDeep else KarColors.Border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun ChartCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    DepthCard(modifier, elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun Donut(parts: List<Pair<Float, Color>>, center: String, sub: String) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    val track = KarColors.Border
    Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (total <= 0f) drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            else {
                var a = -90f
                parts.forEach { (v, c) ->
                    if (v > 0f) {
                        val sweep = 360f * v / total
                        drawArc(c, a, (sweep - 1.5f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        a += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KarColors.Navy)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
        }
    }
}

@Composable
private fun LegendList(items: List<Triple<String, Int, Color>>) {
    Column(Modifier.padding(top = 6.dp)) {
        items.forEach { (l, n, c) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Text("  $l", style = MaterialTheme.typography.labelSmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                Text("$n", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// Workload
// =============================================================================

@Composable
private fun WorkloadTab(rows: List<PlanRow>, settings: PlannerSettings, today: LocalDate) {
    val leaf = rows.filter { !it.isGroup && !it.task.milestone }
    val weeks = (0 until 10).map { today.with(DayOfWeek.MONDAY).plusWeeks(it.toLong()) }
    val dayHours = (settings.workDayMinutes / 60.0).coerceAtLeast(1.0)
    data class Load(val name: String, val tasks: Int, val open: Int, val overdue: Int, val hours: Double, val perWeek: List<Double>)
    val loads = leaf.groupBy { it.task.resource.ifBlank { "Unassigned" } }.map { (name, list) ->
        val perWeek = weeks.map { w ->
            list.filter { it.status != PlanStatus.COMPLETED }.sumOf { r ->
                val s = r.start ?: return@sumOf 0.0
                val f = r.finish ?: return@sumOf 0.0
                val days = PlanCalc.workdays(s, f, settings).coerceAtLeast(1)
                val perDay = (r.task.workHours ?: (days * dayHours)) / days
                var d = maxOf(s, w); val end = minOf(f, w.plusDays(6)); var h = 0.0
                while (!d.isAfter(end)) { if (PlanCalc.isWorking(d, settings)) h += perDay; d = d.plusDays(1) }
                h
            }
        }
        Load(name, list.size, list.count { it.status != PlanStatus.COMPLETED }, list.count { it.status == PlanStatus.OVERDUE },
            list.sumOf { it.task.workHours ?: 0.0 }, perWeek)
    }.sortedByDescending { it.open }
    val capacity = dayHours * settings.workDays.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Planned hours per week for open tasks. Tasks without a work estimate count as full working days. Capacity: ${"%.0f".format(capacity)} h per week.",
            style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
        Spacer(Modifier.height(8.dp))
        DepthCard(Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().background(KarColors.SkyWash).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    H("Resource", 1.6f); H("Tasks", 0.6f); H("Open", 0.6f); H("Overdue", 0.7f); H("Work h", 0.7f)
                    weeks.forEach { w -> H("${w.dayOfMonth}/${w.monthValue}", 0.65f) }
                }
                if (loads.isEmpty()) Text("No tasks yet", color = KarColors.SlateLight, modifier = Modifier.padding(16.dp))
                loads.forEach { l ->
                    HorizontalDivider(color = KarColors.Border)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        C(l.name, 1.6f); C("${l.tasks}", 0.6f); C("${l.open}", 0.6f); C("${l.overdue}", 0.7f, if (l.overdue > 0) KarColors.Red else KarColors.Slate)
                        C(if (l.hours > 0) "%.0f".format(l.hours) else "–", 0.7f)
                        l.perWeek.forEach { h ->
                            val ratio = (h / capacity).toFloat()
                            val c = when { h <= 0.0 -> Color.Transparent; ratio > 1.0f -> KarColors.Red; ratio > 0.8f -> KarColors.Amber; else -> KarColors.SkyMid }
                            Box(Modifier.weight(0.65f).padding(horizontal = 2.dp).height(22.dp).clip(RoundedCornerShape(4.dp))
                                .background(c.copy(alpha = if (h <= 0.0) 0f else (0.25f + 0.6f * ratio.coerceAtMost(1f)))), contentAlignment = Alignment.Center) {
                                if (h > 0.0) Text("%.0f".format(h), style = MaterialTheme.typography.labelSmall, color = KarColors.Ink)
                            }
                        }
                    }
                }
            }
        }
        Text("Amber = over 80% of capacity · red = over capacity", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, modifier = Modifier.padding(top = 6.dp))
    }
}

// =============================================================================
// Small shared pieces
// =============================================================================

@Composable
private fun RowScope.H(text: String, w: Float) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Slate, maxLines = 1, modifier = Modifier.weight(w))
}

@Composable
private fun RowScope.C(text: String, w: Float, color: Color = KarColors.Ink) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(w).padding(end = 6.dp))
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun HealthChip(h: String) = Badge(ProjectHealth.label(h), healthColor(h))

@Composable
private fun ProgressBar(f: Float, modifier: Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(KarColors.Border)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(KarColors.Green))
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, accent: Color, modifier: Modifier) {
    DepthCard(modifier, elevation = 2.dp, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = KarColors.Navy)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1)
            }
        }
    }
}

/** CSV of the whole plan (opens in Excel). */
private fun planCsv(p: SpecialProject, rows: List<PlanRow>, s: PlannerSettings): String {
    fun c(x: String?) = "\"" + (x ?: "").replace("\"", "\"\"") + "\""
    val wbs = rows.associate { it.task.id to it.wbs }
    val sb = StringBuilder("﻿")
    sb.append("PROJECT,${c(p.name)}\nLEAD,${c(p.lead)}\nSTATUS,${c(ProjectHealth.label(p.health))}\n\n")
    sb.append("WBS,TASK,MILESTONE,PRIORITY,STATUS,RESOURCE,BASELINE START,BASELINE END,START,FINISH,DURATION (DAYS),PREDECESSORS,WORK (H),% COMPLETE,ACTUAL START,ACTUAL END,VARIANCE (DAYS),NOTES\n")
    rows.forEach { r ->
        val t = r.task
        sb.append(listOf(
            r.wbs, "  ".repeat(r.depth) + t.title, if (t.milestone) "YES" else "", t.priority, r.status, t.resource,
            t.baselineStart, t.baselineEnd, r.start?.toString(), r.finish?.toString(), r.duration?.toString(),
            t.predecessors.joinToString(" ") { (wbs[it.taskId] ?: "?") + it.type + (if (it.lag != 0) "${it.lag}" else "") },
            t.workHours?.toString(), "${r.percent}", t.actualStart, t.actualEnd, if (r.isGroup) "" else PlanCalc.slip(t, s)?.toString(), t.notes,
        ).joinToString(",") { c(it) }).append('\n')
    }
    return sb.toString()
}

