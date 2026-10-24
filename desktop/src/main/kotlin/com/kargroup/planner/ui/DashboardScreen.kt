package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import java.time.DayOfWeek
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Desktop home page: today at a glance, workload and trends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    tasks: List<TaskEntity>,
    meetings: List<MeetingMinuteEntity>,
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    settings: PlannerSettings,
    manual: Boolean,
    myName: String,
    emailsWaiting: Int,
    today: String,
    onNewActivity: () -> Unit,
    onNewMeeting: () -> Unit,
    onOpenEmails: () -> Unit,
    onOpenTask: (TaskEntity) -> Unit,
    onOpenMeeting: (MeetingMinuteEntity) -> Unit,
    onNavigate: (Screen) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
) {
    val todayDate = Ist.parse(today) ?: Ist.today()
    val open = remember(tasks) { tasks.filter { !it.done } }
    val overdue = remember(open, today) { open.filter { it.dueDate != null && it.dueDate < today }.sortedBy { it.dueDate } }
    val dueToday = remember(open, today) { open.filter { it.dueDate == today } }
    val weekEnd = todayDate.plusDays(6).toString()
    val dueWeek = remember(open, today) { open.filter { it.dueDate != null && it.dueDate in today..weekEnd } }
    val weekAgo = todayDate.minusDays(6)
    val doneWeek = remember(tasks, today) {
        tasks.count { t -> t.done && t.completedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(Ist.ZONE).toLocalDate() }?.let { !it.isBefore(weekAgo) } == true }
    }
    val report = remember(tasks, persons, projects) { buildReport(tasks, persons, projects, ReportPeriod.LAST30) }
    val plan = remember(tasks, settings, meetings, manual) { buildSchedule(TaskIndex(tasks), manual, settings, meetings) }
    val todaysPlan = plan.items.filter { it.day == today }
    val meetingsToday = meetings.filter { it.meetingDate == today }.sortedBy { it.meetingTime }
    val hour = Ist.now().hour
    val greeting = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    val first = myName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoCol = maxWidth > 980.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------------------------------------------------------------- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting + (first?.let { ", $it" } ?: ""), style = MaterialTheme.typography.headlineSmall, color = KarColors.Navy)
                    Text(
                        Ist.formatFull(today) + " · " + if (settings.isWorkingDay(todayDate)) "working day" else "day off",
                        style = MaterialTheme.typography.bodySmall, color = KarColors.Slate,
                    )
                }
                QuickAction("New activity", Icons.Outlined.Add, primary = true, onClick = onNewActivity)
                Spacer(Modifier.width(8.dp))
                QuickAction("New meeting", Icons.Outlined.Groups, onClick = onNewMeeting)
                Spacer(Modifier.width(8.dp))
                QuickAction(if (emailsWaiting > 0) "E-mails to review ($emailsWaiting)" else "Outlook e-mails", Icons.Outlined.Email, onClick = onOpenEmails)
            }

            // ------------------------------------------------------------------ KPIs
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = if (twoCol) 6 else 3,
            ) {
                val m = Modifier.weight(1f)
                Kpi("Open", "${open.size}", "${open.count { it.status == com.kargroup.planner.data.TaskStatus.INITIATED }} initiated", KarColors.SkyDeep, m) { onNavigate(Screen.AllTasks) }
                Kpi("Overdue", "${overdue.size}", if (overdue.isEmpty()) "all on track" else "needs attention", if (overdue.isEmpty()) KarColors.Green else KarColors.Red, m) { onNavigate(Screen.Overdue) }
                Kpi("Due today", "${dueToday.size}", "${meetingsToday.size} meeting(s) today", KarColors.Amber, m) { onNavigate(Screen.Today) }
                Kpi("Due in 7 days", "${dueWeek.size}", "incl. today", KarColors.Navy2, m) { onNavigate(Screen.Upcoming) }
                Kpi("Completed", "$doneWeek", "last 7 days", KarColors.Green, m) { onNavigate(Screen.Completed) }
                Kpi("On time", report.onTimePct?.let { "$it%" } ?: "–", "last 30 days", KarColors.SkyDeep, m) { onNavigate(Screen.Reports) }
            }

            // ------------------------------------------------------------- panels
            val left: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel("Today's agenda", "${meetingsToday.size} meetings · ${todaysPlan.size} scheduled", actionLabel = "Open scheduler", onAction = { onNavigate(Screen.Schedule) }) {
                        if (meetingsToday.isEmpty() && todaysPlan.isEmpty()) EmptyLine("Nothing booked for today")
                        meetingsToday.forEach { mt ->
                            AgendaRow(formatTime12(mt.meetingTime), mt.title, "Meeting · ${mt.attendees.size} attendees", Color(0xFF7C3AED)) { onOpenMeeting(mt) }
                        }
                        todaysPlan.take(10).forEach { si ->
                            AgendaRow(
                                formatMillisTime24(si.startMillis),
                                si.task.text,
                                listOfNotNull(si.task.assignee, si.task.statusNote).joinToString(" · ").ifBlank { formatMinutes(((si.endMillis - si.startMillis) / 60000).toInt()) },
                                priorityColor(si.task.priority) ?: KarColors.SkyMid,
                            ) { onOpenTask(si.task) }
                        }
                    }
                    Panel("Overdue", if (overdue.isEmpty()) "Nothing overdue" else "${overdue.size} activities past target", actionLabel = "See all", onAction = { onNavigate(Screen.Overdue) }) {
                        if (overdue.isEmpty()) EmptyLine("Great — nothing is overdue")
                        overdue.take(8).forEach { t ->
                            Row(Modifier.fillMaxWidth().clickable { onOpenTask(t) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(KarColors.Red))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.text, style = MaterialTheme.typography.bodyMedium, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${t.assignee ?: "Unassigned"} · ${dueLabel(t)}", style = MaterialTheme.typography.labelSmall, color = KarColors.Red)
                                }
                                TextButton(onClick = { onReschedule(t) }) { Text("Reschedule", style = MaterialTheme.typography.labelMedium) }
                            }
                            HorizontalDivider(color = KarColors.Border)
                        }
                    }
                }
            }
            val right: @Composable (Modifier) -> Unit = { mod ->
                Column(mod, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Panel("Workload by person", "Open activities · red = overdue", actionLabel = "By person", onAction = { onNavigate(Screen.People) }) {
                        val rows = open.groupBy { it.assignee?.trim()?.uppercase()?.ifBlank { null } ?: "UNASSIGNED" }
                            .map { (n, l) -> Triple(n, l.size, l.count { it in overdue }) }
                            .sortedByDescending { it.second }.take(8)
                        val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                        if (rows.isEmpty()) EmptyLine("No open activities")
                        rows.forEach { (n, c, o) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onNavigate(Screen.Person(if (n == "UNASSIGNED") null else n)) }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(n, style = MaterialTheme.typography.labelMedium, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(150.dp))
                                Row(Modifier.weight(1f).height(14.dp)) {
                                    val frac = c.toFloat() / max
                                    Row(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))) {
                                        if (c - o > 0) Box(Modifier.weight((c - o).toFloat()).fillMaxHeight().background(KarColors.SkyMid))
                                        if (o > 0) Box(Modifier.weight(o.toFloat()).fillMaxHeight().background(KarColors.Red))
                                    }
                                }
                                Text("$c" + if (o > 0) " ($o)" else "", style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.padding(start = 8.dp).width(48.dp))
                            }
                        }
                    }
                    Panel("Due in the next 7 days", "Open activities by target date", actionLabel = null, onAction = null) {
                        val days = (0..6).map { todayDate.plusDays(it.toLong()) }
                        val counts = days.map { d -> open.count { it.dueDate == d.toString() } }
                        BarStrip(days.map { d -> if (d == todayDate) "Today" else d.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.ENGLISH) }, counts, KarColors.SkyMid, highlightFirst = true)
                    }
                    Panel("Completed per week", "Last 8 weeks", actionLabel = "Reports", onAction = { onNavigate(Screen.Reports) }) {
                        val monday = todayDate.with(DayOfWeek.MONDAY)
                        val weeks = (7 downTo 0).map { monday.minusWeeks(it.toLong()) }
                        val counts = weeks.map { w ->
                            tasks.count { t ->
                                t.done && t.completedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(Ist.ZONE).toLocalDate() }
                                    ?.let { !it.isBefore(w) && it.isBefore(w.plusDays(7)) } == true
                            }
                        }
                        BarStrip(weeks.map { "${it.dayOfMonth}/${it.monthValue}" }, counts, KarColors.Green, highlightFirst = false)
                    }
                }
            }
            if (twoCol) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    left(Modifier.weight(1.15f))
                    right(Modifier.weight(1f))
                }
            } else {
                left(Modifier.fillMaxWidth())
                right(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (primary) KarColors.SkyDeep else KarColors.Card,
        shape = RoundedCornerShape(8.dp),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, KarColors.Border),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (primary) Color.White else KarColors.SkyDeep, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (primary) Color.White else KarColors.Ink, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    DepthCard(modifier, elevation = 2.dp, shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, letterSpacing = 0.6.sp)
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KarColors.Navy)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Panel(title: String, subtitle: String, actionLabel: String?, onAction: (() -> Unit)?, content: @Composable () -> Unit) {
    DepthCard(Modifier.fillMaxWidth(), elevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                }
                if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = KarColors.SlateLight, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun AgendaRow(time: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.labelMedium, color = KarColors.Slate, modifier = Modifier.width(72.dp))
        Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = KarColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Single-series bar strip: one hue, value printed above each bar, first bar optionally emphasised. */
@Composable
private fun BarStrip(labels: List<String>, values: List<Int>, color: Color, highlightFirst: Boolean) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text("$v", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth(0.6f)
                        .height((80f * v / max).coerceAtLeast(if (v > 0) 3f else 1f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (v == 0) KarColors.Border else if (!highlightFirst || i == 0) color else color.copy(alpha = 0.55f))
                )
            }
        }
    }
    HorizontalDivider(color = KarColors.Border)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1) }
    }
}
