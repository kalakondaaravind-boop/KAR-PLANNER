package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.desktop.Tag
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist

// =============================================================================
// Shared filters (Board + Table)
// =============================================================================

data class TaskFilter(
    val text: String = "",
    val person: String? = null,   // "" = unassigned
    val projectId: String? = null,
    val tag: String? = null,
    val due: String? = null,      // overdue / today / week / none
) {
    val active: Boolean get() = text.isNotBlank() || person != null || projectId != null || tag != null || due != null
}

fun TaskFilter.matches(t: TaskEntity, tagsOf: (String) -> Set<String>, today: String): Boolean {
    if (text.isNotBlank()) {
        val q = text.trim()
        val hit = t.text.contains(q, true) || t.notes?.contains(q, true) == true ||
            t.statusNote?.contains(q, true) == true || t.assignee?.contains(q, true) == true
        if (!hit) return false
    }
    if (person != null && !(if (person.isEmpty()) t.assignee.isNullOrBlank() else sameAssignee(t.assignee, person))) return false
    if (projectId != null && t.projectId != projectId) return false
    if (tag != null && tagsOf(t.id).none { it.equals(tag, true) }) return false
    when (due) {
        "overdue" -> if (t.done || t.dueDate == null || t.dueDate >= today) return false
        "today" -> if (t.dueDate != today) return false
        "week" -> {
            val end = (Ist.parse(today) ?: Ist.today()).plusDays(6).toString()
            if (t.dueDate == null || t.dueDate < today || t.dueDate > end) return false
        }
        "none" -> if (t.dueDate != null) return false
    }
    return true
}

@Composable
private fun FilterMenu(label: String, value: String?, options: List<Pair<String?, String>>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            color = if (value != null) KarColors.SkyLight else KarColors.Card,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (value != null) KarColors.SkyDeep.copy(alpha = 0.5f) else KarColors.Border),
        ) {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (value == null) label else "$label: ${options.firstOrNull { it.first == value }?.second ?: value}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (value != null) KarColors.SkyDeep else KarColors.Ink,
                    maxLines = 1,
                )
                Icon(Icons.Outlined.ArrowDropDown, null, tint = KarColors.Slate, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onPick(null); open = false })
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterBar(
    filter: TaskFilter,
    onChange: (TaskFilter) -> Unit,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    tags: List<Tag>,
    trailing: @Composable () -> Unit = {},
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.widthIn(min = 220.dp).clip(RoundedCornerShape(8.dp)).background(KarColors.Card)
                .border(1.dp, KarColors.Border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = KarColors.Slate, modifier = Modifier.size(16.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = filter.text,
                onValueChange = { onChange(filter.copy(text = it)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = KarColors.Ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(KarColors.SkyDeep),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp).width(180.dp),
                decorationBox = { inner ->
                    if (filter.text.isEmpty()) Text("Filter…", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight)
                    inner()
                },
            )
        }
        FilterMenu("Person", filter.person, listOf("" to "Unassigned") + persons.map { it.name to it.name }) { onChange(filter.copy(person = it)) }
        if (projects.isNotEmpty()) FilterMenu("Project", filter.projectId, projects.map { it.id to it.name }) { onChange(filter.copy(projectId = it)) }
        if (tags.isNotEmpty()) FilterMenu("Tag", filter.tag, tags.map { it.name to it.name }) { onChange(filter.copy(tag = it)) }
        FilterMenu(
            "Target", filter.due,
            listOf("overdue" to "Overdue", "today" to "Due today", "week" to "Next 7 days", "none" to "No date"),
        ) { onChange(filter.copy(due = it)) }
        if (filter.active) {
            TextButton(onClick = { onChange(TaskFilter()) }) {
                Icon(Icons.Outlined.Close, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Clear filters")
            }
        }
        trailing()
    }
}

@Composable
fun TagChip(name: String, color: String) {
    val c = parseHexColor(color)
    Text(
        name,
        color = c,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// =============================================================================
// Kanban board
// =============================================================================

private data class BoardColumn(val status: String, val title: String, val color: Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardScreen(
    tasks: List<TaskEntity>,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    tags: List<Tag>,
    tagsOf: (String) -> Set<String>,
    tagColor: (String) -> String,
    today: String,
    onOpen: (TaskEntity) -> Unit,
    onMove: (TaskEntity, String) -> Unit,
    onAdd: () -> Unit,
) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    val index = remember(tasks) { TaskIndex(tasks) }
    val recentCut = System.currentTimeMillis() - 14L * 86_400_000L
    val columns = listOf(
        BoardColumn(TaskStatus.NOT_STARTED, "Not started", KarColors.Slate),
        BoardColumn(TaskStatus.INITIATED, "Initiated", KarColors.SkyDeep),
        BoardColumn(TaskStatus.COMPLETED, "Completed (14 days)", KarColors.Green),
    )
    val visible = tasks.filter { filter.matches(it, tagsOf, today) }
    val byColumn = columns.associate { c ->
        c.status to when (c.status) {
            TaskStatus.COMPLETED -> visible.filter { it.done && (it.completedAt ?: 0L) >= recentCut }.sortedByDescending { it.completedAt }
            TaskStatus.INITIATED -> visible.filter { !it.done && it.status == TaskStatus.INITIATED }.sortedWith(scheduleOrder)
            else -> visible.filter { !it.done && it.status != TaskStatus.INITIATED }.sortedWith(scheduleOrder)
        }
    }
    val columnBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var hoverColumn by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Board", style = MaterialTheme.typography.titleLarge, color = KarColors.Navy)
                Text("Drag a card to another column to change its status. Moving to Completed asks for remarks.",
                    style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
            }
        }
        Spacer(Modifier.height(10.dp))
        FilterBar(filter, { filter = it }, persons, projects, tags)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            columns.forEach { col ->
                val list = byColumn[col.status].orEmpty()
                val hot = dragging != null && hoverColumn == col.status
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { columnBounds[col.status] = it.boundsInRoot() }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hot) col.color.copy(alpha = 0.10f) else KarColors.SkyWash)
                        .border(1.dp, if (hot) col.color else KarColors.Border, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(col.color))
                        Spacer(Modifier.width(8.dp))
                        Text(col.title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, modifier = Modifier.weight(1f))
                        Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = KarColors.Slate,
                            modifier = Modifier.background(KarColors.Card, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    if (col.status == TaskStatus.NOT_STARTED) {
                        TextButton(onClick = onAdd) { Text("+ Add activity", style = MaterialTheme.typography.labelMedium) }
                    }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { t ->
                            BoardCard(
                                task = t,
                                path = index.ancestors(t.id).lastOrNull()?.text,
                                subtasks = index.children(t.id).let { c -> if (c.isEmpty()) null else "${c.count { it.done }}/${c.size}" },
                                tags = tagsOf(t.id).toList(),
                                tagColor = tagColor,
                                project = t.projectId?.let { id -> projects.firstOrNull { it.id == id } },
                                today = today,
                                onOpen = { onOpen(t) },
                                onDrag = { pos ->
                                    dragging = t.id
                                    hoverColumn = columnBounds.entries.firstOrNull { it.value.contains(pos) }?.key
                                },
                                onDrop = {
                                    val target = hoverColumn
                                    dragging = null
                                    hoverColumn = null
                                    if (target != null && target != col.status) onMove(t, target)
                                },
                            )
                        }
                        if (list.isEmpty()) item {
                            Text("Nothing here", style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardCard(
    task: TaskEntity,
    path: String?,
    subtasks: String?,
    tags: List<String>,
    tagColor: (String) -> String,
    project: ProjectEntity?,
    today: String,
    onOpen: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val overdue = !task.done && task.dueDate != null && task.dueDate < today
    val accent = priorityColor(task.priority) ?: KarColors.Border
    Surface(
        color = KarColors.Card,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (active) KarColors.SkyMid else KarColors.Border),
        shadowElevation = if (active) 12.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (active) 10f else 0f)
            .graphicsLayer { translationX = offset.x; translationY = offset.y; rotationZ = if (active) 1.5f else 0f }
            .onGloballyPositioned { if (!active) origin = it.boundsInRoot().topLeft }
            .pointerInput(task.id) {
                detectDragGestures(
                    onDragStart = { p -> active = true; offset = Offset.Zero; onDrag(origin + p) },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount
                        onDrag(origin + offset + change.position)
                    },
                    onDragEnd = { active = false; offset = Offset.Zero; onDrop() },
                    onDragCancel = { active = false; offset = Offset.Zero; onDrop() },
                )
            }
            .clickable(onClick = onOpen),
    ) {
        Row {
            Column(
                Modifier
                    .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
                    .padding(start = 13.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
            ) {
                if (path != null) Text(path, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    task.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.done) KarColors.Slate else KarColors.Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                task.statusNote?.takeIf { it.isNotBlank() && !task.done }?.let {
                    Text("▸ $it", style = MaterialTheme.typography.labelSmall, color = KarColors.SkyDeep, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (task.done) task.completionRemarks?.takeIf { it.isNotBlank() }?.let {
                    Text("💬 $it", style = MaterialTheme.typography.labelSmall, color = KarColors.Green, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (tags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        tags.forEach { TagChip(it, tagColor(it)) }
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val who = task.assignee
                    if (who != null) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape).background(KarColors.SkyLight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(who.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                                style = MaterialTheme.typography.labelSmall, color = KarColors.SkyDeep, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(who, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.weight(1f))
                    if (subtasks != null) {
                        Icon(Icons.Outlined.AccountTree, null, tint = KarColors.SlateLight, modifier = Modifier.size(12.dp))
                        Text(" $subtasks  ", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                    }
                    if (task.dueDate != null) {
                        val c = when { task.done -> KarColors.Slate; overdue -> KarColors.Red; task.dueDate == today -> KarColors.Amber; else -> KarColors.Slate }
                        Text(
                            Ist.friendly(task.dueDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = c,
                            fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.background(c.copy(alpha = if (overdue || task.dueDate == today) 0.12f else 0f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (project != null) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(parseHexColor(project.color)))
                        Spacer(Modifier.width(5.dp))
                        Text(project.name, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, maxLines = 1)
                    }
                }
            }
        }
    }
}


@Suppress("unused")
private val unusedFilterIcon = Icons.Outlined.FilterList
