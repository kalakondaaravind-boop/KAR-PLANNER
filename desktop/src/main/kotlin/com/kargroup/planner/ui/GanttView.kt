package com.kargroup.planner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.desktop.PlanCalc
import com.kargroup.planner.desktop.PlanRow
import com.kargroup.planner.desktop.PlanStatus
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.KarPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale

val PlanStatusColors: Map<String, Color> = mapOf(
    PlanStatus.COMPLETED to Color(0xFF10B981),
    PlanStatus.IN_PROGRESS to Color(0xFF0EA5E9),
    PlanStatus.PLANNED to Color(0xFF94A3B8),
    PlanStatus.OVERDUE to Color(0xFFEF4444),
)

private val RowH = 30.dp
private val GridW = 470.dp
private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val shortFmt = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)

fun planDate(d: LocalDate?): String = d?.format(shortFmt) ?: "–"

/**
 * Gantt chart: task grid on the left, timeline on the right (fits the window, no sideways scrolling).
 * Bars can be dragged to move a task or dragged at their right edge to change its finish.
 */
@Composable
fun GanttView(
    rows: List<PlanRow>,
    settings: PlannerSettings,
    windowStart: LocalDate,
    windowDays: Int,
    today: LocalDate,
    showBaseline: Boolean,
    showLinks: Boolean,
    selectedId: String?,
    onSelect: (PlanRow) -> Unit,
    onOpen: (PlanRow) -> Unit,
    onMove: (PlanRow, Int) -> Unit,      // shift by N calendar days
    onResize: (PlanRow, Int) -> Unit,    // change finish by N calendar days
) {
    val windowEnd = windowStart.plusDays(windowDays.toLong() - 1)
    val vScroll = rememberScrollState()
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    var dragResize by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(KarColors.Card)) {
        // ---------------------------------------------------------------- header
        Row(Modifier.fillMaxWidth().height(44.dp).background(KarColors.SkyWash)) {
            Row(Modifier.width(GridW).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeadCell("WBS", 44.dp); HeadCell("Task", 0.dp, weight = true); HeadCell("Resource", 82.dp)
                HeadCell("Start", 66.dp); HeadCell("Finish", 66.dp); HeadCell("Days", 34.dp); HeadCell("%", 34.dp)
            }
            TimelineHeader(windowStart, windowDays, today, settings, Modifier.weight(1f).height(44.dp))
        }
        HorizontalDivider(color = KarColors.Border)

        // ----------------------------------------------------------------- rows
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val timelineW = (maxWidth - GridW).coerceAtLeast(100.dp)
            Box(Modifier.fillMaxSize().verticalScroll(vScroll)) {
                Column {
                    rows.forEach { r ->
                        val sel = r.task.id == selectedId
                        Row(
                            Modifier.fillMaxWidth().height(RowH)
                                .background(if (sel) KarColors.SkyLight else Color.Transparent)
                        ) {
                            Row(
                                Modifier.width(GridW).height(RowH)
                                    .pointerInput(r.task.id) { detectTapGestures(onDoubleTap = { onOpen(r) }, onTap = { onSelect(r) }) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GridCell(r.wbs, 44.dp, KarColors.Slate)
                                Text(
                                    r.task.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (r.isGroup) FontWeight.Bold else FontWeight.Normal,
                                    color = KarColors.Ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = (r.depth * 12).dp, end = 6.dp),
                                )
                                GridCell(r.task.resource.ifBlank { "–" }, 82.dp, KarColors.Slate)
                                GridCell(planDate(r.start), 66.dp, KarColors.Ink)
                                GridCell(planDate(r.finish), 66.dp, if (r.status == PlanStatus.OVERDUE) KarColors.Red else KarColors.Ink)
                                GridCell(r.duration?.toString() ?: "–", 34.dp, KarColors.Slate)
                                GridCell("${r.percent}%", 34.dp, PlanStatusColors[r.status] ?: KarColors.Slate)
                            }
                            TimelineRow(
                                row = r,
                                windowStart = windowStart,
                                windowDays = windowDays,
                                today = today,
                                settings = settings,
                                showBaseline = showBaseline,
                                dragDx = if (dragId == r.task.id) dragDx else 0f,
                                dragResize = dragResize && dragId == r.task.id,
                                modifier = Modifier.width(timelineW).height(RowH)
                                    .pointerInput(r.task.id, r.start, r.finish, windowStart, windowDays, timelineW) {
                                        val pxPerDay = size.width.toFloat() / windowDays
                                        detectDragGestures(
                                            onDragStart = { p ->
                                                if (r.isGroup || r.start == null || r.finish == null) return@detectDragGestures
                                                val endX = (ChronoUnit.DAYS.between(windowStart, r.finish) + 1) * pxPerDay
                                                dragResize = kotlin.math.abs(p.x - endX) < 8f
                                                dragId = r.task.id
                                                dragDx = 0f
                                            },
                                            onDrag = { ch, amt -> if (dragId == r.task.id) { ch.consume(); dragDx += amt.x } },
                                            onDragEnd = {
                                                if (dragId == r.task.id) {
                                                    val days = Math.round(dragDx / pxPerDay)
                                                    if (days != 0) { if (dragResize) onResize(r, days) else onMove(r, days) }
                                                }
                                                dragId = null; dragDx = 0f; dragResize = false
                                            },
                                            onDragCancel = { dragId = null; dragDx = 0f; dragResize = false },
                                        )
                                    }
                                    .pointerInput(r.task.id) { detectTapGestures(onDoubleTap = { onOpen(r) }, onTap = { onSelect(r) }) },
                            )
                        }
                        HorizontalDivider(color = KarColors.Border.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(40.dp))
                }
                // dependency connectors over the timeline
                if (showLinks) {
                    val index = rows.withIndex().associate { it.value.task.id to it.index }
                    Canvas(Modifier.matchParentSize().padding(start = GridW)) {
                        val ppd = size.width / windowDays
                        val rh = RowH.toPx() + 1f
                        fun x(d: LocalDate) = ChronoUnit.DAYS.between(windowStart, d) * ppd
                        rows.forEachIndexed { i, r ->
                            r.task.predecessors.forEach { dep ->
                                val j = index[dep.taskId] ?: return@forEach
                                val p = rows[j]
                                val ps = p.start ?: return@forEach; val pf = p.finish ?: return@forEach
                                val ts = r.start ?: return@forEach; val tf = r.finish ?: return@forEach
                                val fromX = when (dep.type) { "SS", "SF" -> x(ps); else -> x(pf) + ppd }
                                val toX = when (dep.type) { "FF", "SF" -> x(tf) + ppd; else -> x(ts) }
                                val y1 = j * rh + rh / 2
                                val y2 = i * rh + rh / 2
                                val c = KarColors.Slate.copy(alpha = 0.8f)
                                val path = Path().apply {
                                    moveTo(fromX, y1)
                                    val mid = (if (dep.type == "FF" || dep.type == "SF") maxOf(fromX, toX) + 8f else minOf(fromX + 8f, toX - 8f).coerceAtLeast(fromX + 4f))
                                    lineTo(mid, y1); lineTo(mid, y2); lineTo(toX, y2)
                                }
                                drawPath(path, c, style = Stroke(width = 1.2f))
                                val dir = if (dep.type == "FF" || dep.type == "SF") -1f else 1f
                                drawPath(Path().apply {
                                    moveTo(toX, y2); lineTo(toX - 5f * dir, y2 - 4f); lineTo(toX - 5f * dir, y2 + 4f); close()
                                }, c)
                            }
                        }
                    }
                }
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = windowEnd
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeadCell(text: String, w: Dp, weight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = KarColors.Slate,
        maxLines = 1,
        modifier = if (weight) Modifier.weight(1f) else Modifier.width(w),
    )
}

@Composable
private fun GridCell(text: String, w: Dp, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(w))
}

/** Two-line timeline header: months on top, days (or ISO weeks when zoomed out) below. */
@Composable
private fun TimelineHeader(start: LocalDate, days: Int, today: LocalDate, s: PlannerSettings, modifier: Modifier) {
    val ink = KarColors.Slate
    val grid = KarColors.Border
    val todayC = KarColors.Red
    val nonWork = if (KarPalette.dark) Color(0x22FFFFFF) else Color(0x0F0B1E3F)
    BoxWithConstraints(modifier) {
        val ppd = constraints.maxWidth.toFloat() / days
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            for (i in 0 until days) {
                val d = start.plusDays(i.toLong())
                val x = i * ppd
                if (ppd >= 4f && !PlanCalc.isWorking(d, s)) drawRect(nonWork, Offset(x, h / 2), Size(ppd, h / 2))
                if (d.dayOfMonth == 1 || i == 0) drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
                if (d == today) drawRect(todayC.copy(alpha = 0.18f), Offset(x, h / 2), Size(ppd.coerceAtLeast(2f), h / 2))
            }
            drawLine(grid, Offset(0f, h / 2), Offset(size.width, h / 2), 1f)
        }
        // labels
        Box(Modifier.fillMaxSize()) {
            var i = 0
            while (i < days) {
                val d = start.plusDays(i.toLong())
                if (d.dayOfMonth == 1 || i == 0) {
                    val x = (i * ppd)
                    val lenDays = (d.lengthOfMonth() - d.dayOfMonth + 1).coerceAtMost(days - i)
                    if (lenDays * ppd > 46f) {
                        Text(d.format(monthFmt), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = KarColors.Navy,
                            maxLines = 1, modifier = Modifier.padding(start = (x / 1f).toDpCompat() + 4.dp, top = 3.dp))
                    }
                }
                i++
            }
            if (ppd >= 16f) {
                for (k in 0 until days) {
                    val d = start.plusDays(k.toLong())
                    Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = if (d == today) todayC else ink, maxLines = 1,
                        modifier = Modifier.padding(start = (k * ppd).toDpCompat() + 3.dp, top = 25.dp))
                }
            } else {
                for (k in 0 until days) {
                    val d = start.plusDays(k.toLong())
                    if (d.dayOfWeek.value == 1 && ppd * 7 > 26f) {
                        Text("W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1,
                            modifier = Modifier.padding(start = (k * ppd).toDpCompat() + 3.dp, top = 25.dp))
                    }
                }
            }
        }
    }
}

/** Converts timeline pixels to dp for label placement. */
@Composable
private fun Float.toDpCompat(): Dp = with(androidx.compose.ui.platform.LocalDensity.current) { this@toDpCompat.toDp() }

@Composable
private fun TimelineRow(
    row: PlanRow,
    windowStart: LocalDate,
    windowDays: Int,
    today: LocalDate,
    settings: PlannerSettings,
    showBaseline: Boolean,
    dragDx: Float,
    dragResize: Boolean,
    modifier: Modifier,
) {
    val statusC = PlanStatusColors[row.status] ?: KarColors.SkyMid
    val grid = KarColors.Border.copy(alpha = 0.6f)
    val nonWork = if (KarPalette.dark) Color(0x14FFFFFF) else Color(0x0A0B1E3F)
    val todayC = KarColors.Red
    val baseC = KarColors.SlateLight
    val groupC = if (KarPalette.dark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val textC = KarColors.Slate
    Canvas(modifier) {
        val ppd = size.width / windowDays
        val h = size.height
        fun x(d: LocalDate) = ChronoUnit.DAYS.between(windowStart, d) * ppd
        // background: non-working days, month lines, today
        if (ppd >= 4f) for (i in 0 until windowDays) {
            val d = windowStart.plusDays(i.toLong())
            if (!PlanCalc.isWorking(d, settings)) drawRect(nonWork, Offset(i * ppd, 0f), Size(ppd, h))
        }
        for (i in 0 until windowDays) {
            val d = windowStart.plusDays(i.toLong())
            if (d.dayOfMonth == 1) drawLine(grid, Offset(i * ppd, 0f), Offset(i * ppd, h), 1f)
        }
        val tx = x(today) + ppd / 2
        drawLine(todayC, Offset(tx, 0f), Offset(tx, h), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))

        val s0 = row.start ?: row.finish ?: return@Canvas
        val f0 = row.finish ?: return@Canvas
        val left = x(s0) + if (dragResize) 0f else dragDx
        val right = x(f0) + ppd + dragDx
        val barTop = h * 0.22f
        val barH = h * (if (showBaseline) 0.42f else 0.56f)

        if (row.task.milestone) {
            val cx = x(f0) + ppd / 2 + dragDx
            val cy = h / 2
            val r = h * 0.28f
            drawPath(Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }, statusC)
            return@Canvas
        }
        if (row.isGroup) {
            // summary bar with end caps
            val y = h * 0.34f
            drawRect(groupC, Offset(left, y), Size((right - left).coerceAtLeast(2f), h * 0.18f))
            drawPath(Path().apply { moveTo(left, y); lineTo(left + 6f, y); lineTo(left, y + h * 0.36f); close() }, groupC)
            drawPath(Path().apply { moveTo(right, y); lineTo(right - 6f, y); lineTo(right, y + h * 0.36f); close() }, groupC)
            if (row.percent > 0) drawRect(KarColors.Green, Offset(left, y + h * 0.18f + 1f), Size((right - left) * row.percent / 100f, 2.5f))
            return@Canvas
        }
        val w = (right - left).coerceAtLeast(3f)
        // planned bar (light) + progress (solid)
        drawRoundRect(statusC.copy(alpha = 0.30f), Offset(left, barTop), Size(w, barH), CornerRadius(3f, 3f))
        if (row.percent > 0) drawRoundRect(statusC, Offset(left, barTop), Size(w * row.percent / 100f, barH), CornerRadius(3f, 3f))
        // overdue part (target passed, not complete)
        if (row.status == PlanStatus.OVERDUE) {
            val ox = x(f0) + ppd
            val ex = (x(today) + ppd).coerceAtLeast(ox)
            if (ex > ox) drawRect(todayC.copy(alpha = 0.25f), Offset(ox, barTop + barH * 0.25f), Size(ex - ox, barH * 0.5f))
        }
        drawRoundRect(statusC, Offset(left, barTop), Size(w, barH), CornerRadius(3f, 3f), style = Stroke(1f))
        // baseline (thin bar under) and actual dates (ticks)
        if (showBaseline) {
            val bs = PlanCalc.parse(row.task.baselineStart)
            val be = PlanCalc.parse(row.task.baselineEnd)
            if (bs != null && be != null) drawRect(baseC, Offset(x(bs), barTop + barH + 3f), Size((x(be) + ppd - x(bs)).coerceAtLeast(2f), 3.5f))
        }
        PlanCalc.parse(row.task.actualStart)?.let { a -> drawLine(KarColors.Navy, Offset(x(a), barTop - 2f), Offset(x(a), barTop + barH + 2f), 2f) }
        PlanCalc.parse(row.task.actualEnd)?.let { a -> drawLine(KarColors.Navy, Offset(x(a) + ppd, barTop - 2f), Offset(x(a) + ppd, barTop + barH + 2f), 2f) }
        // resize handle hint
        drawLine(statusC, Offset(right - 2f, barTop + 2f), Offset(right - 2f, barTop + barH - 2f), 2f)
        @Suppress("UNUSED_VARIABLE") val t = textC
    }
}

@Suppress("unused")
private val ganttShape = RoundedCornerShape(4.dp)
