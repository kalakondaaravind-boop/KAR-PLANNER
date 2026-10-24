package com.kargroup.planner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kargroup.planner.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.minutesToHHmm
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
private val MeetingColor = Color(0xFF7C3AED)

/** Target date + time (end of day when no time) as epoch millis in IST, or null without a date. */
fun targetMillis(t: TaskEntity): Long? {
    val d = t.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val time = t.dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(23, 59)
    return d.atTime(time).atZone(Ist.ZONE).toInstant().toEpochMilli()
}

private fun dayMillis(day: LocalDate, minutes: Int): Long =
    day.atStartOfDay(Ist.ZONE).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(
    items: List<ScheduledItem>,
    blocks: List<ScheduleBlock>,
    settings: PlannerSettings,
    manual: Boolean,
    projects: Map<String, ProjectEntity>,
    today: String,
    wide: Boolean,
    header: @Composable () -> Unit,
    quickAdd: @Composable () -> Unit,
    onManualChange: (Boolean) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onResetManual: () -> Unit,
    onToggle: (TaskEntity, Boolean) -> Unit,
    onStatus: (TaskEntity, String) -> Unit,
    onOpen: (TaskEntity) -> Unit,
    onAddActivity: () -> Unit,
    onPin: (TaskEntity, Long?) -> Unit,
    onOpenMeeting: (String) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
    onToggleHoliday: (String) -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    var selectedDay by rememberSaveable { mutableStateOf(today) }
    val selDate = Ist.parse(selectedDay) ?: LocalDate.now(Ist.ZONE)
    val working = settings.isWorkingDay(selDate)
    val isHoliday = selectedDay in settings.holidays
    val dayItems = remember(items, selectedDay) { items.withIndex().filter { it.value.day == selectedDay } }
    val dayMeetings = remember(blocks, selectedDay) { blocks.filter { it.day == selectedDay } }
    val allocated = dayItems.sumOf { ((it.value.endMillis - it.value.startMillis) / 60_000L).toInt() }
    val workStartMs = dayMillis(selDate, settings.workStart)
    val workEndMs = dayMillis(selDate, settings.workEnd)
    val meetingMinutes = dayMeetings.sumOf {
        ((minOf(it.endMillis, workEndMs) - maxOf(it.startMillis, workStartMs)).coerceAtLeast(0L) / 60_000L).toInt()
    }
    val capacity = if (working) settings.workDayMinutes else 0
    val laterCount = items.count { it.day > selectedDay }
    val planDays = remember(items) {
        items.groupBy { it.day }.mapValues { (_, l) -> l.sumOf { ((it.endMillis - it.startMillis) / 60_000L).toInt() } }
            .toSortedMap()
    }
    val nowZ = Ist.now()
    val dayStartMillis = selDate.atStartOfDay(Ist.ZONE).toInstant().toEpochMilli()
    val breakStartMs = dayMillis(selDate, settings.breakStart)
    val breakEndMs = dayMillis(selDate, settings.breakEnd)
    val showBreak = working && settings.breakEnabled && settings.breakEnd > settings.breakStart

    // Drag-and-drop: where each hour row is on screen, and which card is being dragged.
    val hourBounds = remember { mutableStateMapOf<Int, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pickTimeFor by remember { mutableStateOf<ScheduledItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
    ) {
        item(key = "header") { header() }

        // ------------------------------------------------------------- title + mode
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
                            (if (items.isEmpty()) "Nothing to schedule" else "${items.size} activities planned") +
                                " · working hours ${minutesToHHmm(settings.workStart)}–${minutesToHHmm(settings.workEnd)} (IST)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    ModeToggle(manual, onManualChange)
                }
                Spacer(Modifier.height(10.dp))
                quickAdd()
                Spacer(Modifier.height(10.dp))
                Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (manual) "Manual order: use ▲ ▼ to arrange. Long-press a card and drag it to an hour to fix its time."
                            else "Auto order: ⚡ Quick wins (under 10 min) → Target date & time → Priority. Long-press and drag a card to fix its time.",
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
                // Day selector
                Spacer(Modifier.height(10.dp))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selectedDay = selDate.minusDays(1).toString() }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", tint = Color.White)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                selDate.format(Ist.longDayFormat) + " " + selDate.year,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val rel = Ist.friendly(selectedDay)
                            val note = when {
                                isHoliday -> "Holiday"
                                !working -> "Day off"
                                rel == "Today" || rel == "Tomorrow" || rel == "Yesterday" -> rel
                                else -> null
                            }
                            if (note != null) Text(note, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                        }
                        if (selectedDay != today) {
                            TextButton(onClick = { selectedDay = today }) {
                                Text("Today", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { selectedDay = selDate.plusDays(1).toString() }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", tint = Color.White)
                        }
                    }
                }
                if (planDays.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        planDays.entries.take(7).forEach { (d, mins) ->
                            val sel = d == selectedDay
                            Surface(
                                onClick = { selectedDay = d },
                                color = if (sel) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text(
                                    "${Ist.friendly(d)} · ${formatMinutes(mins)}",
                                    color = if (sel) KarColors.SkyDeep else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------- time allocation
        item(key = "sched-alloc") {
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (!working) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (isHoliday) "Holiday — nothing is booked on this day." else "Day off — nothing is booked on this day.",
                            style = MaterialTheme.typography.titleSmall,
                            color = KarColors.Navy,
                        )
                        if (isHoliday || selDate.dayOfWeek.value in settings.workDays) {
                            TextButton(onClick = { onToggleHoliday(selectedDay) }) {
                                Text(if (isHoliday) "Remove holiday" else "Mark as holiday")
                            }
                        }
                    }
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Time allocation: ${if (allocated > 0) formatMinutes(allocated) else "0 min"} of ${formatMinutes(capacity)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = KarColors.Navy,
                        )
                        val free = (capacity - allocated - meetingMinutes).coerceAtLeast(0)
                        Text(
                            buildString {
                                append("${dayItems.size} activit${if (dayItems.size == 1) "y" else "ies"}")
                                if (meetingMinutes > 0) append(" · meetings ${formatMinutes(meetingMinutes)}")
                                append(" · ")
                                append(if (free > 0) formatMinutes(free) + " free" else "fully booked")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (free > 0) KarColors.Green else KarColors.Amber,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (capacity == 0) 0f else ((allocated + meetingMinutes).toFloat() / capacity).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = KarColors.SkyMid,
                        trackColor = KarColors.SkyLight,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (laterCount > 0) "$laterCount more activit${if (laterCount == 1) "y is" else "ies are"} booked on later days." else " ",
                            style = MaterialTheme.typography.labelSmall,
                            color = KarColors.Slate,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onToggleHoliday(selectedDay) }) {
                            Text("Mark as holiday", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            HorizontalDivider(color = KarColors.Border)
        }

        if (items.isEmpty()) {
            item(key = "sched-empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 30.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Outlined.Schedule, null, tint = KarColors.SkyMid, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Your schedule is clear", style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
                    Text(
                        "Add activities with an estimated time and they will be booked into your working hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KarColors.Slate,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onAddActivity) { Text("+ Add Activity") }
                }
            }
        }

        // ------------------------------------------------------- 24-hour timeline
        this.items((0..23).toList(), key = { "hour-$it" }) { hour ->
            val hourStart = dayStartMillis + hour * 3_600_000L
            val hourEnd = hourStart + 3_600_000L
            val starting = dayItems.filter { it.value.startMillis in hourStart until hourEnd }
            val continuing = dayItems.filter { it.value.startMillis < hourStart && it.value.endMillis > hourStart }
            val meetingsStarting = dayMeetings.filter { it.startMillis in hourStart until hourEnd }
            val meetingsContinuing = dayMeetings.filter { it.startMillis < hourStart && it.endMillis > hourStart }
            val breakStarts = showBreak && breakStartMs in hourStart until hourEnd
            val breakContinues = showBreak && breakStartMs < hourStart && breakEndMs > hourStart
            val work = working && hour * 60 < settings.workEnd && hour * 60 + 60 > settings.workStart
            val isNowHour = selectedDay == today && nowZ.hour == hour
            val hasContent = starting.isNotEmpty() || continuing.isNotEmpty() || meetingsStarting.isNotEmpty() ||
                meetingsContinuing.isNotEmpty() || breakStarts || breakContinues
            DisposableEffect(hour) { onDispose { hourBounds.remove(hour) } }
            Column(
                Modifier
                    .zIndex(if (starting.any { it.value.task.id == draggingId }) 1f else 0f)
                    .onGloballyPositioned { hourBounds[hour] = it.boundsInRoot() }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (work) Color.White else Color(0xFFF6F8FB))
                        .padding(start = 8.dp, end = 4.dp),
                ) {
                    Column(Modifier.width(54.dp).padding(top = if (work) 10.dp else 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%02d:00".format(hour),
                            fontSize = 12.sp,
                            fontWeight = if (work) FontWeight.Bold else FontWeight.Normal,
                            color = if (work) KarColors.Navy else KarColors.SlateLight,
                        )
                        if (working && hour == settings.workStart / 60) {
                            Text("Start ${minutesToHHmm(settings.workStart)}", fontSize = 11.sp, color = KarColors.Green)
                        }
                        if (working && hour == (if (settings.workEnd % 60 == 0) settings.workEnd / 60 else settings.workEnd / 60)) {
                            Text("End ${minutesToHHmm(settings.workEnd)}", fontSize = 11.sp, color = KarColors.Red)
                        }
                    }
                    Box(
                        Modifier.width(1.dp)
                            .height(if (starting.isNotEmpty()) 10.dp else if (work || hasContent || isNowHour) 44.dp else 28.dp)
                            .background(KarColors.Border)
                    )
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        if (isNowHour) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(KarColors.Red))
                                Box(Modifier.weight(1f).height(1.5.dp).background(KarColors.Red))
                                Text(
                                    " Now ${"%02d:%02d".format(nowZ.hour, nowZ.minute)}",
                                    fontSize = 11.sp,
                                    color = KarColors.Red,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (breakStarts) {
                            BlockBar("☕ Break ${minutesToHHmm(settings.breakStart)}–${minutesToHHmm(settings.breakEnd)}", KarColors.Slate, onClick = null)
                        } else if (breakContinues) {
                            BlockBar("☕ Break continues until ${minutesToHHmm(settings.breakEnd)}", KarColors.SlateLight, onClick = null)
                        }
                        meetingsContinuing.forEach { b ->
                            BlockBar("🗓 ${b.title} · until ${formatMillisTime24(b.endMillis)}", MeetingColor) { b.meetingId?.let(onOpenMeeting) }
                        }
                        meetingsStarting.forEach { b ->
                            BlockBar(
                                "🗓 Meeting ${formatMillisTime24(b.startMillis)}–${formatMillisTime24(b.endMillis)} · ${b.title}",
                                MeetingColor,
                                strong = true,
                            ) { b.meetingId?.let(onOpenMeeting) }
                        }
                        continuing.forEach { iv ->
                            BlockBar(
                                "${iv.value.task.text} · continues until ${formatMillisTime24(iv.value.endMillis)}",
                                KarColors.SkyDeep,
                            ) { onOpen(iv.value.task) }
                        }
                        starting.forEach { iv ->
                            DraggableCard(
                                id = iv.value.task.id,
                                onDragState = { dragging -> draggingId = if (dragging) iv.value.task.id else null },
                                onDrop = { y ->
                                    val target = hourBounds.entries.firstOrNull { y >= it.value.top && y < it.value.bottom }?.key
                                    if (target != null) onPin(iv.value.task, dayStartMillis + target * 3_600_000L)
                                },
                            ) {
                                ScheduleRow(
                                    position = iv.index + 1,
                                    item = iv.value,
                                    project = iv.value.task.projectId?.let { projects[it] },
                                    today = today,
                                    manual = manual,
                                    canMoveUp = iv.index > 0,
                                    canMoveDown = iv.index < items.lastIndex,
                                    onMoveUp = { onMove(iv.index, iv.index - 1) },
                                    onMoveDown = { onMove(iv.index, iv.index + 1) },
                                    onToggle = onToggle,
                                    onStatus = onStatus,
                                    onOpen = onOpen,
                                    onPinClick = {
                                        if (iv.value.pinned) onPin(iv.value.task, null) else pickTimeFor = iv.value
                                    },
                                    onReschedule = onReschedule,
                                )
                            }
                        }
                        if (!hasContent && work && !isNowHour) {
                            Text(
                                "Free",
                                style = MaterialTheme.typography.labelSmall,
                                color = KarColors.SlateLight,
                                modifier = Modifier.padding(start = 10.dp, top = 12.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = KarColors.Border)
            }
        }

        item(key = "sched-bottom") {
            Box(Modifier.fillMaxWidth().height(18.dp).background(Color.White, SheetBottomShape))
        }
    }

    pickTimeFor?.let { si ->
        val initial = java.time.Instant.ofEpochMilli(si.startMillis).atZone(Ist.ZONE)
        val tState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute - initial.minute % 5, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTimeFor = null },
            containerColor = Color.White,
            title = { Text("Fix start time · ${Ist.friendly(selectedDay)}") },
            text = {
                Column {
                    Text(si.task.text, style = MaterialTheme.typography.bodyMedium, color = KarColors.Slate, maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = tState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPin(si.task, dayMillis(selDate, tState.hour * 60 + tState.minute))
                    pickTimeFor = null
                }) { Text("Fix time") }
            },
            dismissButton = { TextButton(onClick = { pickTimeFor = null }) { Text("Cancel") } },
        )
    }
}

/** Thin coloured bar used for meetings, breaks and activities continuing from an earlier hour. */
@Composable
private fun BlockBar(text: String, color: Color, strong: Boolean = false, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = if (strong) 0.16f else 0.10f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = if (strong) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(14.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Card that can be long-pressed and dragged up/down onto another hour. [onDrop] gets the drop y (root coordinates). */
@Composable
private fun DraggableCard(
    id: String,
    onDragState: (Boolean) -> Unit,
    onDrop: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (dragging) 2.dp else 1.dp, if (dragging) KarColors.SkyMid else KarColors.Border),
        shadowElevation = if (dragging) 16.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            .onGloballyPositioned { if (!dragging) bounds = it.boundsInRoot() }
            .graphicsLayer {
                translationY = offset.y
                val s = if (dragging) 1.02f else 1f
                scaleX = s
                scaleY = s
            }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        offset = Offset.Zero
                        onDragState(true)
                    },
                    onDragEnd = {
                        val y = bounds.top + 24f + offset.y
                        dragging = false
                        offset = Offset.Zero
                        onDragState(false)
                        onDrop(y)
                    },
                    onDragCancel = {
                        dragging = false
                        offset = Offset.Zero
                        onDragState(false)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += Offset(0f, amount.y)
                    },
                )
            },
    ) {
        content()
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
    onPinClick: () -> Unit,
    onReschedule: (TaskEntity) -> Unit,
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
            Text(formatMillisTime24(item.startMillis), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = KarColors.Navy, maxLines = 1)
            Text(
                "→ " + formatMillisTime24(item.endMillis),
                fontSize = 11.sp,
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
            task.statusNote?.takeIf { it.isNotBlank() }?.let { upd ->
                Text("▸ $upd", style = MaterialTheme.typography.bodySmall, color = KarColors.SkyDeep, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.path?.let { p ->
                Text(
                    p,
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
                    Box(Modifier.clip(RoundedCornerShape(50)).then(if (overdue) Modifier.clickable { onReschedule(task) } else Modifier)) {
                        MetaPill(if (overdue) "Overdue · ${dueLabel(task)} · tap to reschedule" else dueLabel(task), color, Icons.Outlined.Event)
                    }
                }
                if (item.pinned) MetaPill("Fixed ${formatMillisTime24(item.startMillis)}", KarColors.SkyDeep, Icons.Outlined.PushPin)
                if (item.overflow) MetaPill("Longer than free slot", KarColors.Amber, Icons.Outlined.WarningAmber)
                if (atRisk) MetaPill("May miss target", KarColors.Amber, Icons.Outlined.WarningAmber)
                if (project != null) MetaPill(project.name, KarColors.Navy, dotColor = parseHexColor(project.color))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onPinClick) {
                Icon(
                    Icons.Outlined.PushPin,
                    if (item.pinned) "Release fixed time" else "Fix start time",
                    tint = if (item.pinned) KarColors.SkyDeep else KarColors.SlateLight,
                )
            }
            if (manual) {
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
