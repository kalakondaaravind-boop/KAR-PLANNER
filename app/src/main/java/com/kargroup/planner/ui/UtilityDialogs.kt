package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.DataCounts
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

private fun alertTag(t: TaskEntity, today: String): Pair<String, Color> {
    val due = t.dueDate ?: today
    return when {
        due < today -> "Overdue · ${Ist.friendly(due)}" to KarColors.Red
        due == today -> "Due today" to KarColors.Amber
        else -> "Due ${Ist.friendly(due)}" to KarColors.Navy2
    }
}

/** Floating in-app banner shown above the content when reminders are pending. */
@Composable
fun ReminderBanner(
    alerts: List<TaskEntity>,
    today: String,
    onReview: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return
    val first = alerts.first()
    val (tag, tagColor) = alertTag(first, today)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = KarColors.SkyDeep,
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = KarColors.Gold, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = KarColors.Navy, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (alerts.size == 1) "1 activity needs attention" else "${alerts.size} activities need attention",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        first.text,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tag, color = if (tagColor == KarColors.Red) Color(0xFFFCA5A5) else KarColors.Gold, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onReview) { Text("Review", color = KarColors.Gold) }
            TextButton(onClick = onSnooze) { Text("Snooze 1h", color = Color.White) }
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun RemindersDialog(
    alerts: List<TaskEntity>,
    today: String,
    onSnooze: (List<String>) -> Unit,
    onDismissReminders: (List<String>) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color.White,
        icon = { Icon(Icons.Outlined.NotificationsActive, null, tint = KarColors.Gold) },
        title = { Text("Reminders", color = KarColors.Navy) },
        text = {
            if (alerts.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.TaskAlt, null, tint = KarColors.Green, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("You're all caught up. No due, overdue or upcoming high-priority activities.", color = KarColors.Slate)
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alerts, key = { it.id }) { t ->
                        val (tag, color) = alertTag(t, today)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = KarColors.Tint,
                            border = BorderStroke(1.dp, KarColors.Border),
                            onClick = { onOpenTask(t) },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (t.priority == Priority.VERY_HIGH) {
                                        Text("‼️ ", style = MaterialTheme.typography.bodySmall)
                                    } else if (t.priority == Priority.HIGH) {
                                        Text("🔴 ", style = MaterialTheme.typography.bodySmall)
                                    } else if (t.priority == Priority.MEDIUM) {
                                        Text("🟡 ", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(t.text, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(tag, color = color, style = MaterialTheme.typography.labelSmall)
                                Row {
                                    TextButton(onClick = { onSnooze(listOf(t.id)) }) {
                                        Icon(Icons.Outlined.Snooze, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Snooze 1 Hour")
                                    }
                                    TextButton(onClick = { onDismissReminders(listOf(t.id)) }) { Text("Dismiss", color = KarColors.Slate) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (alerts.isNotEmpty()) {
                Button(
                    onClick = { onSnooze(alerts.map { it.id }); onClose() },
                    colors = ButtonDefaults.buttonColors(containerColor = KarColors.SkyDeep, contentColor = androidx.compose.ui.graphics.Color.White),
                ) { Text("Snooze All 1 Hour", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = {
            if (alerts.isNotEmpty()) {
                Row {
                    TextButton(onClick = { onDismissReminders(alerts.map { it.id }); onClose() }) { Text("Dismiss All", color = KarColors.Slate) }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        },
    )
}

@Composable
fun DeleteAllDialog(
    loadCounts: suspend () -> DataCounts,
    onBackupFirst: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var counts by remember { mutableStateOf<DataCounts?>(null) }
    var confirmText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { counts = loadCounts() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { Icon(Icons.Outlined.WarningAmber, null, tint = KarColors.Red, modifier = Modifier.size(36.dp)) },
        title = { Text("Danger Zone: Delete All Data", color = KarColors.Red, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "This permanently wipes everything stored on this device. It cannot be undone.",
                    color = KarColors.Ink,
                )
                Spacer(Modifier.size(12.dp))
                Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFFECACA))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        val c = counts
                        CountRow("Activities / tasks", c?.tasks)
                        CountRow("Projects", c?.projects)
                        CountRow("Minutes of Meeting", c?.meetings)
                        CountRow("Journal entries", c?.journal)
                        CountRow("People", c?.persons)
                    }
                }
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = onBackupFirst, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Download Backup First")
                }
                Spacer(Modifier.size(12.dp))
                Text("Type DELETE to confirm:", style = MaterialTheme.typography.labelLarge, color = KarColors.Ink)
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    singleLine = true,
                    placeholder = { Text("DELETE") },
                    isError = confirmText.isNotEmpty() && confirmText != "DELETE",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmText == "DELETE",
                colors = ButtonDefaults.buttonColors(containerColor = KarColors.Red, contentColor = Color.White),
            ) { Text("Yes, Delete Everything", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CountRow(label: String, value: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = KarColors.Ink)
        Text(value?.toString() ?: "…", fontWeight = FontWeight.Bold, color = KarColors.Red)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, color = KarColors.Navy) },
        text = { Text(message, color = KarColors.Ink) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) KarColors.Red else KarColors.Navy,
                    contentColor = Color.White,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Suppress("unused")
private fun Modifier.softCard(): Modifier = this.background(Color.White, RoundedCornerShape(16.dp))
