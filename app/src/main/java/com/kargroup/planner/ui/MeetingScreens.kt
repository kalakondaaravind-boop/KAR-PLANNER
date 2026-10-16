package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kargroup.planner.data.MeetingActionItem
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.ui.components.FieldLabel
import com.kargroup.planner.ui.components.KarEmblem
import com.kargroup.planner.ui.components.KarLogo
import com.kargroup.planner.ui.components.MetaPill
import com.kargroup.planner.ui.components.SelectorField
import com.kargroup.planner.ui.components.SelectorOption
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.IconTile
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val time12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

fun formatMeetingTime(hhmm: String): String =
    runCatching { LocalTime.parse(hhmm).format(time12) }.getOrDefault(hhmm)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingsScreen(
    meetings: List<MeetingMinuteEntity>,
    projects: Map<String, ProjectEntity>,
    wide: Boolean,
    header: @Composable () -> Unit,
    onOpen: (MeetingMinuteEntity) -> Unit,
    onAdd: () -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { header() }
        item {
            OrganizeHeader(
                "Minutes of Meeting",
                "${meetings.size} record(s) · action items sync to your planner as high priority",
                "New MoM",
                onAdd,
            )
        }
        if (meetings.isEmpty()) {
            item { EmptyCard(Icons.Outlined.Groups, "No meetings recorded", "Capture agenda, decisions and action items from your next meeting.") }
        }
        items(meetings, key = { it.id }) { m ->
            val project = m.projectId?.let { projects[it] }
            val doneItems = m.actionItems.count { it.completed }
            DepthCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(m) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = KarColors.SkyDeep, shape = RoundedCornerShape(12.dp), shadowElevation = 6.dp) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val d = Ist.parse(m.meetingDate)
                                Text(d?.dayOfMonth?.toString() ?: "--", color = Color.White, style = MaterialTheme.typography.titleLarge)
                                Text(
                                    d?.month?.name?.take(3) ?: "",
                                    color = KarColors.Gold,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium, color = KarColors.Navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Ist.friendly(m.meetingDate)} · ${formatMeetingTime(m.meetingTime)} · ${m.durationMinutes} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = KarColors.Slate,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (project != null) MetaPill(project.name, KarColors.Navy, dotColor = parseHexColor(project.color))
                        if (m.attendees.isNotEmpty()) MetaPill("${m.attendees.size} attendee(s)", KarColors.Navy2, Icons.Outlined.Person)
                        if (m.keyDecisions.isNotEmpty()) MetaPill("${m.keyDecisions.size} decision(s)", KarColors.Amber, Icons.Outlined.Gavel)
                        if (m.actionItems.isNotEmpty()) {
                            MetaPill(
                                "$doneItems/${m.actionItems.size} actions done",
                                if (doneItems == m.actionItems.size) KarColors.Green else KarColors.Red,
                                Icons.Outlined.CheckCircle,
                            )
                        }
                    }
                    if (m.agenda.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(m.agenda, style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeetingEditorDialog(
    existing: MeetingMinuteEntity?,
    projects: List<ProjectEntity>,
    people: List<com.kargroup.planner.data.PersonEntity>,
    defaultProjectId: String?,
    onDismiss: () -> Unit,
    onSave: (MeetingMinuteEntity) -> Unit,
    onDelete: (MeetingMinuteEntity) -> Unit,
) {
    val nowIst = Ist.now()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var projectId by remember { mutableStateOf(existing?.projectId ?: defaultProjectId) }
    var date by remember { mutableStateOf(existing?.meetingDate ?: Ist.todayStr()) }
    var time by remember { mutableStateOf(existing?.meetingTime ?: "%02d:%02d".format(nowIst.hour, if (nowIst.minute < 30) 0 else 30)) }
    var duration by remember { mutableStateOf((existing?.durationMinutes ?: 30).toString()) }
    val attendees = remember { mutableStateListOf<String>().apply { addAll(existing?.attendees.orEmpty()) } }
    var attendeeInput by remember { mutableStateOf("") }
    var agenda by remember { mutableStateOf(existing?.agenda ?: "") }
    var notes by remember { mutableStateOf(existing?.discussionNotes ?: "") }
    val decisions = remember { mutableStateListOf<String>().apply { addAll(existing?.keyDecisions.orEmpty()) } }
    val actions = remember { mutableStateListOf<MeetingActionItem>().apply { addAll(existing?.actionItems.orEmpty()) } }
    var datePickerFor by remember { mutableStateOf<String?>(null) } // "meeting" or action item id
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun addAttendee() {
        attendeeInput.split(",").map { it.trim() }.filter { it.isNotEmpty() && it !in attendees }.forEach { attendees.add(it) }
        attendeeInput = ""
    }

    fun buildMeeting(): MeetingMinuteEntity {
        if (attendeeInput.isNotBlank()) addAttendee()
        val base = existing ?: MeetingMinuteEntity(title = "", meetingDate = date, meetingTime = time)
        return base.copy(
            title = title.trim(),
            projectId = projectId,
            meetingDate = date,
            meetingTime = time,
            durationMinutes = duration.toIntOrNull()?.coerceIn(1, 24 * 60) ?: 30,
            attendees = attendees.toList(),
            agenda = agenda.trim(),
            discussionNotes = notes.trim(),
            keyDecisions = decisions.map { it.trim() }.filter { it.isNotEmpty() },
            actionItems = actions.toList(),
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(
            shape = RoundedCornerShape(if (wide) 20.dp else 0.dp),
            color = KarColors.SurfaceBg,
            modifier = (if (wide) Modifier.padding(24.dp).widthIn(max = 860.dp) else Modifier.fillMaxSize())
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column {
                // Header bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(KarBrush.SkyHeader)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close", tint = Color.White) }
                    KarLogo(height = 28.dp, onDark = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (existing == null) "New Minutes of Meeting" else "Minutes of Meeting",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color(0xFFFCA5A5)) }
                    }
                    GoldButton("Save", { onSave(buildMeeting()) }, enabled = title.isNotBlank(), height = 42.dp)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(KarBrush.Gold))

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Basics
                    EditorCard {
                        OutlinedTextField(title, { title = it }, label = { Text("Meeting title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        SelectorField(
                            "Project",
                            listOf(SelectorOption(null, "No project", null)) + projects.map { SelectorOption(it.id, it.name, parseHexColor(it.color)) },
                            projectId,
                            { projectId = it },
                            Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { datePickerFor = "meeting" },
                                label = { Text(Ist.formatFull(date)) },
                                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                            )
                            AssistChip(
                                onClick = { showTimePicker = true },
                                label = { Text(formatMeetingTime(time) + " IST") },
                                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            duration,
                            { v -> duration = v.filter { it.isDigit() }.take(4) },
                            label = { Text("Duration (minutes)") },
                            leadingIcon = { Icon(Icons.Outlined.Timer, null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }

                    // Attendees
                    EditorCard {
                        SectionTitle("Attendees", Icons.Outlined.Person)
                        if (attendees.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                attendees.toList().forEach { name ->
                                    InputChip(
                                        selected = false,
                                        onClick = { attendees.remove(name) },
                                        label = { Text(name) },
                                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove", Modifier.size(16.dp)) },
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                attendeeInput,
                                { attendeeInput = it },
                                label = { Text("Add attendee (comma separates)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { addAttendee() }) { Icon(Icons.Outlined.Add, "Add attendee", tint = KarColors.Navy) }
                        }
                    }

                    // Agenda & notes
                    EditorCard {
                        SectionTitle("Agenda", Icons.Outlined.Event)
                        OutlinedTextField(agenda, { agenda = it }, placeholder = { Text("One agenda item per line") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        FieldLabel("Discussion notes")
                        OutlinedTextField(notes, { notes = it }, placeholder = { Text("What was discussed…") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                    }

                    // Decisions
                    EditorCard {
                        SectionTitle("Key decisions", Icons.Outlined.Gavel)
                        decisions.forEachIndexed { i, d ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                                Text("${i + 1}.", color = KarColors.Slate, modifier = Modifier.width(26.dp))
                                OutlinedTextField(d, { decisions[i] = it }, singleLine = false, modifier = Modifier.weight(1f))
                                IconButton(onClick = { decisions.removeAt(i) }) { Icon(Icons.Outlined.Delete, "Remove", tint = KarColors.Slate) }
                            }
                        }
                        TextButton(onClick = { decisions.add("") }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add decision")
                        }
                    }

                    // Action items
                    EditorCard {
                        SectionTitle("Action items", Icons.Outlined.CheckCircle)
                        Text(
                            "Each action item appears in your planner as a 🔴 High priority activity and stays in sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KarColors.Slate,
                        )
                        Spacer(Modifier.height(8.dp))
                        actions.forEachIndexed { i, item ->
                            Surface(color = KarColors.Tint, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = item.completed,
                                            onCheckedChange = { actions[i] = item.copy(completed = it) },
                                            colors = CheckboxDefaults.colors(checkedColor = KarColors.Green, uncheckedColor = KarColors.Red),
                                        )
                                        OutlinedTextField(
                                            item.description,
                                            { actions[i] = item.copy(description = it) },
                                            label = { Text("Action") },
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = { actions.removeAt(i) }) { Icon(Icons.Outlined.Delete, "Remove", tint = KarColors.Slate) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp)) {
                                        PersonPicker(
                                            value = item.assignee,
                                            onValueChange = { actions[i] = item.copy(assignee = it) },
                                            people = people,
                                            label = "Assignee",
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        AssistChip(
                                            onClick = { datePickerFor = item.id },
                                            label = { Text(if (item.dueDate.isNullOrBlank()) "Due date" else Ist.friendly(item.dueDate)) },
                                            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp)) },
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { actions.add(MeetingActionItem()) }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add action item")
                        }
                    }

                    if (existing != null) {
                        Text(
                            "Recorded ${Ist.formatStamp(existing.createdAt)} IST",
                            style = MaterialTheme.typography.labelSmall,
                            color = KarColors.SlateLight,
                        )
                    }
                }
            }
        }
    }

    val pickerTarget = datePickerFor
    if (pickerTarget != null) {
        val currentValue = if (pickerTarget == "meeting") date else actions.firstOrNull { it.id == pickerTarget }?.dueDate
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Ist.dateToPickerMillis(Ist.parse(currentValue) ?: Ist.today())
        )
        DatePickerDialog(
            onDismissRequest = { datePickerFor = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val picked = Ist.pickerMillisToDate(ms).toString()
                        if (pickerTarget == "meeting") {
                            date = picked
                        } else {
                            val idx = actions.indexOfFirst { it.id == pickerTarget }
                            if (idx >= 0) actions[idx] = actions[idx].copy(dueDate = picked)
                        }
                    }
                    datePickerFor = null
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (pickerTarget != "meeting") {
                        TextButton(onClick = {
                            val idx = actions.indexOfFirst { it.id == pickerTarget }
                            if (idx >= 0) actions[idx] = actions[idx].copy(dueDate = null)
                            datePickerFor = null
                        }) { Text("No date") }
                    }
                    TextButton(onClick = { datePickerFor = null }) { Text("Cancel") }
                }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(10, 0))
        val tState = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color.White,
            title = { Text("Meeting time (IST)") },
            text = { TimePicker(state = tState) },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(tState.hour, tState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color.White,
            title = { Text("Delete this meeting?") },
            text = { Text("\"${existing.title}\" will be removed. Activities created from its action items stay in your planner.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete(existing) },
                    colors = ButtonDefaults.buttonColors(containerColor = KarColors.Red),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorCard(content: @Composable () -> Unit) {
    DepthCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(icon, null, tint = KarColors.SkyMid, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
    }
    HorizontalDivider(color = KarColors.Border, modifier = Modifier.padding(bottom = 10.dp))
}
