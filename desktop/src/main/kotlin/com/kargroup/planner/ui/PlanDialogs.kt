package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.desktop.Dependency
import com.kargroup.planner.desktop.PlanCalc
import com.kargroup.planner.desktop.PlanPriority
import com.kargroup.planner.desktop.PlanRow
import com.kargroup.planner.desktop.PlanTask
import com.kargroup.planner.desktop.ProjectHealth
import com.kargroup.planner.desktop.SpecialProject
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import java.time.LocalDate

fun priorityColorOf(p: String): Color = when (p) {
    PlanPriority.CRITICAL -> Color(0xFF9F1239)
    PlanPriority.HIGH -> Color(0xFFEF4444)
    PlanPriority.LOW -> Color(0xFF64748B)
    else -> Color(0xFF0EA5E9)
}

fun healthColor(h: String): Color = when (h) {
    ProjectHealth.AT_RISK -> Color(0xFFF59E0B)
    ProjectHealth.OFF_TRACK -> Color(0xFFEF4444)
    ProjectHealth.ON_HOLD -> Color(0xFF64748B)
    ProjectHealth.COMPLETED -> Color(0xFF10B981)
    else -> Color(0xFF10B981)
}

/** A date box that opens a calendar; [onChange] gets null when cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateBox(label: String, value: String?, onChange: (String?) -> Unit, modifier: Modifier = Modifier, clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Surface(
        onClick = { open = true },
        color = KarColors.Card,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, KarColors.Border),
        modifier = modifier,
    ) {
        Row(Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                Text(PlanCalc.parse(value)?.let { planDate(it) } ?: "Not set", style = MaterialTheme.typography.bodySmall,
                    color = if (value == null) KarColors.SlateLight else KarColors.Ink)
            }
            if (clearable && value != null) {
                IconButton(onClick = { onChange(null) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Clear", tint = KarColors.SlateLight, modifier = Modifier.size(14.dp)) }
            } else Icon(Icons.Outlined.Event, null, tint = KarColors.SlateLight, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        }
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(PlanCalc.parse(value) ?: Ist.today()))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { onChange(Ist.pickerMillisToDate(it).toString()) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }
}

@Composable
private fun Field(label: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = KarColors.Navy, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

// =============================================================================
// Special project (create / edit)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecialProjectDialog(
    project: SpecialProject,
    persons: List<PersonEntity>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SpecialProject) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(project.name) }
    var lead by remember { mutableStateOf(project.lead) }
    var health by remember { mutableStateOf(project.health) }
    var objective by remember { mutableStateOf(project.objective) }
    var start by remember { mutableStateOf(project.start) }
    var end by remember { mutableStateOf(project.targetEnd) }
    var color by remember { mutableStateOf(project.color) }
    var archived by remember { mutableStateOf(project.archived) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text(if (isNew) "New special project" else "Edit special project") },
        text = {
            Column(Modifier.widthIn(min = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PersonPicker(value = lead, onValueChange = { lead = it }, people = persons, label = "Project lead", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = objective, onValueChange = { objective = it }, label = { Text("Objective / scope") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                Field("Dates")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBox("Start", start, { start = it }, Modifier.weight(1f))
                    DateBox("Target end", end, { end = it }, Modifier.weight(1f))
                }
                Field("Project status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProjectHealth.ALL.forEach { h -> PillChip(ProjectHealth.label(h), health == h, healthColor(h)) { health = h } }
                }
                Field("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#0EA5E9", "#2563EB", "#7C3AED", "#DB2777", "#DC2626", "#D97706", "#059669", "#475569").forEach { c ->
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(parseHexColor(c))
                                .border(if (c == color) 3.dp else 0.dp, KarColors.Navy, CircleShape)
                                .clickable { color = c }
                        )
                    }
                }
                if (!isNew) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    Text("Archived (hidden from the portfolio)", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = archived, onCheckedChange = { archived = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(project.copy(name = name.trim(), lead = lead.trim(), health = health, objective = objective.trim(),
                    start = start, targetEnd = end, color = color, archived = archived))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = KarColors.Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KarColors.Card,
            title = { Text("Delete \"${project.name}\"?") },
            text = { Text("The project and all its planned tasks will be deleted. This cannot be undone.", color = KarColors.Slate) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = KarColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}


// =============================================================================
// Plan task (create / edit)
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanTaskDialog(
    task: PlanTask,
    isNew: Boolean,
    allRows: List<PlanRow>,
    persons: List<PersonEntity>,
    settings: PlannerSettings,
    onDismiss: () -> Unit,
    onSave: (PlanTask) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(task.title) }
    var priority by remember { mutableStateOf(task.priority) }
    var resource by remember { mutableStateOf(task.resource) }
    var start by remember { mutableStateOf(task.start) }
    var finish by remember { mutableStateOf(task.finish) }
    var milestone by remember { mutableStateOf(task.milestone) }
    var percent by remember { mutableStateOf(task.percent.toFloat()) }
    var actualStart by remember { mutableStateOf(task.actualStart) }
    var actualEnd by remember { mutableStateOf(task.actualEnd) }
    var baselineStart by remember { mutableStateOf(task.baselineStart) }
    var baselineEnd by remember { mutableStateOf(task.baselineEnd) }
    var work by remember { mutableStateOf(task.workHours?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var preds by remember { mutableStateOf(task.predecessors) }
    var constraint by remember { mutableStateOf(task.constraint) }
    var constraintDate by remember { mutableStateOf(task.constraintDate) }
    var notes by remember { mutableStateOf(task.notes) }
    var addDep by remember { mutableStateOf(false) }
    val isGroup = allRows.any { it.task.parentId == task.id }
    val wbsOf = allRows.associate { it.task.id to it.wbs }
    val s = PlanCalc.parse(start)
    val f = PlanCalc.parse(finish)
    val duration = if (s != null && f != null && !f.isBefore(s)) PlanCalc.workdays(s, f, settings) else null
    var durText by remember { mutableStateOf(duration?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text(if (isNew) "New task" else "Task ${wbsOf[task.id] ?: ""}") },
        text = {
            Column(Modifier.widthIn(min = 620.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Field("Priority")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlanPriority.ALL.forEach { p -> PillChip(PlanPriority.label(p), priority == p, priorityColorOf(p)) { priority = p } }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Field("Resource")
                        PersonPicker(value = resource, onValueChange = { resource = it }, people = persons, label = "Assigned to", modifier = Modifier.fillMaxWidth())
                    }
                }
                if (isGroup) {
                    Text("This task has subtasks: its dates and % complete are calculated from them.",
                        style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, modifier = Modifier.padding(top = 10.dp))
                } else {
                    Field("Schedule")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateBox("Start", start, {
                            start = it
                            val sd = PlanCalc.parse(it); val d = durText.toIntOrNull()
                            if (sd != null && d != null && d > 0) finish = PlanCalc.finishFor(sd, d, settings).toString()
                        }, Modifier.weight(1f))
                        OutlinedTextField(
                            value = durText,
                            onValueChange = { v ->
                                durText = v.filter { it.isDigit() }.take(4)
                                val d = durText.toIntOrNull(); val sd = PlanCalc.parse(start)
                                if (d != null && d > 0 && sd != null) finish = PlanCalc.finishFor(sd, d, settings).toString()
                            },
                            label = { Text("Working days") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(130.dp),
                        )
                        DateBox("Finish", finish, {
                            finish = it
                            val sd = PlanCalc.parse(start); val fd = PlanCalc.parse(it)
                            durText = if (sd != null && fd != null && !fd.isBefore(sd)) PlanCalc.workdays(sd, fd, settings).toString() else ""
                        }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Switch(checked = milestone, onCheckedChange = { milestone = it; if (it) start = finish }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
                        Spacer(Modifier.width(8.dp))
                        Text("Milestone (a key date, shown as ◆)", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink)
                    }
                    Field("% complete")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = percent, onValueChange = { percent = (Math.round(it / 5f) * 5).toFloat() }, valueRange = 0f..100f, steps = 19,
                            colors = SliderDefaults.colors(thumbColor = KarColors.SkyDeep, activeTrackColor = KarColors.SkyDeep),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("${percent.toInt()}%", fontWeight = FontWeight.Bold, color = KarColors.SkyDeep, modifier = Modifier.width(48.dp))
                    }
                    Field("Actual dates")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateBox("Actual start", actualStart, { actualStart = it }, Modifier.weight(1f))
                        DateBox("Actual end", actualEnd, { actualEnd = it; if (it != null) percent = 100f }, Modifier.weight(1f))
                        OutlinedTextField(
                            value = work, onValueChange = { v -> work = v.filter { it.isDigit() || it == '.' }.take(6) },
                            label = { Text("Work (hours)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(130.dp),
                        )
                    }
                    Field("Baseline")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateBox("Baseline start", baselineStart, { baselineStart = it }, Modifier.weight(1f))
                        DateBox("Baseline end", baselineEnd, { baselineEnd = it }, Modifier.weight(1f))
                        TextButton(onClick = { baselineStart = start; baselineEnd = finish }) { Text("Use current dates") }
                    }
                }

                Field("Depends on (predecessors)")
                preds.forEachIndexed { i, d ->
                    val pr = allRows.firstOrNull { it.task.id == d.taskId }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("${pr?.wbs ?: "?"}  ${pr?.task?.title ?: "(deleted)"}", style = MaterialTheme.typography.bodySmall, color = KarColors.Ink,
                            maxLines = 1, modifier = Modifier.weight(1f))
                        SmallMenu(
                            d.type,
                            listOf("FS" to "Finish → Start", "SS" to "Start → Start", "FF" to "Finish → Finish", "SF" to "Start → Finish"),
                        ) { t -> preds = preds.toMutableList().also { it[i] = d.copy(type = t) } }
                        OutlinedTextField(
                            value = d.lag.toString(),
                            onValueChange = { v -> v.trim().let { t -> (if (t == "-" ) 0 else t.toIntOrNull())?.let { n -> preds = preds.toMutableList().also { it[i] = d.copy(lag = n.coerceIn(-365, 365)) } } } },
                            label = { Text("Lag days") }, singleLine = true, modifier = Modifier.width(100.dp),
                        )
                        IconButton(onClick = { preds = preds.filterIndexed { k, _ -> k != i } }) { Icon(Icons.Outlined.Close, "Remove", tint = KarColors.SlateLight) }
                    }
                }
                Box {
                    TextButton(onClick = { addDep = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Add dependency") }
                    DropdownMenu(expanded = addDep, onDismissRequest = { addDep = false }) {
                        val excluded = descendantsAndSelf(task.id, allRows) + preds.map { it.taskId }
                        allRows.filter { it.task.id !in excluded }.forEach { r ->
                            DropdownMenuItem(text = { Text("${r.wbs}  ${r.task.title}") }, onClick = { preds = preds + Dependency(r.task.id); addDep = false })
                        }
                    }
                }
                Field("Constraint")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallMenu(
                        constraint ?: "NONE",
                        listOf("NONE" to "As soon as possible", "SNET" to "Start no earlier than", "FNLT" to "Finish no later than"),
                    ) { constraint = if (it == "NONE") null else it }
                    if (constraint != null) DateBox("Constraint date", constraintDate, { constraintDate = it }, Modifier.width(200.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val sd = PlanCalc.parse(start); val fd = PlanCalc.parse(finish)
                val fixedStart = if (sd != null && fd != null && sd.isAfter(fd)) finish else start
                onSave(
                    task.copy(
                        title = title.trim(), priority = priority, resource = resource.trim(),
                        start = if (milestone) finish else fixedStart, finish = finish, milestone = milestone,
                        percent = percent.toInt().coerceIn(0, 100),
                        actualStart = actualStart, actualEnd = actualEnd, baselineStart = baselineStart, baselineEnd = baselineEnd,
                        workHours = work.toDoubleOrNull()?.takeIf { it > 0 },
                        predecessors = preds, constraint = constraint, constraintDate = if (constraint == null) null else constraintDate,
                        notes = notes.trim(),
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = KarColors.Red) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun descendantsAndSelf(id: String, rows: List<PlanRow>): Set<String> {
    val out = HashSet<String>().apply { add(id) }
    var grew = true
    while (grew) { grew = false; rows.forEach { if (it.task.parentId in out && out.add(it.task.id)) grew = true } }
    return out
}

@Composable
fun SmallMenu(value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, color = KarColors.SkyWash, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, KarColors.Border)) {
            Text((options.firstOrNull { it.first == value }?.second ?: value) + "  ▾", style = MaterialTheme.typography.labelMedium, color = KarColors.Ink,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(k) }) }
        }
    }
}

@Suppress("unused")
private val unusedDivider: @Composable () -> Unit = { HorizontalDivider() }

@Suppress("unused")
private fun unusedDate(): LocalDate = LocalDate.now()
