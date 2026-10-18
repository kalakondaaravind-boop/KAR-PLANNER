package com.kargroup.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.JournalEntry
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist

private sealed interface Hit {
    data class Task(val task: TaskEntity, val where: String) : Hit
    data class Meeting(val meeting: MeetingMinuteEntity, val where: String) : Hit
    data class Journal(val entry: JournalEntry, val where: String) : Hit
}

/** Short snippet of [text] around the first match of [q]. */
private fun snippet(text: String, q: String): String {
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return text.take(90)
    val from = (i - 35).coerceAtLeast(0)
    val to = (i + q.length + 55).coerceAtMost(text.length)
    return (if (from > 0) "…" else "") + text.substring(from, to).replace('\n', ' ') + (if (to < text.length) "…" else "")
}

@Composable
fun SearchScreen(
    tasks: List<TaskEntity>,
    meetings: List<MeetingMinuteEntity>,
    journal: List<JournalEntry>,
    projects: Map<String, ProjectEntity>,
    journalLocked: Boolean,
    wide: Boolean,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenMeeting: (MeetingMinuteEntity) -> Unit,
    onOpenJournal: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = query.trim()
    val hits = remember(q, tasks, meetings, journal, journalLocked) {
        if (q.length < 2) emptyList<Hit>() else buildList<Hit> {
            tasks.forEach { t ->
                val where = when {
                    t.text.contains(q, true) -> t.text
                    t.notes?.contains(q, true) == true -> "Notes: " + snippet(t.notes, q)
                    t.completionRemarks?.contains(q, true) == true -> "Remarks: " + snippet(t.completionRemarks, q)
                    t.assignee?.contains(q, true) == true -> "Assigned to ${t.assignee}"
                    t.projectId?.let { projects[it]?.name }?.contains(q, true) == true -> "Project: ${projects[t.projectId]?.name}"
                    else -> null
                }
                if (where != null) add(Hit.Task(t, where))
            }
            meetings.forEach { m ->
                val where = when {
                    m.title.contains(q, true) -> "Meeting · ${Ist.friendly(m.meetingDate)}"
                    m.agenda.contains(q, true) -> "Agenda: " + snippet(m.agenda, q)
                    m.discussionNotes.contains(q, true) -> "Notes: " + snippet(m.discussionNotes, q)
                    m.keyDecisions.any { it.contains(q, true) } -> "Decision: " + snippet(m.keyDecisions.first { it.contains(q, true) }, q)
                    m.actionItems.any { it.description.contains(q, true) } ->
                        "Action: " + snippet(m.actionItems.first { it.description.contains(q, true) }.description, q)
                    m.attendees.any { it.contains(q, true) } -> "Attendee: " + m.attendees.first { it.contains(q, true) }
                    else -> null
                }
                if (where != null) add(Hit.Meeting(m, where))
            }
            if (!journalLocked) journal.forEach { j ->
                val where = when {
                    j.title?.contains(q, true) == true -> j.title
                    j.content.contains(q, true) -> snippet(j.content, q)
                    else -> null
                }
                if (where != null) add(Hit.Journal(j, where))
            }
        }
    }
    val hPad = if (wide) 24.dp else 12.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = KarColors.SkyDeep) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") }
                },
                placeholder = { Text("Search activities, remarks, meetings and journal") },
            )
        }
        item {
            Text(
                when {
                    q.length < 2 -> "Type at least 2 letters" + if (journalLocked) " · journal is locked and not searched" else ""
                    hits.isEmpty() -> "No matches for \"$q\""
                    else -> "${hits.size} result${if (hits.size == 1) "" else "s"}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = KarColors.Slate,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        items(hits.size) { i ->
            when (val h = hits[i]) {
                is Hit.Task -> HitRow(
                    icon = if (h.task.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    tint = if (h.task.done) KarColors.Green else KarColors.SkyDeep,
                    title = h.task.text,
                    line = listOfNotNull(
                        h.where.takeIf { it != h.task.text },
                        h.task.dueDate?.let { "Target ${dueLabel(h.task)}" },
                        if (h.task.done) "Completed" else null,
                    ).joinToString(" · ").ifBlank { "Activity" },
                    onClick = { onOpenTask(h.task) },
                )
                is Hit.Meeting -> HitRow(Icons.Outlined.Groups, KarColors.Navy2, h.meeting.title, h.where) { onOpenMeeting(h.meeting) }
                is Hit.Journal -> HitRow(
                    Icons.Outlined.Book, KarColors.Amber,
                    "Journal · " + Ist.formatFull(h.entry.date),
                    h.where,
                ) { onOpenJournal(h.entry.date) }
            }
        }
    }
}

@Composable
private fun HitRow(icon: ImageVector, tint: Color, title: String, line: String, onClick: () -> Unit) {
    DepthCard(Modifier.fillMaxWidth(), elevation = 3.dp, onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = KarColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
