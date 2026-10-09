package com.kargroup.planner.ui

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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.kargroup.planner.data.ContextTagEntity
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
    val defaultContextId: String? = null,
    val defaultDueDate: String? = null,
    val defaultRecurring: Boolean = false,
)

private val DayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    request: TaskDialogRequest,
    projects: List<ProjectEntity>,
    contexts: List<ContextTagEntity>,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
) {
    val base = request.existing
    val parent = request.parent
    var title by remember { mutableStateOf(base?.text ?: "") }
    var notes by remember { mutableStateOf(base?.notes ?: "") }
    var projectId by remember { mutableStateOf(base?.projectId ?: request.defaultProjectId ?: parent?.projectId) }
    var contextId by remember { mutableStateOf(base?.contextId ?: request.defaultContextId ?: parent?.contextId) }
    var priority by remember { mutableStateOf(base?.priority) }
    var dueDate by remember { mutableStateOf(if (base != null) base.dueDate else request.defaultDueDate) }
    var recurring by remember { mutableStateOf(base?.isRecurring ?: request.defaultRecurring) }
    var frequency by remember { mutableStateOf(base?.recurrenceFrequency ?: Frequency.DAILY) }
    var interval by remember { mutableIntStateOf(base?.recurrenceInterval ?: 1) }
    var days by remember { mutableStateOf(base?.recurrenceDaysOfWeek?.toSet() ?: emptySet()) }
    var showPicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { focus.requestFocus() }
    }

    val today = Ist.todayStr()
    val tomorrow = Ist.today().plusDays(1).toString()

    fun submit() {
        if (title.isBlank()) {
            titleError = true
            return
        }
        val weeklyKind = frequency == Frequency.WEEKLY || frequency == Frequency.BIWEEKLY
        val template = base ?: TaskEntity(text = "", parentId = parent?.id)
        onSave(
            template.copy(
                text = title.trim(),
                notes = notes.trim().ifBlank { null },
                projectId = projectId,
                contextId = contextId,
                priority = priority,
                dueDate = if (recurring && dueDate == null) today else dueDate,
                isRecurring = recurring,
                recurrenceFrequency = if (recurring) frequency else null,
                recurrenceInterval = if (recurring) interval.coerceAtLeast(1) else 1,
                recurrenceDaysOfWeek = if (recurring && weeklyKind && days.isNotEmpty()) days.sorted() else null,
            )
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
                val contextOptions = listOf(SelectorOption(null, "No context", null)) +
                    contexts.map { SelectorOption(it.id, it.name, parseHexColor(it.color)) }

                Spacer(Modifier.height(12.dp))
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectorField("Project", projectOptions, projectId, { projectId = it }, Modifier.weight(1f))
                        SelectorField("GTD Context", contextOptions, contextId, { contextId = it }, Modifier.weight(1f))
                    }
                } else {
                    SelectorField("Project", projectOptions, projectId, { projectId = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    SelectorField("GTD Context", contextOptions, contextId, { contextId = it }, Modifier.fillMaxWidth())
                }

                FieldLabel("Priority")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip("Standard", priority == null) { priority = null }
                    PillChip("🟡 Medium", priority == Priority.MEDIUM, KarColors.Amber) { priority = Priority.MEDIUM }
                    PillChip("🔴 High", priority == Priority.HIGH, KarColors.Red) { priority = Priority.HIGH }
                }

                FieldLabel("Due date")
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
