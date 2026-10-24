package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.desktop.ActivityTemplate
import com.kargroup.planner.desktop.MomTemplate
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

/** Things the keyboard shortcuts and the command bar can ask the main screen to do. */
sealed interface DesktopCommand {
    data object NewActivity : DesktopCommand
    data object NewMeeting : DesktopCommand
    data object OpenPalette : DesktopCommand
    data object Shortcuts : DesktopCommand
    data object ToggleDark : DesktopCommand
    data object Reminders : DesktopCommand
    data object Export : DesktopCommand
    data object Import : DesktopCommand
    data object PrepareEmails : DesktopCommand
    data object TextBigger : DesktopCommand
    data object TextSmaller : DesktopCommand
    data object TextReset : DesktopCommand
    data class Go(val screen: Screen) : DesktopCommand
    data class OpenTask(val task: TaskEntity) : DesktopCommand
    data class OpenMeeting(val meeting: MeetingMinuteEntity) : DesktopCommand
    data class UseTemplate(val template: ActivityTemplate) : DesktopCommand
    data class UseMeetingTemplate(val template: MomTemplate) : DesktopCommand
}

private data class PaletteItem(
    val label: String,
    val hint: String,
    val icon: ImageVector,
    val command: DesktopCommand,
)

/** Keyboard shortcuts shown in the help sheet (F1) and next to commands. */
val DesktopShortcuts = listOf(
    "Ctrl + K" to "Command bar: search everything, jump anywhere",
    "Ctrl + N" to "New activity",
    "Ctrl + M" to "New meeting (MoM)",
    "Ctrl + F" to "Search",
    "Ctrl + E" to "Outlook e-mails",
    "Ctrl + D" to "Light / dark theme",
    "Ctrl + R" to "Reminders",
    "Ctrl + +  /  Ctrl + −" to "Bigger / smaller text",
    "Ctrl + 0" to "Normal text size",
    "Ctrl + 1" to "Dashboard",
    "Ctrl + 2" to "Scheduler",
    "Ctrl + 3" to "Board",
    "Ctrl + 4" to "Table",
    "Ctrl + 5" to "Today",
    "Ctrl + 6" to "By person",
    "Ctrl + 7" to "Minutes of meeting",
    "Ctrl + 8" to "Reports",
    "Ctrl + 9" to "Settings",
    "F1" to "This list of shortcuts",
    "Esc" to "Close a dialog",
)

private val GoTargets = listOf(
    Triple("Dashboard", Screen.Dashboard, "Ctrl+1"),
    Triple("Scheduler", Screen.Schedule, "Ctrl+2"),
    Triple("Board", Screen.Board, "Ctrl+3"),
    Triple("Table", Screen.Table, "Ctrl+4"),
    Triple("Special projects (Gantt)", Screen.Tracker, "Ctrl+T"),
    Triple("Today", Screen.Today, "Ctrl+5"),
    Triple("By person", Screen.People, "Ctrl+6"),
    Triple("Minutes of meeting", Screen.Meetings, "Ctrl+7"),
    Triple("Reports", Screen.Reports, "Ctrl+8"),
    Triple("Settings", Screen.Settings, "Ctrl+9"),
    Triple("Overdue", Screen.Overdue, ""),
    Triple("Upcoming 7 days", Screen.Upcoming, ""),
    Triple("All activities", Screen.AllTasks, ""),
    Triple("Completed", Screen.Completed, ""),
    Triple("Projects", Screen.Projects, ""),
    Triple("My journal", Screen.Journal, ""),
    Triple("Outlook e-mails", Screen.Emails, "Ctrl+E"),
    Triple("Templates", Screen.Templates, ""),
    Triple("Search", Screen.Search, "Ctrl+F"),
)

@Composable
fun CommandBar(
    tasks: List<TaskEntity>,
    meetings: List<MeetingMinuteEntity>,
    persons: List<PersonEntity>,
    templates: List<ActivityTemplate>,
    meetingTemplates: List<MomTemplate>,
    onCommand: (DesktopCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = query.trim()
    val items = remember(q, tasks, meetings, persons, templates, meetingTemplates) {
        val actions = listOf(
            PaletteItem("New activity", "Ctrl+N", Icons.Outlined.Bolt, DesktopCommand.NewActivity),
            PaletteItem("New meeting (MoM)", "Ctrl+M", Icons.Outlined.Groups, DesktopCommand.NewMeeting),
            PaletteItem("Prepare e-mails now", "Digest, reminders", Icons.Outlined.ArrowForward, DesktopCommand.PrepareEmails),
            PaletteItem("Switch light / dark theme", "Ctrl+D", Icons.Outlined.ArrowForward, DesktopCommand.ToggleDark),
            PaletteItem("Show reminders", "Ctrl+R", Icons.Outlined.ArrowForward, DesktopCommand.Reminders),
            PaletteItem("Export backup", "", Icons.Outlined.ArrowForward, DesktopCommand.Export),
            PaletteItem("Import backup", "", Icons.Outlined.ArrowForward, DesktopCommand.Import),
            PaletteItem("Keyboard shortcuts", "F1", Icons.Outlined.Keyboard, DesktopCommand.Shortcuts),
            PaletteItem("Bigger text", "Ctrl +", Icons.Outlined.ArrowForward, DesktopCommand.TextBigger),
            PaletteItem("Smaller text", "Ctrl −", Icons.Outlined.ArrowForward, DesktopCommand.TextSmaller),
        ) + GoTargets.map { (label, s, key) -> PaletteItem("Go to $label", key, Icons.Outlined.ArrowForward, DesktopCommand.Go(s)) } +
            templates.map { PaletteItem("New from template: ${it.name}", "Template", Icons.Outlined.ContentCopy, DesktopCommand.UseTemplate(it)) } +
            meetingTemplates.map { PaletteItem("New meeting from template: ${it.name}", "Template", Icons.Outlined.ContentCopy, DesktopCommand.UseMeetingTemplate(it)) }

        fun matches(s: String) = q.isEmpty() || q.split(" ").filter { it.isNotBlank() }.all { s.contains(it, ignoreCase = true) }
        val cmd = actions.filter { matches(it.label) }
        if (q.length < 2) cmd.take(14) else {
            val t = tasks.filter { matches(it.text) || matches(it.assignee.orEmpty()) || matches(it.statusNote.orEmpty()) }
                .sortedWith(compareBy({ it.done }, { it.dueDate ?: "9999" })).take(12)
                .map {
                    PaletteItem(
                        it.text,
                        listOfNotNull(it.assignee, it.dueDate?.let { d -> Ist.friendly(d) }, if (it.done) "Completed" else null).joinToString(" · "),
                        if (it.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        DesktopCommand.OpenTask(it),
                    )
                }
            val m = meetings.filter { matches(it.title) }.take(6)
                .map { PaletteItem(it.title, "Meeting · ${Ist.friendly(it.meetingDate)}", Icons.Outlined.Groups, DesktopCommand.OpenMeeting(it)) }
            val p = persons.filter { matches(it.name) }.take(5)
                .map { PaletteItem(it.name, "Person · open their activities", Icons.Outlined.Person, DesktopCommand.Go(Screen.Person(it.name))) }
            cmd + t + m + p
        }
    }
    if (selected > items.lastIndex) selected = 0.coerceAtMost(items.lastIndex.coerceAtLeast(0))
    LaunchedEffect(selected) { if (items.isNotEmpty()) runCatching { listState.animateScrollToItem(selected) } }

    fun run(i: Int) {
        val item = items.getOrNull(i) ?: return
        onDismiss()
        onCommand(item.command)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = KarColors.Card,
            shadowElevation = 24.dp,
            modifier = Modifier.padding(top = 60.dp).widthIn(max = 640.dp).fillMaxWidth(0.9f),
        ) {
            Column {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = KarColors.Slate)
                    TextField(
                        value = query,
                        onValueChange = { query = it; selected = 0 },
                        singleLine = true,
                        placeholder = { Text("Type a command, activity, meeting or person…", color = KarColors.SlateLight) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = KarColors.Ink,
                            unfocusedTextColor = KarColors.Ink,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus)
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionDown -> { if (items.isNotEmpty()) selected = (selected + 1) % items.size; true }
                                    Key.DirectionUp -> { if (items.isNotEmpty()) selected = (selected - 1 + items.size) % items.size; true }
                                    Key.Enter, Key.NumPadEnter -> { run(selected); true }
                                    Key.Escape -> { onDismiss(); true }
                                    else -> false
                                }
                            },
                    )
                    Text("Esc", color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = KarColors.Border)
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(vertical = 6.dp), state = listState) {
                    if (items.isEmpty()) item {
                        Text("No matches", color = KarColors.Slate, modifier = Modifier.padding(16.dp))
                    }
                    itemsIndexed(items) { i, it ->
                        val sel = i == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp)
                                .background(if (sel) KarColors.SkyLight else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { run(i) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(it.icon, null, tint = if (sel) KarColors.SkyDeep else KarColors.Slate, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                it.label,
                                color = KarColors.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (it.hint.isNotBlank()) {
                                Spacer(Modifier.width(12.dp))
                                Text(it.hint, color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
                HorizontalDivider(color = KarColors.Border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("↑ ↓ to move", color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall)
                    Text("Enter to open", color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall)
                    Text("F1 for all shortcuts", color = KarColors.SlateLight, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KarColors.Card,
        icon = { Icon(Icons.Outlined.Keyboard, null, tint = KarColors.SkyDeep) },
        title = { Text("Keyboard shortcuts", color = KarColors.Navy) },
        text = {
            Column {
                DesktopShortcuts.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            k,
                            fontWeight = FontWeight.Bold,
                            color = KarColors.SkyDeep,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(96.dp)
                                .background(KarColors.SkyLight, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(v, color = KarColors.Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
