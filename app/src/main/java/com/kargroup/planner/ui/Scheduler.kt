package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.MetaPill
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import java.time.LocalDate
import java.time.LocalTime

private val SheetTopShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val SheetBottomShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

/** Target date + time (end of day when no time) as epoch millis in IST, or null without a date. */
private fun targetMillis(t: TaskEntity): Long? {
    val d = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = t.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(23, 59)
    return d.atTime(time).atZone(Ist.ZONE).toInstant().toEpochMilli()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedulerScreen(
    items: List<ScheduledItem>,
    manual: Boolean,
    projects: Map<String, ProjectEntity>,
    today: String,
    wide: Boolean,
    header: @Composable () -> Unit,
    onManualChange: (Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onResetManual: () -> Unit,
    onToggle: (TaskEntity, Boolean) -> Unit,
    onStatus: (TaskEntity, String) -> Unit,
    onOpen: (TaskEntity) -> Unit,
    onAddActivity: () -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    val totalMinutes = items.sumOf { ((it.endMillis - it.startMillis) / 60_000L).toInt() }
    val quickCount = items.count { isQuickWin(it.task) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
    ) {
        item(key = "header") { header() }

        item(key = "sched-top") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .depthShadow(10.dp, SheetTopShape)
                    .background(KarBrush.SkyHeader, SheetTopShape)
                    .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 14.dp)
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text("Scheduler", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (items.isEmpty()) "Nothing to schedule"
                            else "${items.size} activities · ${formatMinutes(totalMinutes)} planned from now (IST)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    ModeToggle(manual, onManualChange)
                }
                Spacer(Modifier.height(10.dp))
                Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (manual) "Manual order: use ▲ ▼ to arrange. New activities are added at the end."
                            else "Auto order: ⚡ Quick wins (under 10 min) → Priority → Target date & time",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (manual) {
                            TextButton(onClick = onResetManual) {
                                Text("Reset to auto", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (quickCount > 0 && !manual) {
                    Text(
                        "$quickCount quick win${if (quickCount == 1) "" else "s"} at the top",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        if (items.isEmpty()) {
            item(key = "sched-empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 40.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Schedule, null, tint = KarColors.SkyMid, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Your schedule is clear", style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
                    Text(
                        "Add activities with an estimated time and they will be planned here automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KarColors.Slate,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onAddActivity) { Text("+ Add Activity") }
                }
            }
        }

        itemsIndexed(items, key = { _, it -> "sched-${it.task.id}" }) { i, item ->
            Column(Modifier.animateItem().background(Color.White)) {
                ScheduleRow(
                    position = i + 1,
                    item = item,
                    project = item.task.projectId?.let { projects[it] },
                    today = today,
                    manual = manual,
                    canMoveUp = i > 0,
                    canMoveDown = i < items.lastIndex,
                    onMoveUp = { onMove(i, i - 1) },
                    onMoveDown = { onMove(i, i + 1) },
                    onToggle = onToggle,
                    onStatus = onStatus,
                    onOpen = onOpen,
                )
                HorizontalDivider(color = KarColors.Border, modifier = Modifier.padding(start = 16.dp))
            }
        }

        item(key = "sched-bottom") {
            Box(Modifier.fillMaxWidth().height(18.dp).background(Color.White, SheetBottomShape))
        }
    }
}

@Composable
private fun ModeToggle(manual: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(3.dp)
    ) {
        ModeSegment("Auto", Icons.Outlined.AutoAwesome, selected = !manual) { onChange(false) }
        ModeSegment("Manual", Icons.Outlined.PanTool, selected = manual) { onChange(true) }
    }
}

@Composable
private fun ModeSegment(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) KarColors.SkyDeep else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) KarColors.SkyDeep else Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleRow(
    position: Int,
    item: ScheduledItem,
    project: ProjectEntity?,
    today: String,
    manual: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (TaskEntity, Boolean) -> Unit,
    onStatus: (TaskEntity, String) -> Unit,
    onOpen: (TaskEntity) -> Unit,
) {
    val task = item.task
    val now = System.currentTimeMillis()
    val target = targetMillis(task)
    val overdue = target != null && target < now
    val atRisk = !overdue && target != null && item.endMillis > target
    val accent = priorityColor(task.priority) ?: if (isQuickWin(task)) KarColors.Green else KarColors.SkyMid

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(task) }
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Position + planned slot
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$position", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(formatMillisTime(item.startMillis), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = KarColors.Navy, maxLines = 1)
            Text(
                "→ " + formatMillisTime(item.endMillis),
                fontSize = 10.sp,
                color = KarColors.Slate,
                maxLines = 1,
            )
        }
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle(task, it) },
            colors = CheckboxDefaults.colors(
                checkedColor = KarColors.Green,
                uncheckedColor = priorityColor(task.priority) ?: KarColors.SlateLight,
            ),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(top = 10.dp, start = 2.dp)
        ) {
            Text(task.text, style = MaterialTheme.typography.titleSmall, color = KarColors.Ink)
            if (item.path != null) {
                Text(
                    item.path,
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
                StatusPill(task, onStatus)
                AssigneePill(task)
                PriorityPill(task.priority)
                if (task.estimatedMinutes != null) {
                    EstimatePill(task)
                } else {
                    MetaPill("~${formatMinutes(DEFAULT_PLAN_MINUTES)} (no estimate)", KarColors.SlateLight)
                }
                if (task.dueDate != null) {
                    val color = when {
                        overdue -> KarColors.Red
                        task.dueDate == today -> KarColors.Amber
                        else -> KarColors.Navy2
                    }
                    MetaPill(if (overdue) "Overdue · ${dueLabel(task)}" else dueLabel(task), color, Icons.Outlined.Event)
                }
                if (atRisk) MetaPill("May miss target", KarColors.Amber, Icons.Outlined.WarningAmber)
                if (project != null) MetaPill(project.name, KarColors.Navy, dotColor = parseHexColor(project.color))
            }
        }
        if (manual) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.KeyboardArrowUp, "Move up", tint = if (canMoveUp) KarColors.SkyDeep else KarColors.Border)
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "Move down", tint = if (canMoveDown) KarColors.SkyDeep else KarColors.Border)
                }
            }
        }
    }
}
