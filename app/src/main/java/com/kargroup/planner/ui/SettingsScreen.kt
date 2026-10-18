package com.kargroup.planner.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.LockMode
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.minutesToHHmm
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

/** Which time field the picker is editing. */
private enum class TimeField { WORK_START, WORK_END, BREAK_START, BREAK_END }

fun canUseExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return am.canScheduleExactAlarms()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PlannerSettings,
    wide: Boolean,
    onUpdate: ((PlannerSettings) -> PlannerSettings) -> Unit,
    onPickBackupFolder: () -> Unit,
    onBackupNow: () -> Unit,
    onSetPin: (String) -> Unit,
) {
    val context = LocalContext.current
    var editTime by remember { mutableStateOf<TimeField?>(null) }
    var addHoliday by remember { mutableStateOf(false) }
    var setPin by remember { mutableStateOf(false) }
    var pendingLock by remember { mutableStateOf<String?>(null) }
    var exactOk by remember { mutableStateOf(canUseExactAlarms(context)) }
    val hPad = if (wide) 24.dp else 12.dp

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ------------------------------------------------------------ working day
        item {
            Section("Working day", Icons.Outlined.Schedule) {
                Text(
                    "The Scheduler books activities only inside these hours, skipping the break and meetings.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox("Day starts", settings.workStart) { editTime = TimeField.WORK_START }
                    TimeBox("Day ends", settings.workEnd) { editTime = TimeField.WORK_END }
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow("Lunch / break", "Keep this time free every working day", settings.breakEnabled) { on ->
                    onUpdate { it.copy(breakEnabled = on) }
                }
                if (settings.breakEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeBox("Break from", settings.breakStart) { editTime = TimeField.BREAK_START }
                        TimeBox("Break to", settings.breakEnd) { editTime = TimeField.BREAK_END }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Working days", style = MaterialTheme.typography.labelMedium, color = KarColors.Navy)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, label ->
                        val day = i + 1
                        val on = day in settings.workDays
                        FilterChip(
                            selected = on,
                            onClick = {
                                onUpdate { s ->
                                    val next = if (on) s.workDays - day else s.workDays + day
                                    s.copy(workDays = next.ifEmpty { s.workDays })
                                }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KarColors.SkyDeep,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Capacity: ${formatMinutes(settings.workDayMinutes)} per working day",
                    style = MaterialTheme.typography.labelSmall, color = KarColors.Slate,
                )
            }
        }

        // --------------------------------------------------------------- holidays
        item {
            Section("Holidays & leave", Icons.Outlined.EventBusy) {
                Text(
                    "Nothing is scheduled on these dates. You can also mark a day from the Scheduler.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                Spacer(Modifier.height(8.dp))
                val upcoming = settings.holidays.sorted().filter { it >= Ist.todayStr() }
                val past = settings.holidays.size - upcoming.size
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    upcoming.forEach { d ->
                        Surface(color = KarColors.SkyLight, shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Ist.formatFull(d), style = MaterialTheme.typography.labelMedium, color = KarColors.SkyDeep)
                                IconButton(onClick = { onUpdate { it.copy(holidays = it.holidays - d) } }) {
                                    Icon(Icons.Outlined.Close, "Remove", tint = KarColors.SkyDeep)
                                }
                            }
                        }
                    }
                }
                if (upcoming.isEmpty()) Text("No upcoming holidays", style = MaterialTheme.typography.labelMedium, color = KarColors.SlateLight)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addHoliday = true }) { Text("Add holiday") }
                    if (past > 0) TextButton(onClick = {
                        val today = Ist.todayStr()
                        onUpdate { it.copy(holidays = it.holidays.filter { d -> d >= today }.toSet()) }
                    }) { Text("Clear $past past") }
                }
            }
        }

        // -------------------------------------------------------------- reminders
        item {
            Section("Reminders", Icons.Outlined.NotificationsActive) {
                Text("Remind me before the target time", style = MaterialTheme.typography.labelMedium, color = KarColors.Navy)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 15, 30, 60, 120).forEach { m ->
                        FilterChip(
                            selected = settings.reminderLeadMinutes == m,
                            onClick = { onUpdate { it.copy(reminderLeadMinutes = m) } },
                            label = { Text(if (m == 0) "At time" else formatMinutes(m)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KarColors.SkyDeep,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    "Exact-time reminders",
                    "Alarm-style notification at the exact minute for activities with a target time",
                    settings.exactReminders,
                ) { on -> onUpdate { it.copy(exactReminders = on) } }
                if (settings.exactReminders && !exactOk) {
                    Surface(color = KarColors.Amber.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Android needs your permission for exact alarms. Until then reminders may arrive a few minutes late.",
                                style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 31) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                exactOk = canUseExactAlarms(context)
                            }) { Text("Allow") }
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------------- backup
        item {
            Section("Automatic backup", Icons.Outlined.Backup) {
                Text(
                    "Once a day the app saves a full backup file into a folder you choose — pick a Google Drive folder to keep a copy off the tablet. The newest 14 are kept.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                Spacer(Modifier.height(8.dp))
                val folderName = settings.backupFolder?.let { Uri.decode(it).substringAfterLast(':').substringAfterLast('/').ifBlank { "Selected folder" } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Folder", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                        Text(folderName ?: "Not chosen yet", fontWeight = FontWeight.SemiBold, color = KarColors.Ink)
                    }
                    OutlinedButton(onClick = onPickBackupFolder) { Text(if (folderName == null) "Choose folder" else "Change") }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    "Daily automatic backup",
                    if (settings.backupFolder == null) "Choose a folder first" else "Runs once a day in the background",
                    settings.autoBackup,
                    enabled = settings.backupFolder != null,
                ) { on -> onUpdate { it.copy(autoBackup = on) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Last backup: " + (settings.lastBackupAt?.let { Ist.formatStamp(it) + " IST" } ?: "never"),
                        style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onBackupNow, enabled = settings.backupFolder != null) { Text("Back up now") }
                }
            }
        }

        // --------------------------------------------------------------- security
        item {
            Section("Privacy lock", Icons.Outlined.Lock) {
                Text(
                    "Protect the app or just your journal with a PIN (and fingerprint, if the tablet has one).",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(LockMode.OFF to "Off", LockMode.JOURNAL to "Journal only", LockMode.APP to "Whole app").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.lockMode == mode,
                            onClick = {
                                if (mode != LockMode.OFF && settings.pinHash == null) {
                                    pendingLock = mode
                                    setPin = true
                                } else onUpdate { it.copy(lockMode = mode) }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KarColors.SkyDeep,
                                selectedLabelColor = Color.White,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (settings.pinHash == null) "No PIN set" else "PIN is set",
                        style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { pendingLock = null; setPin = true }) {
                        Text(if (settings.pinHash == null) "Set PIN" else "Change PIN")
                    }
                }
                if (biometricAvailable(context)) {
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Unlock with fingerprint", "Use the tablet's fingerprint / face unlock", settings.useBiometric) { on ->
                        onUpdate { it.copy(useBiometric = on) }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dialogs
    editTime?.let { field ->
        val current = when (field) {
            TimeField.WORK_START -> settings.workStart
            TimeField.WORK_END -> settings.workEnd
            TimeField.BREAK_START -> settings.breakStart
            TimeField.BREAK_END -> settings.breakEnd
        }
        val state = rememberTimePickerState(initialHour = (current / 60) % 24, initialMinute = current % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { editTime = null },
            containerColor = Color.White,
            title = {
                Text(
                    when (field) {
                        TimeField.WORK_START -> "Day starts at"
                        TimeField.WORK_END -> "Day ends at"
                        TimeField.BREAK_START -> "Break starts at"
                        TimeField.BREAK_END -> "Break ends at"
                    }
                )
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    var m = state.hour * 60 + state.minute
                    if (field == TimeField.WORK_END && m == 0) m = 24 * 60
                    onUpdate {
                        when (field) {
                            TimeField.WORK_START -> it.copy(workStart = m)
                            TimeField.WORK_END -> it.copy(workEnd = m)
                            TimeField.BREAK_START -> it.copy(breakStart = m)
                            TimeField.BREAK_END -> it.copy(breakEnd = m)
                        }
                    }
                    editTime = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTime = null }) { Text("Cancel") } },
        )
    }

    if (addHoliday) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(Ist.today()))
        DatePickerDialog(
            onDismissRequest = { addHoliday = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Ist.pickerMillisToDate(ms).toString()
                        onUpdate { it.copy(holidays = it.holidays + d) }
                    }
                    addHoliday = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addHoliday = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (setPin) {
        SetPinDialog(
            onDismiss = { setPin = false; pendingLock = null },
            onSet = { pin ->
                onSetPin(pin)
                pendingLock?.let { mode -> onUpdate { it.copy(lockMode = mode) } }
                pendingLock = null
                setPin = false
            },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = KarColors.SkyDeep, modifier = Modifier.padding(end = 8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = KarColors.Border, modifier = Modifier.padding(vertical = 10.dp))
            content()
        }
    }
}

@Composable
private fun TimeBox(label: String, minutes: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = KarColors.SkyLight, shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(min = 140.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
            Text(
                if (minutes >= 24 * 60) "12:00 AM (midnight)" else formatTime12(minutesToHHmm(minutes)),
                fontWeight = FontWeight.Bold, color = KarColors.SkyDeep, style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) KarColors.Ink else KarColors.SlateLight)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = KarColors.SkyDeep),
        )
    }
}

