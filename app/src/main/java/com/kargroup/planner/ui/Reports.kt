package com.kargroup.planner.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// =============================================================================
// Report model
// =============================================================================

enum class ReportPeriod(val label: String) {
    WEEK("This week"), MONTH("This month"), LAST30("Last 30 days"), YEAR("This year");

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        WEEK -> today.with(java.time.DayOfWeek.MONDAY) to today
        MONTH -> today.withDayOfMonth(1) to today
        LAST30 -> today.minusDays(29) to today
        YEAR -> today.withDayOfYear(1) to today
    }
}

data class PersonStat(
    val name: String,
    val completed: Int,
    val onTime: Int,
    val late: Int,
    val open: Int,
    val overdue: Int,
)

data class ProjectStat(val name: String, val completed: Int, val open: Int, val overdue: Int)

data class Report(
    val from: LocalDate,
    val to: LocalDate,
    val completed: Int,
    val onTime: Int,
    val late: Int,
    val created: Int,
    val openNow: Int,
    val initiatedNow: Int,
    val overdueNow: Int,
    val avgDaysToComplete: Double?,
    val perBucket: List<Pair<String, Int>>,
    val people: List<PersonStat>,
    val projects: List<ProjectStat>,
    val overdueList: List<TaskEntity>,
    val completedList: List<TaskEntity>,
) {
    val onTimePct: Int? get() = if (completed == 0) null else (onTime * 100 / completed)
}

private fun dayOf(ms: Long): LocalDate = java.time.Instant.ofEpochMilli(ms).atZone(Ist.ZONE).toLocalDate()

private fun isOnTime(t: TaskEntity): Boolean {
    val done = t.completedAt ?: return true
    val target = targetMillis(t) ?: return true
    return done <= target
}

fun buildReport(
    tasks: List<TaskEntity>,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    period: ReportPeriod,
    today: LocalDate = Ist.today(),
): Report {
    val (from, to) = period.range(today)
    val todayStr = today.toString()
    val inRange: (Long?) -> Boolean = { ms -> ms != null && !dayOf(ms).isBefore(from) && !dayOf(ms).isAfter(to) }
    val completed = tasks.filter { it.done && inRange(it.completedAt) }
    val open = tasks.filter { !it.done }
    val overdue = open.filter { it.dueDate != null && it.dueDate < todayStr }
    val onTime = completed.count { isOnTime(it) }
    val avg = completed.mapNotNull { t -> t.completedAt?.let { (it - t.createdAt) / 86_400_000.0 } }
        .filter { it >= 0 }.takeIf { it.isNotEmpty() }?.average()

    val buckets: List<Pair<String, Int>> = if (period == ReportPeriod.YEAR) {
        val fmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        (1..today.monthValue).map { m ->
            val label = LocalDate.of(today.year, m, 1).format(fmt)
            label to completed.count { dayOf(it.completedAt!!).monthValue == m }
        }
    } else {
        val fmt = DateTimeFormatter.ofPattern("d", Locale.ENGLISH)
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            d.format(fmt) to completed.count { dayOf(it.completedAt!!) == d }
        }.toList()
    }

    fun key(n: String?) = n?.trim()?.ifBlank { null }?.uppercase()
    val names = (persons.map { it.name } + tasks.mapNotNull { it.assignee }).mapNotNull { key(it) }.distinct()
    val people = names.map { n ->
        val c = completed.filter { key(it.assignee) == n }
        val o = open.filter { key(it.assignee) == n }
        PersonStat(n, c.size, c.count { isOnTime(it) }, c.count { !isOnTime(it) }, o.size, o.count { it in overdue })
    }.filter { it.completed + it.open > 0 }.sortedWith(compareBy({ -it.overdue }, { -it.completed }, { it.name }))
        .let { list ->
            val un = completed.count { key(it.assignee) == null } to open.count { key(it.assignee) == null }
            if (un.first + un.second > 0) {
                val c = completed.filter { key(it.assignee) == null }
                list + PersonStat("UNASSIGNED", c.size, c.count { isOnTime(it) }, c.count { !isOnTime(it) }, un.second,
                    open.count { key(it.assignee) == null && it in overdue })
            } else list
        }
    val projectStats = projects.map { p ->
        ProjectStat(
            p.name,
            completed.count { it.projectId == p.id },
            open.count { it.projectId == p.id },
            overdue.count { it.projectId == p.id },
        )
    }.filter { it.completed + it.open > 0 }.sortedByDescending { it.open + it.completed }

    return Report(
        from = from, to = to,
        completed = completed.size, onTime = onTime, late = completed.size - onTime,
        created = tasks.count { inRange(it.createdAt) },
        openNow = open.size,
        initiatedNow = open.count { it.status == TaskStatus.INITIATED },
        overdueNow = overdue.size,
        avgDaysToComplete = avg,
        perBucket = buckets,
        people = people,
        projects = projectStats,
        overdueList = overdue.sortedBy { it.dueDate },
        completedList = completed.sortedByDescending { it.completedAt },
    )
}

// =============================================================================
// Screen
// =============================================================================

private val HeaderShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(
    tasks: List<TaskEntity>,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    wide: Boolean,
    onExportPdf: (ReportPeriod) -> Unit,
    onExportCsv: (ReportPeriod) -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(ReportPeriod.MONTH) }
    val report = remember(tasks, persons, projects, period) { buildReport(tasks, persons, projects, period) }
    val hPad = if (wide) 24.dp else 12.dp
    val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().depthShadow(10.dp, HeaderShape).background(KarBrush.SkyHeader, HeaderShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text("Reports", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            "${report.from.format(fmt)} – ${report.to.format(fmt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderButton("PDF", Icons.Outlined.PictureAsPdf) { onExportPdf(period) }
                        HeaderButton("Excel", Icons.Outlined.TableChart) { onExportCsv(period) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportPeriod.entries.forEach { p ->
                        Surface(
                            onClick = { period = p },
                            color = if (p == period) Color.White else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                p.label,
                                color = if (p == period) KarColors.SkyDeep else Color.White,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        }

        // KPI tiles
        item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (wide) 5 else 2,
            ) {
                val tile = Modifier.weight(1f)
                Kpi("Completed", report.completed.toString(), "${report.created} created in period", tile)
                Kpi("On time", report.onTimePct?.let { "$it%" } ?: "–", "${report.onTime} on time · ${report.late} late", tile)
                Kpi("Overdue now", report.overdueNow.toString(), "needs a reason or new date", tile, alert = report.overdueNow > 0)
                Kpi("Open now", report.openNow.toString(), "${report.initiatedNow} initiated", tile)
                Kpi("Avg. time to close", report.avgDaysToComplete?.let { "%.1f d".format(it) } ?: "–", "from creation to completion", tile)
            }
        }

        // Completions chart
        item {
            DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("Completed activities", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Text(
                        if (period == ReportPeriod.YEAR) "per month · tap a bar for the number" else "per day · tap a bar for the number",
                        style = MaterialTheme.typography.labelSmall,
                        color = KarColors.Slate,
                    )
                    Spacer(Modifier.height(12.dp))
                    CompletionBars(report.perBucket)
                }
            }
        }

        // People
        item {
            DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("By person", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Spacer(Modifier.height(8.dp))
                    TableRow(listOf("Person", "Done", "On time", "Late", "Open", "Overdue"), header = true)
                    if (report.people.isEmpty()) Text("No activity in this period", color = KarColors.Slate, modifier = Modifier.padding(8.dp))
                    report.people.forEach { p ->
                        HorizontalDivider(color = KarColors.Border)
                        TableRow(
                            listOf(p.name, "${p.completed}", "${p.onTime}", "${p.late}", "${p.open}", "${p.overdue}"),
                            alertCol = if (p.overdue > 0) 5 else -1,
                        )
                    }
                }
            }
        }

        // Projects
        if (report.projects.isNotEmpty()) {
            item {
                DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("By project", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                        Spacer(Modifier.height(8.dp))
                        TableRow(listOf("Project", "Done", "Open", "Overdue"), header = true)
                        report.projects.forEach { p ->
                            HorizontalDivider(color = KarColors.Border)
                            TableRow(listOf(p.name, "${p.completed}", "${p.open}", "${p.overdue}"), alertCol = if (p.overdue > 0) 3 else -1)
                        }
                    }
                }
            }
        }

        // Overdue list
        if (report.overdueList.isNotEmpty()) {
            item {
                DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Overdue now (${report.overdueList.size})", style = MaterialTheme.typography.titleSmall, color = KarColors.Red)
                        Spacer(Modifier.height(6.dp))
                        report.overdueList.take(25).forEach { t ->
                            HorizontalDivider(color = KarColors.Border)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${t.assignee ?: "Unassigned"} · target ${dueLabel(t)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KarColors.Slate,
                                    )
                                }
                                Surface(onClick = { onOpenTask(t) }, color = KarColors.SkyLight, shape = RoundedCornerShape(8.dp)) {
                                    Text("Open", color = KarColors.SkyDeep, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = KarColors.SkyDeep, modifier = Modifier.padding(end = 6.dp))
            Text(label, color = KarColors.SkyDeep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, modifier: Modifier, alert: Boolean = false) {
    DepthCard(modifier.widthIn(min = 140.dp), elevation = 4.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, letterSpacing = 0.8.sp)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (alert) KarColors.Red else KarColors.Navy)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 2)
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, alertCol: Int = -1) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            Text(
                c,
                modifier = if (i == 0) Modifier.weight(2.2f) else Modifier.weight(1f),
                style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (header || i == 0) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    header -> KarColors.Slate
                    i == alertCol -> KarColors.Red
                    else -> KarColors.Ink
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Single-series bar chart: one hue, thin bars with rounded tops on a baseline, tap to read a value. */
@Composable
private fun CompletionBars(data: List<Pair<String, Int>>) {
    var selected by remember(data) { mutableStateOf(data.indexOfLast { it.second > 0 }.takeIf { it >= 0 }) }
    val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val bar = KarColors.SkyMid
    val grid = KarColors.Border
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(data) {
                    detectTapGestures { pos ->
                        if (data.isNotEmpty()) {
                            val slot = size.width.toFloat() / data.size
                            selected = (pos.x / slot).toInt().coerceIn(0, data.lastIndex)
                        }
                    }
                }
            ) {
                val n = data.size.coerceAtLeast(1)
                val slot = size.width / n
                val w = (slot * 0.6f).coerceAtMost(28.dp.toPx())
                val top = 18.dp.toPx()
                val h = size.height - top
                // recessive grid: baseline + half line
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                drawLine(grid, Offset(0f, top + h / 2), Offset(size.width, top + h / 2), strokeWidth = 1f)
                data.forEachIndexed { i, (_, v) ->
                    if (v > 0) {
                        val bh = h * v / max
                        val x = slot * i + (slot - w) / 2
                        val isSel = i == selected
                        drawRoundRect(
                            color = if (selected == null || isSel) bar else bar.copy(alpha = 0.45f),
                            topLeft = Offset(x, size.height - bh),
                            size = Size(w, bh),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                        // square off the bottom so the bar sits on the baseline
                        drawRect(
                            color = if (selected == null || isSel) bar else bar.copy(alpha = 0.45f),
                            topLeft = Offset(x, size.height - minOf(bh, 4.dp.toPx())),
                            size = Size(w, minOf(bh, 4.dp.toPx())),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val step = when {
                data.size > 20 -> 5
                data.size > 10 -> 2
                else -> 1
            }
            data.forEachIndexed { i, (label, _) ->
                Text(
                    if (i % step == 0 || i == data.lastIndex) label else "",
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    color = KarColors.SlateLight,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        selected?.let { i ->
            val (label, v) = data[i]
            Text(
                "$label: $v completed",
                style = MaterialTheme.typography.labelMedium,
                color = KarColors.Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}

// =============================================================================
// Export: Excel (CSV) and PDF
// =============================================================================

object ReportExport {

    private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    fun csv(report: Report, tasks: List<TaskEntity>, projects: List<ProjectEntity>): String {
        val pName = projects.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append('﻿') // lets Excel read UTF-8 correctly
        sb.append("KAR PLANNER REPORT,${report.from} TO ${report.to}\n\n")
        sb.append("SUMMARY\n")
        sb.append("COMPLETED,${report.completed}\nON TIME,${report.onTime}\nLATE,${report.late}\n")
        sb.append("ON TIME %,${report.onTimePct ?: ""}\nOPEN NOW,${report.openNow}\nOVERDUE NOW,${report.overdueNow}\n\n")
        sb.append("BY PERSON\nPERSON,COMPLETED,ON TIME,LATE,OPEN,OVERDUE\n")
        report.people.forEach { sb.append("${csvCell(it.name)},${it.completed},${it.onTime},${it.late},${it.open},${it.overdue}\n") }
        sb.append("\nACTIVITIES\nTITLE,PROJECT,ASSIGNED TO,PRIORITY,STATUS,TARGET DATE,TARGET TIME,ESTIMATE (MIN),COMPLETED AT,REMARKS\n")
        val list = report.completedList + tasks.filter { !it.done }
        list.forEach { t ->
            sb.append(
                listOf(
                    t.text, t.projectId?.let { pName[it] } ?: "", t.assignee ?: "", t.priority ?: "standard",
                    TaskStatus.label(t.effectiveStatusLabel()), t.dueDate ?: "", t.dueTime ?: "",
                    t.estimatedMinutes?.toString() ?: "", t.completedAt?.let { Ist.formatStamp(it) } ?: "",
                    t.completionRemarks ?: "",
                ).joinToString(",") { csvCell(it.uppercase()) }
            ).append('\n')
        }
        return sb.toString()
    }

    private fun TaskEntity.effectiveStatusLabel(): String = when {
        done -> TaskStatus.COMPLETED
        status == TaskStatus.INITIATED -> TaskStatus.INITIATED
        else -> TaskStatus.NOT_STARTED
    }

    /** A4 PDF: header, KPIs, by-person table and overdue list. */
    fun pdf(context: Context, uri: Uri, report: Report) {
        val doc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 40f
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var c = page.canvas
        var y = margin

        val title = Paint().apply { color = 0xFF0284C7.toInt(); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val h2 = Paint().apply { color = 0xFF0B1E3F.toInt(); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val body = Paint().apply { color = 0xFF0F172A.toInt(); textSize = 10f; isAntiAlias = true }
        val muted = Paint().apply { color = 0xFF64748B.toInt(); textSize = 9f; isAntiAlias = true }
        val red = Paint(body).apply { color = 0xFFEF4444.toInt() }
        val line = Paint().apply { color = 0xFFD6E8F7.toInt(); strokeWidth = 1f }
        val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

        fun newPageIfNeeded(need: Float) {
            if (y + need > pageH - margin) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                c = page.canvas
                y = margin
            }
        }
        fun text(s: String, x: Float, p: Paint) = c.drawText(s.uppercase(), x, y, p)

        text("KAR GROUP · PLANNER REPORT", margin, title); y += 18f
        text("${report.from.format(fmt)} – ${report.to.format(fmt)} · generated ${Ist.formatStamp(System.currentTimeMillis())} IST", margin, muted); y += 26f

        text("Summary", margin, h2); y += 16f
        listOf(
            "Completed" to "${report.completed}",
            "On time" to (report.onTimePct?.let { "$it% (${report.onTime} on time, ${report.late} late)" } ?: "–"),
            "Open now" to "${report.openNow} (${report.initiatedNow} initiated)",
            "Overdue now" to "${report.overdueNow}",
            "Average time to close" to (report.avgDaysToComplete?.let { "%.1f days".format(it) } ?: "–"),
        ).forEach { (k, v) ->
            text(k, margin, muted); text(v, margin + 170f, body); y += 15f
        }
        y += 12f

        text("By person", margin, h2); y += 16f
        val cols = floatArrayOf(margin, margin + 210f, margin + 270f, margin + 330f, margin + 390f, margin + 450f)
        listOf("Person", "Done", "On time", "Late", "Open", "Overdue").forEachIndexed { i, h -> text(h, cols[i], muted) }
        y += 6f; c.drawLine(margin, y, pageW - margin, y, line); y += 12f
        report.people.forEach { p ->
            newPageIfNeeded(16f)
            listOf(p.name.take(32), "${p.completed}", "${p.onTime}", "${p.late}", "${p.open}", "${p.overdue}").forEachIndexed { i, v ->
                text(v, cols[i], if (i == 5 && p.overdue > 0) red else body)
            }
            y += 15f
        }
        y += 12f

        if (report.overdueList.isNotEmpty()) {
            newPageIfNeeded(40f)
            text("Overdue activities", margin, h2); y += 16f
            report.overdueList.forEach { t ->
                newPageIfNeeded(28f)
                text("• " + t.text.take(80), margin, body); y += 12f
                text("${t.assignee ?: "Unassigned"} · target ${t.dueDate}${t.dueTime?.let { " $it" } ?: ""}", margin + 10f, muted); y += 14f
            }
        }
        if (report.completedList.isNotEmpty()) {
            newPageIfNeeded(40f)
            y += 8f
            text("Completed in period", margin, h2); y += 16f
            report.completedList.take(200).forEach { t ->
                newPageIfNeeded(28f)
                text("✓ " + t.text.take(80), margin, body); y += 12f
                text(
                    "${t.assignee ?: "Unassigned"} · ${t.completedAt?.let { Ist.formatStamp(it) } ?: ""}" +
                        (t.completionRemarks?.let { " · ${it.take(70)}" } ?: ""),
                    margin + 10f, muted,
                ); y += 14f
            }
        }
        doc.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("Could not write the PDF")
        doc.close()
    }
}

// =============================================================================
// Share a person's open activities (WhatsApp / e-mail)
// =============================================================================

object ShareUpdate {
    fun text(person: String?, tasks: List<TaskEntity>): String {
        val today = Ist.todayStr()
        val mine = tasks.filter { !it.done && sameAssignee(it.assignee, person) }
        val overdue = mine.filter { it.dueDate != null && it.dueDate < today }.sortedBy { it.dueDate }
        val initiated = mine.filter { it !in overdue && it.status == TaskStatus.INITIATED }.sortedWith(rootOrder)
        val notStarted = mine.filter { it !in overdue && it.status != TaskStatus.INITIATED }.sortedWith(rootOrder)
        val sb = StringBuilder()
        sb.append("*${(person ?: "Unassigned").uppercase()} – OPEN ACTIVITIES (${Ist.formatFull(today).uppercase()})*\n")
        fun section(title: String, list: List<TaskEntity>) {
            if (list.isEmpty()) return
            sb.append("\n*$title (${list.size})*\n")
            list.forEachIndexed { i, t ->
                sb.append("${i + 1}. ${t.text.uppercase()}")
                if (t.dueDate != null) sb.append(" — ${dueLabel(t).uppercase()}")
                sb.append('\n')
            }
        }
        section("OVERDUE", overdue)
        section("INITIATED", initiated)
        section("NOT STARTED", notStarted)
        if (mine.isEmpty()) sb.append("\nNO OPEN ACTIVITIES. 👍\n")
        sb.append("\n— SENT FROM KAR PLANNER")
        return sb.toString()
    }

    fun whatsApp(context: Context, phone: String?, message: String) {
        val digits = phone?.filter { it.isDigit() }?.let { d -> if (d.length == 10) "91$d" else d }
        val intent = if (!digits.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + Uri.encode(message)))
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message).setPackage("com.whatsapp")
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "Share update")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun email(context: Context, email: String?, subject: String, message: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (email ?: "")))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, message)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message), "Share update")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
