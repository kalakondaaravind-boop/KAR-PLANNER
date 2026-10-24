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

// Tablet-only exports: PDF via android.graphics.pdf and sharing via WhatsApp / e-mail apps.

object ReportExport {

    fun csv(report: Report, tasks: List<TaskEntity>, projects: List<ProjectEntity>): String = ReportCsv.csv(report, tasks, projects)

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
