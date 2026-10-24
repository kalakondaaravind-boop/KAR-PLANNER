package com.kargroup.planner.ui

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.LockMode
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.minutesToHHmm
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.components.OutlinedTextField
import androidx.compose.material.icons.outlined.Email
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

/** Which time field the picker is editing. */
private enum class TimeField { WORK_START, WORK_END, BREAK_START, BREAK_END, DIGEST }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PlannerSettings,
    wide: Boolean,
    onUpdate: ((PlannerSettings) -> PlannerSettings) -> Unit,
    onBackupNow: () -> Unit,
    onSetPin: (String) -> Unit,
    email: com.kargroup.planner.desktop.EmailSettings,
    onEmailUpdate: ((com.kargroup.planner.desktop.EmailSettings) -> com.kargroup.planner.desktop.EmailSettings) -> Unit,
    onTestEmail: () -> Unit,
    onOpenDataFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onStartWithWindows: (Boolean) -> Unit,
) {
    val context: Any? = null
    var editTime by remember { mutableStateOf<TimeField?>(null) }
    var addHoliday by remember { mutableStateOf(false) }
    var setPin by remember { mutableStateOf(false) }
    var pendingLock by remember { mutableStateOf<String?>(null) }
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

        // -------------------------------------------------------------- e-mail
        item {
            Section("E-mail & Outlook", Icons.Outlined.Email) {
                Text(
                    "E-mails are prepared for you to review in Outlook E-mails, then sent through the classic Outlook app on this PC, from your own mailbox.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                Spacer(Modifier.height(10.dp))
                var name by remember { mutableStateOf(email.myName) }
                var mail by remember { mutableStateOf(email.myEmail) }
                var sign by remember { mutableStateOf(email.signature) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { v -> name = v; onEmailUpdate { it.copy(myName = v.trim()) } },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.widthIn(min = 220.dp, max = 360.dp),
                    )
                    OutlinedTextField(
                        value = mail,
                        onValueChange = { v -> mail = v; onEmailUpdate { it.copy(myEmail = v.trim()) } },
                        label = { Text("Your e-mail (Outlook account)") },
                        singleLine = true,
                        isError = mail.isNotBlank() && !mail.contains('@'),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                        modifier = Modifier.widthIn(min = 260.dp, max = 400.dp),
                    )
                }
                OutlinedTextField(
                    value = sign,
                    onValueChange = { v -> sign = v; onEmailUpdate { it.copy(signature = v) } },
                    label = { Text("Signature line (designation, company, phone)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                ToggleRow("Copy me (CC)", "Put yourself in CC on MoM, reminder and weekly e-mails", email.ccMe) { on -> onEmailUpdate { it.copy(ccMe = on) } }
                HorizontalDivider(color = KarColors.Border, modifier = Modifier.padding(vertical = 6.dp))
                ToggleRow("Daily digest to me", "Your schedule, meetings, overdue and due items for the day", email.digestOn) { on -> onEmailUpdate { it.copy(digestOn = on) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeBox("Prepare e-mails at", email.digestTime) { editTime = TimeField.DIGEST }
                    Spacer(Modifier.width(12.dp))
                    Text("Every working day; you get a notification when they are ready to review.",
                        style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, modifier = Modifier.weight(1f))
                }
                ToggleRow("Action reminders to owners", "One e-mail per person listing their items that are due soon", email.remindersOn) { on -> onEmailUpdate { it.copy(remindersOn = on) } }
                if (email.remindersOn) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "Due today", 1 to "Today + tomorrow", 2 to "Next 2 days", 3 to "Next 3 days", 7 to "Next 7 days").forEach { (d, label) ->
                            FilterChip(
                                selected = email.reminderDaysAhead == d,
                                onClick = { onEmailUpdate { it.copy(reminderDaysAhead = d) } },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KarColors.SkyDeep, selectedLabelColor = Color.White),
                            )
                        }
                    }
                    ToggleRow("Include overdue items", "Keep reminding until the item is completed or rescheduled", email.remindOverdue) { on -> onEmailUpdate { it.copy(remindOverdue = on) } }
                }
                ToggleRow("Weekly update to each person", "All their open activities and what they completed last week", email.weeklyOn) { on -> onEmailUpdate { it.copy(weeklyOn = on) } }
                if (email.weeklyOn) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, label ->
                            FilterChip(
                                selected = email.weeklyDay == i + 1,
                                onClick = { onEmailUpdate { it.copy(weeklyDay = i + 1) } },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KarColors.SkyDeep, selectedLabelColor = Color.White),
                            )
                        }
                    }
                }
                ToggleRow("MoM to participants", "Prepare the minutes e-mail as soon as a meeting is saved", email.momAuto) { on -> onEmailUpdate { it.copy(momAuto = on) } }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onTestEmail, enabled = email.myEmail.contains('@')) { Text("Send a test e-mail to me") }
                }
            }
        }

        // ------------------------------------------------------------ data & backup
        item {
            Section("Data, backup & startup", Icons.Outlined.Backup) {
                Text(
                    "Your data is saved on this PC automatically after every change, with a dated copy kept each day (newest 30). The file format is the same as the tablet backup, so you can move data between them.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                )
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onBackupNow) { Text("Back up now") }
                    OutlinedButton(onClick = onExport) { Text("Export backup file") }
                    OutlinedButton(onClick = onImport) { Text("Import tablet backup") }
                    TextButton(onClick = onOpenDataFolder) { Text("Open data folder") }
                }
                ToggleRow(
                    "Start with Windows",
                    "Opens minimised in the system tray when you sign in, so e-mails are prepared on time",
                    email.startWithWindows,
                ) { on -> onStartWithWindows(on) }
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
            TimeField.DIGEST -> email.digestTime
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
                        TimeField.DIGEST -> "Prepare e-mails at"
                    }
                )
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    var m = state.hour * 60 + state.minute
                    if (field == TimeField.WORK_END && m == 0) m = 24 * 60
                    if (field == TimeField.DIGEST) {
                        onEmailUpdate { it.copy(digestTime = m.coerceAtMost(23 * 60 + 59)) }
                    } else onUpdate {
                        when (field) {
                            TimeField.WORK_START -> it.copy(workStart = m)
                            TimeField.WORK_END -> it.copy(workEnd = m)
                            TimeField.BREAK_START -> it.copy(breakStart = m)
                            TimeField.BREAK_END -> it.copy(breakEnd = m)
                            TimeField.DIGEST -> it
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

