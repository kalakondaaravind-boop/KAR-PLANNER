package com.kargroup.planner.ui

import com.kargroup.planner.data.newId
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kargroup.planner.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.kargroup.planner.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kargroup.planner.data.QUICK_TASK_MINUTES
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.effectiveStatus
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.History
import com.kargroup.planner.data.Frequency
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.FieldLabel
import com.kargroup.planner.ui.components.KarEmblem
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.SelectorField
import com.kargroup.planner.ui.components.SelectorOption
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.delay

/** What the Add/Edit dialog should open with. */
data class TaskDialogRequest(
    val existing: TaskEntity? = null,
    val parent: TaskEntity? = null,
    val defaultProjectId: String? = null,
    val defaultDueDate: String? = null,
    val defaultRecurring: Boolean = false,
    val defaultAssignee: String? = null,
)

/** A subtask row edited inside the activity pop-up. [id] is null for a new subtask. */
data class SubtaskDraft(
    val key: String = newId(),
    val id: String? = null,
    val text: String = "",
    val assignee: String = "",
    val done: Boolean = false,
)

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    request: TaskDialogRequest,
    projects: List<ProjectEntity>,
    people: List<PersonEntity>,
    onAddPerson: (PersonEntity) -> Unit,
    existingSubtasks: List<TaskEntity> = emptyList(),
    history: List<com.kargroup.planner.data.TaskHistoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (task: TaskEntity, completed: Boolean, subtasks: List<SubtaskDraft>, removedSubtaskIds: List<String>, rescheduleReason: String?) -> Unit,
) {
    val base = request.existing
    val parent = request.parent
    var title by remember { mutableStateOf(base?.text ?: "") }
    var notes by remember { mutableStateOf(base?.notes ?: "") }
    var projectId by remember { mutableStateOf(base?.projectId ?: request.defaultProjectId ?: parent?.projectId) }
    var priority by remember { mutableStateOf(base?.priority) }
    var dueDate by remember { mutableStateOf(if (base != null) base.dueDate else request.defaultDueDate) }
    var dueTime by remember { mutableStateOf(base?.dueTime) }
    var status by remember { mutableStateOf(base?.effectiveStatus ?: TaskStatus.NOT_STARTED) }
    var assignee by remember { mutableStateOf(base?.assignee ?: request.defaultAssignee ?: parent?.assignee ?: "") }
    val subtasks = remember {
        mutableStateListOf<SubtaskDraft>().apply {
            addAll(existingSubtasks.map { SubtaskDraft(id = it.id, text = it.text, assignee = it.assignee ?: "", done = it.done) })
        }
    }
    val removedSubtaskIds = remember { mutableStateListOf<String>() }
    // -1 = main "Assigned to"; >= 0 = that subtask row; null = no "Add person" form open.
    var addPersonFor by remember { mutableStateOf<Int?>(null) }
    var estimate by remember { mutableStateOf(base?.estimatedMinutes?.toString() ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }
    var recurring by remember { mutableStateOf(base?.isRecurring ?: request.defaultRecurring) }
    var frequency by remember { mutableStateOf(base?.recurrenceFrequency ?: Frequency.DAILY) }
    var interval by remember { mutableIntStateOf(base?.recurrenceInterval ?: 1) }
    var days by remember { mutableStateOf(base?.recurrenceDaysOfWeek?.toSet() ?: emptySet()) }
    var showPicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var rescheduleReason by remember { mutableStateOf("") }
    var reasonError by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focus.requestFocus() }
    }

    val today = Ist.todayStr()
    val tomorrow = Ist.today().plusDays(1).toString()
    // Moving the target of an overdue activity needs a reason (kept in its history).
    val wasOverdue = base != null && !base.done && base.dueDate != null && base.dueDate < today
    val needsReason = wasOverdue && status != TaskStatus.COMPLETED && (dueDate != base?.dueDate || dueTime != base?.dueTime)

    fun submit() {
        if (title.isBlank()) {
            titleError = true
            return
        }
        if (needsReason && rescheduleReason.isBlank()) {
            reasonError = true
            return
        }
        val weeklyKind = frequency == Frequency.WEEKLY || frequency == Frequency.BIWEEKLY
        val template = base ?: TaskEntity(text = "", parentId = parent?.id)
        onSave(
            template.copy(
                text = title.trim(),
                notes = notes.trim().ifBlank { null },
                projectId = projectId,
                priority = priority,
                dueDate = if (recurring && dueDate == null) today else dueDate,
                dueTime = if (dueDate == null && !recurring) null else dueTime,
                estimatedMinutes = estimate.toIntOrNull()?.takeIf { it > 0 },
                status = if (status == TaskStatus.COMPLETED) (template.status ?: TaskStatus.INITIATED) else status,
                assignee = assignee.trim().ifBlank { null },
                isRecurring = recurring,
                recurrenceFrequency = if (recurring) frequency else null,
                recurrenceInterval = if (recurring) interval.coerceAtLeast(1) else 1,
                recurrenceDaysOfWeek = if (recurring && weeklyKind && days.isNotEmpty()) days.sorted() else null,
            ),
            status == TaskStatus.COMPLETED,
            subtasks.filter { it.text.isNotBlank() }.map { it.copy(text = it.text.trim(), assignee = it.assignee.trim()) },
            removedSubtaskIds.toList() + subtasks.filter { it.text.isBlank() && it.id != null }.mapNotNull { it.id },
            if (needsReason) rescheduleReason.trim() else null,
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(KarBrush.SkyHeader)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KarEmblem(30.dp, stemColor = Color.White)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            when {
                                base != null -> "Edit Activity"
                                parent != null -> "Add Subtask"
                                else -> "New Activity"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            if (parent != null) "Under: ${parent.text}" else "KAR Group Planner · IST",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(KarBrush.Gold))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; if (it.isNotBlank()) titleError = false },
                    label = { Text("Activity title *") },
                    isError = titleError,
                    supportingText = { if (titleError) Text("Title is required") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / checklist / links") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                val projectOptions = listOf(SelectorOption(null, "No project", null)) +
                    projects.map { SelectorOption(it.id, it.name, parseHexColor(it.color)) }

                Spacer(Modifier.height(12.dp))
                SelectorField("Project", projectOptions, projectId, { projectId = it }, Modifier.fillMaxWidth())

                FieldLabel("Status")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PillChip("⚪ Not Started", status == TaskStatus.NOT_STARTED, KarColors.Slate) { status = TaskStatus.NOT_STARTED }
                    PillChip("🔵 Initiated", status == TaskStatus.INITIATED, KarColors.SkyDeep) { status = TaskStatus.INITIATED }
                    PillChip("🟢 Completed", status == TaskStatus.COMPLETED, KarColors.Green) { status = TaskStatus.COMPLETED }
                }

                FieldLabel("Assigned to")
                PersonPicker(
                    value = assignee,
                    onValueChange = { assignee = it },
                    people = people,
                    label = "Person",
                    onAddNew = { addPersonFor = -1 },
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldLabel("Priority")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PillChip("Standard", priority == null) { priority = null }
                    PillChip("🟡 Medium", priority == Priority.MEDIUM, KarColors.Amber) { priority = Priority.MEDIUM }
                    PillChip("🔴 High", priority == Priority.HIGH, KarColors.Red) { priority = Priority.HIGH }
                    PillChip("‼️ Very High", priority == Priority.VERY_HIGH, KarColors.Crimson) { priority = Priority.VERY_HIGH }
                }

                FieldLabel("Estimated activity time")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(5, 10, 15, 30, 45, 60, 90, 120).forEach { m ->
                        PillChip(formatMinutes(m), estimate == m.toString()) { estimate = m.toString() }
                    }
                    PillChip("None", estimate.isBlank()) { estimate = "" }
                }
                OutlinedTextField(
                    value = estimate,
                    onValueChange = { v -> estimate = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Or type minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        val m = estimate.toIntOrNull()
                        if (m != null && m in 1 until QUICK_TASK_MINUTES) Text("⚡ Quick win: scheduled first")
                    },
                    modifier = Modifier.padding(top = 6.dp).widthIn(max = 260.dp),
                )

                FieldLabel("Target date")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PillChip("Today", dueDate == today) { dueDate = today }
                    PillChip("Tomorrow", dueDate == tomorrow) { dueDate = tomorrow }
                    PillChip("None", dueDate == null) { dueDate = null }
                    val custom = dueDate != null && dueDate != today && dueDate != tomorrow
                    FilterChip(
                        selected = custom,
                        onClick = { showPicker = true },
                        label = { Text(if (custom) Ist.formatFull(dueDate) else "Pick date…") },
                        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp)) },
                        colors = chipColors(KarColors.Navy),
                    )
                }

                FieldLabel("Target time (IST)")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = dueTime != null,
                        onClick = { showTimePicker = true },
                        label = { Text(dueTime?.let { formatTime12(it) } ?: "Set time…") },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp)) },
                        colors = chipColors(KarColors.Navy),
                    )
                    PillChip("No time", dueTime == null) { dueTime = null }
                }
                if (needsReason) {
                    Surface(
                        color = KarColors.Amber.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "This activity was overdue (${dueLabel(base!!)}). Why is the target moving?",
                                style = MaterialTheme.typography.bodySmall,
                                color = KarColors.Ink,
                            )
                            FlowRow(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                RescheduleReasons.forEach { r ->
                                    PillChip(r, rescheduleReason.equals(r, true), KarColors.Amber) { rescheduleReason = r; reasonError = false }
                                }
                            }
                            OutlinedTextField(
                                value = rescheduleReason,
                                onValueChange = { rescheduleReason = it; if (it.isNotBlank()) reasonError = false },
                                label = { Text("Reason for delay *") },
                                isError = reasonError,
                                supportingText = { if (reasonError) Text("A reason is required to move an overdue activity") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (dueTime != null && dueDate == null && !recurring) {
                    Text(
                        "Pick a target date too, otherwise the time is ignored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KarColors.Amber,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Surface(color = KarColors.Tint, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Recurring activity", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                                Text(
                                    "When checked off, the next one is created automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KarColors.Slate,
                                )
                            }
                            Switch(
                                checked = recurring,
                                onCheckedChange = {
                                    recurring = it
                                    if (it && dueDate == null) dueDate = today
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep, checkedThumbColor = Color.White),
                            )
                        }
                        AnimatedVisibility(visible = recurring) {
                            Column {
                                FieldLabel("Frequency")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Frequency.ALL.forEach { f ->
                                        PillChip(f.replaceFirstChar { it.uppercase() }, frequency == f) { frequency = f }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                    Text("Repeat every", style = MaterialTheme.typography.bodyMedium, color = KarColors.Ink)
                                    IconButton(onClick = { if (interval > 1) interval-- }) {
                                        Icon(Icons.Outlined.Remove, contentDescription = "Less")
                                    }
                                    Text("$interval", style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
                                    IconButton(onClick = { if (interval < 30) interval++ }) {
                                        Icon(Icons.Outlined.Add, contentDescription = "More")
                                    }
                                    Text(
                                        when (frequency) {
                                            Frequency.WEEKLY -> if (interval == 1) "week" else "weeks"
                                            Frequency.BIWEEKLY -> "× 2 weeks"
                                            Frequency.MONTHLY -> if (interval == 1) "month" else "months"
                                            else -> if (interval == 1) "day" else "days"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (frequency == Frequency.WEEKLY || frequency == Frequency.BIWEEKLY) {
                                    FieldLabel("On days")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        DayLetters.forEachIndexed { i, letter ->
                                            val dayNum = i + 1
                                            val sel = dayNum in days
                                            Box(
                                                Modifier
                                                    .size(38.dp)
                                                    .background(if (sel) KarColors.SkyDeep else Color.White, CircleShape)
                                                    .border(1.dp, if (sel) KarColors.SkyDeep else KarColors.Border, CircleShape)
                                                    .clickable { days = if (sel) days - dayNum else days + dayNum },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    letter,
                                                    color = if (sel) Color.White else KarColors.Navy,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    Row(Modifier.padding(top = 6.dp)) {
                                        TextButton(onClick = { days = setOf(1, 2, 3, 4, 5) }) { Text("Weekdays") }
                                        TextButton(onClick = { days = emptySet() }) { Text("Clear") }
                                    }
                                }
                            }
                        }
                    }
                }

                // ------------------------------------------------------------ subtasks
                Spacer(Modifier.height(14.dp))
                Surface(color = KarColors.Tint, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, KarColors.Border)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AccountTree, null, tint = KarColors.SkyDeep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Subtasks" + if (subtasks.isNotEmpty()) " (${subtasks.size})" else "",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = KarColors.Navy,
                                )
                                Text(
                                    "Break the activity down and assign each step to a person.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KarColors.Slate,
                                )
                            }
                        }
                        subtasks.forEachIndexed { i, sub ->
                            Spacer(Modifier.height(10.dp))
                            SubtaskEditorRow(
                                index = i + 1,
                                draft = sub,
                                wide = wide,
                                people = people,
                                onAddPerson = { addPersonFor = i },
                                onChange = { subtasks[i] = it },
                                onRemove = {
                                    sub.id?.let { removedSubtaskIds.add(it) }
                                    subtasks.removeAt(i)
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { subtasks.add(SubtaskDraft(assignee = "")) }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add subtask")
                        }
                        if (base != null && subtasks.isNotEmpty()) {
                            Text(
                                "Due dates, priority and deeper sub-steps for a subtask can be set by opening it from the list.",
                                style = MaterialTheme.typography.labelSmall,
                                color = KarColors.SlateLight,
                            )
                        }
                    }
                }

                if (base != null && history.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, KarColors.Border)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.History, null, tint = KarColors.SkyDeep, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "History (${history.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = KarColors.Navy,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Hide" else "Show") }
                            }
                            if (showHistory) HistoryTimeline(history)
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = KarColors.Slate) }
                    Spacer(Modifier.width(8.dp))
                    GoldButton(
                        text = if (base != null) "Save Changes" else "Create Activity",
                        onClick = { submit() },
                        enabled = title.isNotBlank(),
                    )
                }
                }
            }
        }
    }

    addPersonFor?.let { target ->
        PersonDialog(
            existing = null,
            takenNames = people.map { it.name },
            onDismiss = { addPersonFor = null },
            onSave = { p ->
                onAddPerson(p)
                if (target < 0) {
                    assignee = p.name
                } else if (target < subtasks.size) {
                    subtasks[target] = subtasks[target].copy(assignee = p.name)
                }
                addPersonFor = null
            },
        )
    }

    if (showTimePicker) {
        val parsed = dueTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: java.time.LocalTime.of(10, 0)
        val tState = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Color.White,
            title = { Text("Target time (IST)") },
            text = { TimePicker(state = tState) },
            confirmButton = {
                TextButton(onClick = {
                    dueTime = "%02d:%02d".format(tState.hour, tState.minute)
                    if (dueDate == null) dueDate = today
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }

    if (showPicker) {
        val initial = Ist.parse(dueDate) ?: Ist.today()
        val state = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(initial))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dueDate = Ist.pickerMillisToDate(it).toString() }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun chipColors(accent: Color) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = accent.copy(alpha = 0.14f),
    selectedLabelColor = KarColors.Navy,
    selectedLeadingIconColor = accent,
)

@Composable
fun PillChip(label: String, selected: Boolean, accent: Color = KarColors.SkyDeep, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = chipColors(accent),
    )
}

@Composable
private fun SubtaskEditorRow(
    index: Int,
    draft: SubtaskDraft,
    wide: Boolean,
    people: List<PersonEntity>,
    onAddPerson: () -> Unit,
    onChange: (SubtaskDraft) -> Unit,
    onRemove: () -> Unit,
) {
    val titleField: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            value = draft.text,
            onValueChange = { onChange(draft.copy(text = it)) },
            label = { Text("Subtask $index") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (draft.done) TextDecoration.LineThrough else null,
            ),
            modifier = m,
        )
    }
    val personField: @Composable (Modifier) -> Unit = { m ->
        PersonPicker(
            value = draft.assignee,
            onValueChange = { onChange(draft.copy(assignee = it)) },
            people = people,
            onAddNew = onAddPerson,
            modifier = m,
        )
    }
    Surface(color = Color.White, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, KarColors.Border)) {
        if (wide) {
            Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                titleField(Modifier.weight(1.5f))
                Spacer(Modifier.width(8.dp))
                personField(Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove subtask", tint = KarColors.Slate) }
            }
        } else {
            Column(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    titleField(Modifier.weight(1f))
                    IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove subtask", tint = KarColors.Slate) }
                }
                personField(Modifier.fillMaxWidth().padding(end = 10.dp, top = 4.dp))
            }
        }
    }
}
