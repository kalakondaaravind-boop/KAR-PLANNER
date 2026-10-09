package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.depthShadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.MetaPill
import com.kargroup.planner.ui.components.contextIcon
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import com.kargroup.planner.util.Recurrence

class TaskActions(
    val onToggle: (TaskEntity, Boolean) -> Unit,
    val onToggleCollapse: (TaskEntity) -> Unit,
    val onOpen: (TaskEntity) -> Unit,
    val onDrill: (String?) -> Unit,
    val onAddSubtask: (TaskEntity) -> Unit,
    val onDelete: (TaskEntity) -> Unit,
)

private val SheetTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val SheetBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
fun TaskListScreen(
    title: String,
    subtitle: String?,
    accent: Color?,
    sections: List<TaskSection>,
    index: TaskIndex,
    drillRootId: String?,
    showCompleted: Boolean,
    showCompletedToggle: Boolean,
    projects: Map<String, ProjectEntity>,
    contexts: Map<String, ContextTagEntity>,
    today: String,
    wide: Boolean,
    actions: TaskActions,
    onShowCompletedChange: (Boolean) -> Unit,
    onAddActivity: () -> Unit,
    header: @Composable () -> Unit,
    sheetExtras: (@Composable () -> Unit)? = null,
) {
    val hPad = if (wide) 24.dp else 12.dp
    val drillRoot = drillRootId?.let { index.byId[it] }
    val totalRows = sections.sumOf { it.rows.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
    ) {
        item(key = "header") { header() }

        item(key = "sheet-top") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .depthShadow(10.dp, SheetTop)
                    .background(KarBrush.SkyHeader, SheetTop)
                    .padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (accent != null) {
                        Box(Modifier.size(width = 5.dp, height = 28.dp).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        if (!subtitle.isNullOrBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f))
                        }
                    }
                    if (showCompletedToggle) {
                        FilterChip(
                            selected = showCompleted,
                            onClick = { onShowCompletedChange(!showCompleted) },
                            label = { Text("Show done") },
                            leadingIcon = { Icon(Icons.Outlined.TaskAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White,
                                iconColor = Color.White,
                                selectedContainerColor = Color.White,
                                selectedLabelColor = KarColors.SkyDeep,
                                selectedLeadingIconColor = KarColors.SkyDeep,
                            ),
                        )
                    }
                }
                if (drillRoot != null) {
                    Spacer(Modifier.height(8.dp))
                    Breadcrumbs(title, index.ancestors(drillRoot.id), drillRoot, actions.onDrill)
                }
                sheetExtras?.invoke()
            }
        }

        if (totalRows == 0) {
            item(key = "empty") { EmptyState(onAddActivity) }
        }

        sections.forEach { section ->
            if (section.title != null) {
                item(key = "sec-${section.key}") { SectionHeader(section) }
            }
            items(section.rows, key = { "${section.key}-${it.task.id}" }) { row ->
                Column(Modifier.animateItem().background(Color.White)) {
                    TaskRow(
                        row = row,
                        project = row.task.projectId?.let { projects[it] },
                        context = row.task.contextId?.let { contexts[it] },
                        today = today,
                        isDrillRoot = row.task.id == drillRootId,
                        actions = actions,
                    )
                    HorizontalDivider(color = KarColors.Border, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }

        item(key = "sheet-bottom") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(Color.White, SheetBottom)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Breadcrumbs(
    rootLabel: String,
    ancestors: List<TaskEntity>,
    current: TaskEntity,
    onDrill: (String?) -> Unit,
) {
    Surface(color = KarColors.Tint, shape = RoundedCornerShape(10.dp)) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { onDrill(null) }) { Text(rootLabel, maxLines = 1) }
            ancestors.forEach { a ->
                Text("›", color = KarColors.SlateLight, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { onDrill(a.id) }) {
                    Text(a.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(0.dp))
                }
            }
            Text("›", color = KarColors.SlateLight, modifier = Modifier.padding(top = 12.dp))
            Text(
                current.text,
                fontWeight = FontWeight.Bold,
                color = KarColors.Navy,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            )
            IconButton(onClick = { onDrill(null) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Exit drilldown", tint = KarColors.Slate)
            }
        }
    }
}

@Composable
private fun SectionHeader(section: TaskSection) {
    val color = when (section.tone) {
        SectionTone.Danger -> KarColors.Red
        SectionTone.Accent -> KarColors.Navy2
        SectionTone.Normal -> KarColors.Slate
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (section.title ?: "").uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
            Text(
                section.rows.count { it.depth == 0 }.toString(),
                color = color,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = KarColors.Green, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(10.dp))
        Text("All clear here", style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
        Text(
            "No activities in this view. Add one to get going.",
            style = MaterialTheme.typography.bodyMedium,
            color = KarColors.Slate,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAdd) { Text("+ Add Activity") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    row: TreeRow,
    project: ProjectEntity?,
    context: ContextTagEntity?,
    today: String,
    isDrillRoot: Boolean,
    actions: TaskActions,
) {
    val task = row.task
    var menuOpen by remember { mutableStateOf(false) }
    val indent = (minOf(row.depth, 8) * 22).dp
    val due = task.dueDate
    val overdue = !task.done && due != null && due < today

    val stripe = when {
        task.done -> KarColors.Green
        task.priority == Priority.HIGH -> KarColors.Red
        task.priority == Priority.MEDIUM -> KarColors.Amber
        overdue -> KarColors.Red
        else -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isDrillRoot) KarColors.GoldTint else Color.White)
            .drawBehind {
                if (stripe != null) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(stripe, stripe.copy(alpha = 0.55f))),
                        topLeft = Offset(indent.toPx(), 6.dp.toPx()),
                        size = Size(4.dp.toPx(), size.height - 12.dp.toPx()),
                    )
                }
            }
            .clickable { actions.onOpen(task) }
            .padding(start = 4.dp + indent, end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (row.childCount > 0) {
                IconButton(onClick = { actions.onToggleCollapse(task) }) {
                    Icon(
                        if (task.isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.ExpandMore,
                        contentDescription = if (task.isCollapsed) "Expand" else "Collapse",
                        tint = KarColors.Slate,
                    )
                }
            }
        }
        Checkbox(
            checked = task.done,
            onCheckedChange = { actions.onToggle(task, it) },
            colors = CheckboxDefaults.colors(
                checkedColor = KarColors.Green,
                uncheckedColor = when (task.priority) {
                    Priority.HIGH -> KarColors.Red
                    Priority.MEDIUM -> KarColors.Amber
                    else -> KarColors.SlateLight
                },
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(top = 10.dp, bottom = 6.dp, start = 2.dp)
        ) {
            Text(
                task.text,
                style = if (row.depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                color = if (task.done) KarColors.SlateLight else KarColors.Ink,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            val firstNoteLine = task.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }
            if (firstNoteLine != null) {
                Text(
                    firstNoteLine.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = KarColors.Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (task.priority) {
                    Priority.HIGH -> MetaPill("High", KarColors.Red, dotColor = KarColors.Red)
                    Priority.MEDIUM -> MetaPill("Medium", KarColors.Amber, dotColor = KarColors.Amber)
                }
                if (due != null) {
                    val color = when {
                        task.done -> KarColors.Slate
                        overdue -> KarColors.Red
                        due == today -> KarColors.Amber
                        else -> KarColors.Navy2
                    }
                    MetaPill(if (overdue) "Overdue · ${Ist.friendly(due)}" else Ist.friendly(due), color, Icons.Outlined.Event)
                }
                if (task.isRecurring) MetaPill(Recurrence.describe(task), KarColors.Navy2, Icons.Outlined.Repeat)
                if (project != null) {
                    val pc = parseHexColor(project.color)
                    MetaPill(project.name, KarColors.Navy, dotColor = pc)
                }
                if (context != null) {
                    val cc = parseHexColor(context.color)
                    MetaPill(context.name, cc, contextIcon(context.icon))
                }
                if (task.meetingId != null) MetaPill("MoM", KarColors.Navy2, Icons.Outlined.Groups)
                if (row.childCount > 0) {
                    MetaPill("${row.doneChildCount}/${row.childCount}", KarColors.Slate, Icons.Outlined.Checklist)
                }
                if (task.done && task.completedAt != null) {
                    MetaPill("Done ${Ist.formatStamp(task.completedAt)}", KarColors.Green)
                }
            }
        }
        IconButton(onClick = { actions.onDrill(task.id) }) {
            Icon(Icons.Outlined.CenterFocusStrong, contentDescription = "Drill down", tint = KarColors.Navy2)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More", tint = KarColors.Slate)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add subtask") },
                    leadingIcon = { Icon(Icons.Outlined.AddTask, null) },
                    onClick = { menuOpen = false; actions.onAddSubtask(task) },
                )
                DropdownMenuItem(
                    text = { Text("Drill down") },
                    leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) },
                    onClick = { menuOpen = false; actions.onDrill(task.id) },
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menuOpen = false; actions.onOpen(task) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = KarColors.Red) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = KarColors.Red) },
                    onClick = { menuOpen = false; actions.onDelete(task) },
                )
            }
        }
    }
}
