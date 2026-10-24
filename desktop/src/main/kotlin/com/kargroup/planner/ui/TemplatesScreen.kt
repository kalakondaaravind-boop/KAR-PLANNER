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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.Priority
import com.kargroup.planner.desktop.ActivityTemplate
import com.kargroup.planner.desktop.MomTemplate
import com.kargroup.planner.desktop.Tag
import com.kargroup.planner.desktop.TagStore
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor

/** Templates for activities and meetings, plus the tag list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplatesScreen(
    activities: List<ActivityTemplate>,
    meetings: List<MomTemplate>,
    tags: List<Tag>,
    tagUsage: (String) -> Int,
    persons: List<PersonEntity>,
    onUseActivity: (ActivityTemplate) -> Unit,
    onUseMeeting: (MomTemplate) -> Unit,
    onSaveActivity: (ActivityTemplate) -> Unit,
    onDeleteActivity: (ActivityTemplate) -> Unit,
    onSaveMeeting: (MomTemplate) -> Unit,
    onDeleteMeeting: (MomTemplate) -> Unit,
    onSaveTag: (Tag, String?) -> Unit,
    onDeleteTag: (Tag) -> Unit,
) {
    var editActivity by remember { mutableStateOf<ActivityTemplate?>(null) }
    var editMeeting by remember { mutableStateOf<MomTemplate?>(null) }
    var editTag by remember { mutableStateOf<Pair<Tag, String?>?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Templates & tags", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
        Text("Start recurring work in one click. Templates are also in the command bar (Ctrl+K).",
            style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)

        Section("Activity templates", Icons.Outlined.ContentCopy, "New activity template") {
            editActivity = ActivityTemplate(name = "", title = "")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            activities.sortedBy { it.name }.forEach { t ->
                TemplateCard(
                    title = t.name,
                    lines = listOfNotNull(
                        t.title.takeIf { it != t.name },
                        if (t.subtasks.isNotEmpty()) "${t.subtasks.size} subtasks" else null,
                        t.estimatedMinutes?.let { "Estimate ${formatMinutes(it)}" },
                        t.dueInDays?.let { "Due in $it day(s)" },
                        t.assignee.ifBlank { null }?.let { "Owner $it" },
                    ),
                    onUse = { onUseActivity(t) },
                    onEdit = { editActivity = t },
                    onDelete = { onDeleteActivity(t) },
                )
            }
            if (activities.isEmpty()) Text("No templates yet", color = KarColors.SlateLight)
        }

        Section("Meeting templates", Icons.Outlined.Groups, "New meeting template") {
            editMeeting = MomTemplate(name = "", title = "")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            meetings.sortedBy { it.name }.forEach { t ->
                TemplateCard(
                    title = t.name,
                    lines = listOfNotNull(
                        "${formatTime12(t.meetingTime)} · ${formatMinutes(t.durationMinutes)}",
                        t.attendees.takeIf { it.isNotEmpty() }?.let { "${it.size} attendees" },
                        t.agenda.lines().firstOrNull { it.isNotBlank() },
                    ),
                    onUse = { onUseMeeting(t) },
                    onEdit = { editMeeting = t },
                    onDelete = { onDeleteMeeting(t) },
                )
            }
            if (meetings.isEmpty()) Text("No templates yet", color = KarColors.SlateLight)
        }

        Section("Tags", Icons.Outlined.Label, "New tag") { editTag = Tag("", TagStore.COLORS.first()) to null }
        Text("Put tags on activities from the Table view (tick rows → Add tag), then filter the Board and Table by tag.",
            style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.sortedBy { it.name }.forEach { t ->
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(KarColors.Card)
                        .border(1.dp, KarColors.Border, RoundedCornerShape(8.dp))
                        .clickable { editTag = t to t.name }
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(t.name, t.color)
                    Text("  ${tagUsage(t.name)}", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                    IconButton(onClick = { onDeleteTag(t) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Delete, "Delete tag", tint = KarColors.SlateLight, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }

    editActivity?.let { t -> ActivityTemplateDialog(t, persons, onDismiss = { editActivity = null }) { onSaveActivity(it); editActivity = null } }
    editMeeting?.let { t -> MeetingTemplateDialog(t, onDismiss = { editMeeting = null }) { onSaveMeeting(it); editMeeting = null } }
    editTag?.let { (t, old) ->
        var name by remember(t) { mutableStateOf(t.name) }
        var color by remember(t) { mutableStateOf(t.color) }
        AlertDialog(
            onDismissRequest = { editTag = null },
            containerColor = KarColors.Card,
            title = { Text(if (old == null) "New tag" else "Edit tag") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Tag name") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagStore.COLORS.forEach { c ->
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(parseHexColor(c))
                                    .border(if (c == color) 3.dp else 0.dp, KarColors.Navy, CircleShape)
                                    .clickable { color = c }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (name.isNotBlank()) TagChip(name.uppercase(), color)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSaveTag(Tag(name, color), old); editTag = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editTag = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon, null, tint = KarColors.SkyDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onAction) { Text("+ $action") }
    }
}

@Composable
private fun TemplateCard(title: String, lines: List<String>, onUse: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    DepthCard(Modifier.width(280.dp), elevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            lines.take(4).forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUse) { Text("Use", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit", tint = KarColors.Slate, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = KarColors.SlateLight, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTemplateDialog(t: ActivityTemplate, persons: List<PersonEntity>, onDismiss: () -> Unit, onSave: (ActivityTemplate) -> Unit) {
    var name by remember { mutableStateOf(t.name) }
    var title by remember { mutableStateOf(t.title) }
    var notes by remember { mutableStateOf(t.notes) }
    var priority by remember { mutableStateOf(t.priority) }
    var estimate by remember { mutableStateOf(t.estimatedMinutes?.toString() ?: "") }
    var due by remember { mutableStateOf(t.dueInDays?.toString() ?: "") }
    var owner by remember { mutableStateOf(t.assignee) }
    var subs by remember { mutableStateOf(t.subtasks.joinToString("\n") { it.first }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text(if (t.name.isBlank()) "New activity template" else "Edit template") },
        text = {
            Column(Modifier.widthIn(min = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Activity title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("Standard", priority == null) { priority = null }
                    PillChip("Medium", priority == Priority.MEDIUM, KarColors.Amber) { priority = Priority.MEDIUM }
                    PillChip("High", priority == Priority.HIGH, KarColors.Red) { priority = Priority.HIGH }
                    PillChip("Very High", priority == Priority.VERY_HIGH, KarColors.Crimson) { priority = Priority.VERY_HIGH }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = estimate, onValueChange = { v -> estimate = v.filter { it.isDigit() }.take(4) }, label = { Text("Estimate (min)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = due, onValueChange = { v -> due = v.filter { it.isDigit() }.take(3) }, label = { Text("Due in (days)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                PersonPicker(value = owner, onValueChange = { owner = it }, people = persons, label = "Default owner", modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subs, onValueChange = { subs = it }, label = { Text("Subtasks (one per line)") }, minLines = 3, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && title.isNotBlank(), onClick = {
                onSave(
                    t.copy(
                        name = name.trim(), title = title.trim(), notes = notes.trim(), priority = priority,
                        estimatedMinutes = estimate.toIntOrNull()?.takeIf { it > 0 }, dueInDays = due.toIntOrNull(),
                        assignee = owner.trim(),
                        subtasks = subs.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { it to "" },
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MeetingTemplateDialog(t: MomTemplate, onDismiss: () -> Unit, onSave: (MomTemplate) -> Unit) {
    var name by remember { mutableStateOf(t.name) }
    var title by remember { mutableStateOf(t.title) }
    var time by remember { mutableStateOf(t.meetingTime) }
    var duration by remember { mutableStateOf(t.durationMinutes.toString()) }
    var attendees by remember { mutableStateOf(t.attendees.joinToString(", ")) }
    var agenda by remember { mutableStateOf(t.agenda) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        title = { Text(if (t.name.isBlank()) "New meeting template" else "Edit template") },
        text = {
            Column(Modifier.widthIn(min = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Meeting title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = time, onValueChange = { time = it.take(5) }, label = { Text("Time (HH:MM)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = duration, onValueChange = { v -> duration = v.filter { it.isDigit() }.take(3) }, label = { Text("Duration (min)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = attendees, onValueChange = { attendees = it }, label = { Text("Attendees (comma separated)") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = agenda, onValueChange = { agenda = it }, label = { Text("Agenda") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val validTime = Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(time.trim())
            TextButton(enabled = name.isNotBlank() && title.isNotBlank() && validTime, onClick = {
                val hhmm = time.trim().let { if (it.length == 4) "0$it" else it }
                onSave(
                    t.copy(
                        name = name.trim(), title = title.trim(), meetingTime = hhmm,
                        durationMinutes = duration.toIntOrNull()?.coerceIn(5, 600) ?: 30,
                        attendees = attendees.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        agenda = agenda.trim(),
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
