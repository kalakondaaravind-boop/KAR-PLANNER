package com.kargroup.planner.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.HistoryKind
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskHistoryEntity
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

/** Common reasons offered as one-tap chips (the user can still type their own). */
val RescheduleReasons = listOf(
    "Waiting for inputs", "Client delay", "Higher priority work", "Dependency pending", "Out of office", "Scope changed",
)

/**
 * New target for an overdue activity. A reason is compulsory and is kept in the activity's history.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RescheduleDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (newDate: String, newTime: String?, reason: String) -> Unit,
) {
    val tomorrow = Ist.today().plusDays(1)
    var date by remember { mutableStateOf(tomorrow.toString()) }
    var time by remember { mutableStateOf(task.dueTime) }
    var reason by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Outlined.EventRepeat, null, tint = KarColors.SkyDeep) },
        title = { Text("Reschedule activity") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(task.text, fontWeight = FontWeight.SemiBold, color = KarColors.Ink, maxLines = 2)
                Text(
                    "Was due ${dueLabel(task)}",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Red,
                )
                Spacer(Modifier.height(12.dp))
                Text("New target date", style = MaterialTheme.typography.labelMedium, color = KarColors.Navy)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val today = Ist.today()
                    listOf(
                        "Today" to today, "Tomorrow" to tomorrow,
                        "+3 days" to today.plusDays(3), "Next week" to today.plusDays(7),
                    ).forEach { (label, d) ->
                        SmallChip(label, date == d.toString()) { date = d.toString() }
                    }
                    SmallChip(if (date in listOf(today, tomorrow, today.plusDays(3), today.plusDays(7)).map { it.toString() }) "Pick…" else Ist.formatFull(date), false) { pickDate = true }
                }
                Spacer(Modifier.height(10.dp))
                Text("New target time", style = MaterialTheme.typography.labelMedium, color = KarColors.Navy)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallChip(time?.let { formatTime12(it) } ?: "Set time…", time != null) { pickTime = true }
                    SmallChip("No time", time == null) { time = null }
                }
                Spacer(Modifier.height(12.dp))
                Text("Reason for delay *", style = MaterialTheme.typography.labelMedium, color = KarColors.Navy)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RescheduleReasons.forEach { r -> SmallChip(r, reason.equals(r, true)) { reason = r } }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date, time, reason.trim()) }, enabled = reason.isNotBlank()) { Text("Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(Ist.parse(date) ?: tomorrow))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Ist.pickerMillisToDate(it).toString() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val parsed = time?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() } ?: java.time.LocalTime.of(10, 0)
        val state = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = Color.White,
            title = { Text("Target time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = { time = "%02d:%02d".format(state.hour, state.minute); pickTime = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) KarColors.SkyDeep else KarColors.SkyLight,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else KarColors.SkyDeep,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// =============================================================================
// History timeline (shown inside the activity pop-up)
// =============================================================================

private fun historyColor(kind: String): Color = when (kind) {
    HistoryKind.CREATED -> KarColors.SkyDeep
    HistoryKind.RESCHEDULED -> KarColors.Amber
    HistoryKind.COMPLETED -> KarColors.Green
    HistoryKind.REOPENED -> KarColors.Red
    HistoryKind.ASSIGNED -> KarColors.Navy2
    else -> KarColors.Slate
}

private fun historyTitle(kind: String): String = when (kind) {
    HistoryKind.CREATED -> "Created"
    HistoryKind.UPDATED -> "Updated"
    HistoryKind.ASSIGNED -> "Assigned"
    HistoryKind.RESCHEDULED -> "Rescheduled"
    HistoryKind.STATUS -> "Status"
    HistoryKind.COMPLETED -> "Completed"
    HistoryKind.REOPENED -> "Reopened"
    else -> kind
}

@Composable
fun HistoryTimeline(history: List<TaskHistoryEntity>) {
    Column {
        history.sortedByDescending { it.at }.take(40).forEachIndexed { i, h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 4.dp).size(10.dp).background(historyColor(h.kind), CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(historyTitle(h.kind), fontWeight = FontWeight.SemiBold, color = historyColor(h.kind), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(Ist.formatStamp(h.at), style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
                    }
                    h.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = KarColors.Ink)
                    }
                }
            }
            if (i == 39 && history.size > 40) Text("… older entries hidden", style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
        }
    }
}
