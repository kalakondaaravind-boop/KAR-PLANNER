package com.kargroup.planner.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.effectiveStatus
import com.kargroup.planner.desktop.Tag
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist

private enum class SortCol { TITLE, OWNER, PROJECT, PRIORITY, TARGET, STATUS, ESTIMATE }

/** Bulk actions the table can run on the selected activities. */
class BulkActions(
    val assign: (Set<String>, String?) -> Unit,
    val status: (Set<String>, String) -> Unit,
    val priority: (Set<String>, String?) -> Unit,
    val currentStatus: (Set<String>, String) -> Unit,
    val reschedule: (Set<String>, String, String) -> Unit,
    val complete: (Set<String>, String) -> Unit,
    val delete: (Set<String>) -> Unit,
    val addTag: (Set<String>, String) -> Unit,
    val removeTag: (Set<String>, String) -> Unit,
    val exportCsv: (List<TaskEntity>) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TableScreen(
    tasks: List<TaskEntity>,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    tags: List<Tag>,
    tagsOf: (String) -> Set<String>,
    tagColor: (String) -> String,
    today: String,
    onOpen: (TaskEntity) -> Unit,
    bulk: BulkActions,
) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    var includeDone by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortCol.TARGET) }
    var asc by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf<String?>(null) } // reschedule / status / complete / delete
    val index = remember(tasks) { TaskIndex(tasks) }
    val projectName = remember(projects) { projects.associate { it.id to it.name } }

    val rows = remember(tasks, filter, includeDone, sort, asc, today, tagsOf) {
        val base = tasks.filter { (includeDone || !it.done) && filter.matches(it, tagsOf, today) }
        val cmp: Comparator<TaskEntity> = when (sort) {
            SortCol.TITLE -> compareBy { it.text }
            SortCol.OWNER -> compareBy { it.assignee ?: "~" }
            SortCol.PROJECT -> compareBy { it.projectId?.let { id -> projectName[id] } ?: "~" }
            SortCol.PRIORITY -> compareBy { Priority.rank(it.priority) }
            SortCol.TARGET -> compareBy<TaskEntity> { it.dueDate ?: "9999-99-99" }.thenBy { it.dueTime ?: "99:99" }
            SortCol.STATUS -> compareBy { listOf(TaskStatus.INITIATED, TaskStatus.NOT_STARTED, TaskStatus.COMPLETED).indexOf(it.effectiveStatus) }
            SortCol.ESTIMATE -> compareBy { it.estimatedMinutes ?: Int.MAX_VALUE }
        }
        base.sortedWith(if (asc) cmp else cmp.reversed())
    }
    val visibleIds = rows.map { it.id }.toSet()
    val sel = selected intersect visibleIds

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Table", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
                Text("${rows.size} activities · click a heading to sort · tick rows to edit many at once",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
            }
            Text("Show completed", style = MaterialTheme.typography.labelMedium, color = KarColors.Slate)
            Spacer(Modifier.width(6.dp))
            Switch(checked = includeDone, onCheckedChange = { includeDone = it }, colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep))
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = { bulk.exportCsv(rows) }) {
                Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export view")
            }
        }
        Spacer(Modifier.height(10.dp))
        FilterBar(filter, { filter = it }, persons, projects, tags)
        Spacer(Modifier.height(10.dp))

        // ------------------------------------------------------------ bulk bar
        if (sel.isNotEmpty()) {
            Surface(color = KarColors.SkyLight, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("${sel.size} selected", fontWeight = FontWeight.Bold, color = KarColors.SkyDeep,
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
                    MenuButton("Assign to", listOf<Pair<String?, String>>(null to "Unassigned") + persons.map { it.name to it.name }) { bulk.assign(sel, it) }
                    MenuButton("Status", listOf<Pair<String?, String>>(TaskStatus.NOT_STARTED to "Not started", TaskStatus.INITIATED to "Initiated")) { it?.let { s -> bulk.status(sel, s) } }
                    MenuButton("Priority", listOf<Pair<String?, String>>(Priority.VERY_HIGH to "Very High", Priority.HIGH to "High", Priority.MEDIUM to "Medium", null to "Standard")) { bulk.priority(sel, it) }
                    if (tags.isNotEmpty()) {
                        MenuButton("Add tag", tags.map { it.name to it.name }) { it?.let { t -> bulk.addTag(sel, t) } }
                        MenuButton("Remove tag", tags.map { it.name to it.name }) { it?.let { t -> bulk.removeTag(sel, t) } }
                    }
                    TextButton(onClick = { dialog = "current" }) { Text("Current status…") }
                    TextButton(onClick = { dialog = "reschedule" }) { Text("Reschedule…") }
                    TextButton(onClick = { dialog = "complete" }) { Text("Complete…", color = KarColors.Green) }
                    TextButton(onClick = { dialog = "delete" }) { Text("Delete", color = KarColors.Red) }
                    TextButton(onClick = { selected = emptySet() }) { Text("Clear selection", color = KarColors.Slate) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // --------------------------------------------------------------- table
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(KarColors.Card)
                .border(1.dp, KarColors.Border, RoundedCornerShape(10.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().background(KarColors.SkyWash).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = rows.isNotEmpty() && sel.size == rows.size,
                    onCheckedChange = { on -> selected = if (on) visibleIds else emptySet() },
                    colors = CheckboxDefaults.colors(checkedColor = KarColors.SkyDeep),
                )
                fun click(c: SortCol) { if (sort == c) asc = !asc else { sort = c; asc = true } }
                Header("Activity", 3.2f, sort == SortCol.TITLE, asc) { click(SortCol.TITLE) }
                Header("Owner", 1.3f, sort == SortCol.OWNER, asc) { click(SortCol.OWNER) }
                Header("Project", 1.1f, sort == SortCol.PROJECT, asc) { click(SortCol.PROJECT) }
                Header("Priority", 0.9f, sort == SortCol.PRIORITY, asc) { click(SortCol.PRIORITY) }
                Header("Target", 1.1f, sort == SortCol.TARGET, asc) { click(SortCol.TARGET) }
                Header("Status", 0.95f, sort == SortCol.STATUS, asc) { click(SortCol.STATUS) }
                Header("Current status", 2f, false, asc) {}
                Header("Est.", 0.6f, sort == SortCol.ESTIMATE, asc) { click(SortCol.ESTIMATE) }
            }
            HorizontalDivider(color = KarColors.Border)
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows, key = { _, t -> t.id }) { i, t ->
                    val checked = t.id in sel
                    val overdue = !t.done && t.dueDate != null && t.dueDate < today
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (checked) KarColors.SkyLight else if (i % 2 == 1) KarColors.Tint else Color.Transparent)
                            .clickable { onOpen(t) }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on -> selected = if (on) selected + t.id else selected - t.id },
                            colors = CheckboxDefaults.colors(checkedColor = KarColors.SkyDeep),
                        )
                        Column(Modifier.weight(3.2f).padding(end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                            index.ancestors(t.id).lastOrNull()?.let {
                                Text(it.text, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                t.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (t.done) KarColors.Slate else KarColors.Ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val tg = tagsOf(t.id)
                            if (tg.isNotEmpty()) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                tg.take(4).forEach { TagChip(it, tagColor(it)) }
                            }
                        }
                        Cell(t.assignee ?: "–", 1.3f)
                        Cell(t.projectId?.let { projectName[it] } ?: "–", 1.1f)
                        Box(Modifier.weight(0.9f)) {
                            val pc = priorityColor(t.priority)
                            Text(
                                when (t.priority) { Priority.VERY_HIGH -> "Very High"; Priority.HIGH -> "High"; Priority.MEDIUM -> "Medium"; else -> "–" },
                                style = MaterialTheme.typography.labelSmall,
                                color = pc ?: KarColors.SlateLight,
                                fontWeight = if (pc != null) FontWeight.Bold else FontWeight.Normal,
                                modifier = if (pc != null) Modifier.background(pc.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier,
                            )
                        }
                        Box(Modifier.weight(1.1f)) {
                            val c = when { t.done -> KarColors.Slate; overdue -> KarColors.Red; t.dueDate == today -> KarColors.Amber; else -> KarColors.Ink }
                            Text(
                                if (t.dueDate == null) "–" else Ist.friendly(t.dueDate) + (t.dueTime?.let { " " + it } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = c,
                                fontWeight = if (overdue || t.dueDate == today) FontWeight.Bold else FontWeight.Normal,
                                modifier = if (overdue || t.dueDate == today) Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp) else Modifier,
                            )
                        }
                        Box(Modifier.weight(0.95f)) {
                            val sc = statusColor(t.effectiveStatus)
                            Text(TaskStatus.label(t.effectiveStatus), style = MaterialTheme.typography.labelSmall, color = sc, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.background(sc.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Cell((if (t.done) t.completionRemarks else t.statusNote) ?: "–", 2f, lines = 2)
                        Cell(t.estimatedMinutes?.let { formatMinutes(it) } ?: "–", 0.6f)
                    }
                    HorizontalDivider(color = KarColors.Border.copy(alpha = 0.6f))
                }
                if (rows.isEmpty()) item {
                    Text("No activities match", color = KarColors.SlateLight, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs
    when (dialog) {
        "reschedule" -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(Ist.today().plusDays(1)))
            var reason by remember { mutableStateOf("") }
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(enabled = reason.isNotBlank() && state.selectedDateMillis != null, onClick = {
                        state.selectedDateMillis?.let { bulk.reschedule(sel, Ist.pickerMillisToDate(it).toString(), reason.trim()) }
                        dialog = null; selected = emptySet()
                    }) { Text("Reschedule ${sel.size}") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            ) {
                Column {
                    DatePicker(state = state)
                    OutlinedTextField(
                        value = reason, onValueChange = { reason = it }, label = { Text("Reason (required)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
        "current", "complete" -> {
            var text by remember { mutableStateOf("") }
            val complete = dialog == "complete"
            AlertDialog(
                onDismissRequest = { dialog = null },
                containerColor = KarColors.Card,
                title = { Text(if (complete) "Complete ${sel.size} activities" else "Current status for ${sel.size} activities") },
                text = {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text(if (complete) "Completion remarks (required)" else "Current status / latest update") },
                        minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(enabled = text.isNotBlank(), onClick = {
                        if (complete) bulk.complete(sel, text.trim()) else bulk.currentStatus(sel, text.trim())
                        dialog = null; selected = emptySet()
                    }) { Text(if (complete) "Complete" else "Update") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = KarColors.Card,
            title = { Text("Delete ${sel.size} activities?") },
            text = { Text("They and their subtasks will be deleted. This cannot be undone.", color = KarColors.Slate) },
            confirmButton = { TextButton(onClick = { bulk.delete(sel); dialog = null; selected = emptySet() }) { Text("Delete", color = KarColors.Red) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RowScope.Header(label: String, weight: Float, active: Boolean, asc: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.weight(weight).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (active) KarColors.SkyDeep else KarColors.Slate)
        if (active) Icon(if (asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, tint = KarColors.SkyDeep, modifier = Modifier.size(13.dp).padding(start = 2.dp))
    }
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, lines: Int = 1) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (text == "–") KarColors.SlateLight else KarColors.Ink,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight).padding(end = 8.dp),
    )
}

@Composable
private fun MenuButton(label: String, options: List<Pair<String?, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

@Suppress("unused")
private fun tagColorPreview(c: String) = parseHexColor(c)
