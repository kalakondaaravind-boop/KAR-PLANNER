package com.kargroup.planner.desktop

import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.ui.TaskIndex
import com.kargroup.planner.ui.buildSchedule
import com.kargroup.planner.ui.formatMillisTime24
import com.kargroup.planner.ui.formatMinutes
import com.kargroup.planner.ui.formatTime12
import com.kargroup.planner.ui.rootOrder
import com.kargroup.planner.ui.sameAssignee
import com.kargroup.planner.util.Ist
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Everything the e-mails are built from. */
data class PlannerData(
    val tasks: List<TaskEntity>,
    val persons: List<PersonEntity>,
    val projects: List<ProjectEntity>,
    val meetings: List<MeetingMinuteEntity>,
)

/**
 * Builds Outlook-friendly HTML e-mails (tables with inline styles, Segoe UI) in the planner's
 * house style. All text is in capital letters, like the app.
 */
object EmailComposer {

    private const val SKY = "#0284C7"
    private const val NAVY = "#0B1E3F"
    private const val SLATE = "#64748B"
    private const val RED = "#DC2626"
    private const val AMBER = "#B45309"
    private const val GREEN = "#047857"
    private const val BORDER = "#D6E8F7"

    // ------------------------------------------------------------------ helpers

    private fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Upper-cased, HTML-escaped, line breaks kept. */
    private fun u(s: String?): String = esc(s?.uppercase()).replace("\n", "<br>")

    fun emailOf(name: String?, persons: List<PersonEntity>): String? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        if (n.contains('@')) return n.lowercase()
        return persons.firstOrNull { sameAssignee(it.name, n) }?.email?.trim()?.takeIf { it.contains('@') }?.lowercase()
    }

    private fun projectName(t: TaskEntity, projects: List<ProjectEntity>) = t.projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }

    private fun target(t: TaskEntity): String =
        if (t.dueDate == null) "—" else Ist.formatFull(t.dueDate) + (t.dueTime?.let { " · " + formatTime12(it) } ?: "")

    private fun priorityLabel(p: String?): String = when (p) {
        Priority.VERY_HIGH -> "VERY HIGH"
        Priority.HIGH -> "HIGH"
        Priority.MEDIUM -> "MEDIUM"
        else -> "STANDARD"
    }

    private fun priorityColor(p: String?): String = when (p) {
        Priority.VERY_HIGH -> "#9F1239"
        Priority.HIGH -> RED
        Priority.MEDIUM -> AMBER
        else -> SLATE
    }

    private fun statusLabel(t: TaskEntity): String = when {
        t.done -> "COMPLETED"
        t.status == TaskStatus.INITIATED -> "INITIATED"
        else -> "NOT STARTED"
    }

    private fun page(title: String, subtitle: String, body: String, es: EmailSettings, signed: Boolean = true): String {
        val sign = if (!signed) "" else buildString {
            append("<p style=\"margin:24px 0 0 0;font-size:13px;color:$NAVY\">REGARDS,<br><b>")
            append(u(es.myName.ifBlank { "KAR PLANNER" }))
            append("</b>")
            if (es.signature.isNotBlank()) append("<br><span style=\"color:$SLATE\">${u(es.signature)}</span>")
            append("</p>")
        }
        return """
<html><body style="margin:0;padding:0;background:#F0F7FD">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F0F7FD;font-family:'Segoe UI',Calibri,Arial,sans-serif">
<tr><td align="center" style="padding:20px 10px">
<table role="presentation" width="720" cellpadding="0" cellspacing="0" style="max-width:720px;width:100%;background:#FFFFFF;border:1px solid $BORDER">
<tr><td style="background:$SKY;padding:18px 24px;color:#FFFFFF">
<div style="font-size:11px;letter-spacing:3px;font-weight:bold">KAR GROUP · PLANNER</div>
<div style="font-size:20px;font-weight:bold;margin-top:4px">${u(title)}</div>
<div style="font-size:12px;margin-top:2px">${u(subtitle)}</div>
</td></tr>
<tr><td style="padding:20px 24px;font-size:13px;color:#0F172A">
$body
$sign
</td></tr>
<tr><td style="background:#F8FBFE;border-top:1px solid $BORDER;padding:10px 24px;font-size:10px;color:$SLATE">
SENT FROM KAR PLANNER · ${u(Ist.formatStamp(System.currentTimeMillis()))} IST
</td></tr>
</table></td></tr></table></body></html>
""".trimIndent()
    }

    private fun heading(text: String, color: String = SKY): String =
        "<p style=\"margin:20px 0 6px 0;font-size:12px;font-weight:bold;letter-spacing:1px;color:$color\">${u(text)}</p>"

    private fun para(text: String): String = "<p style=\"margin:0 0 10px 0;font-size:13px\">${u(text)}</p>"

    private fun table(headers: List<String>, rows: List<List<String>>): String {
        val th = headers.joinToString("") {
            "<th align=\"left\" style=\"padding:7px 8px;background:#E0F2FE;color:$NAVY;font-size:11px;border:1px solid $BORDER\">${u(it)}</th>"
        }
        val tr = rows.joinToString("") { r ->
            "<tr>" + r.joinToString("") { "<td valign=\"top\" style=\"padding:7px 8px;font-size:12px;border:1px solid $BORDER\">$it</td>" } + "</tr>"
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse\"><tr>$th</tr>$tr</table>"
    }

    private fun colored(text: String, color: String, bold: Boolean = true) =
        "<span style=\"color:$color;${if (bold) "font-weight:bold" else ""}\">${u(text)}</span>"

    private fun taskRows(list: List<TaskEntity>, projects: List<ProjectEntity>, today: String, withOwner: Boolean): List<List<String>> =
        list.mapIndexed { i, t ->
            val overdue = !t.done && t.dueDate != null && t.dueDate < today
            buildList {
                add("${i + 1}")
                add("<b>${u(t.text)}</b>" + (projectName(t, projects)?.let { "<br><span style=\"color:$SLATE;font-size:11px\">${u(it)}</span>" } ?: ""))
                if (withOwner) add(u(t.assignee ?: "UNASSIGNED"))
                add(if (overdue) colored("OVERDUE · " + target(t), RED) else u(target(t)))
                add(colored(priorityLabel(t.priority), priorityColor(t.priority)))
                add(u(statusLabel(t)))
            }
        }

    private fun taskHeaders(withOwner: Boolean) =
        if (withOwner) listOf("#", "Activity", "Owner", "Target", "Priority", "Status")
        else listOf("#", "Activity", "Target", "Priority", "Status")

    private fun joinAddresses(list: Collection<String>) = list.filter { it.isNotBlank() }.distinct().joinToString("; ")

    // ---------------------------------------------------------------------- MoM

    fun mom(m: MeetingMinuteEntity, data: PlannerData, es: EmailSettings, key: String = "mom:${m.id}"): EmailDraft {
        val names = (m.attendees + m.actionItems.map { it.assignee }).map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val missing = names.filter { emailOf(it, data.persons) == null && !sameAssignee(it, es.myName) }
        val to = joinAddresses(names.mapNotNull { emailOf(it, data.persons) }.filter { !it.equals(es.myEmail, true) })
        val cc = if (es.ccMe) es.myEmail else ""
        val project = m.projectId?.let { id -> data.projects.firstOrNull { it.id == id }?.name }
        val today = Ist.todayStr()

        val body = buildString {
            append(para("Dear all,"))
            append(para("Please find below the minutes of the meeting held on ${Ist.formatFull(m.meetingDate)} and the action points agreed. Kindly complete your actions by the target dates."))
            append(
                table(
                    listOf("Meeting", "Details"),
                    listOfNotNull(
                        listOf(u("Title"), "<b>${u(m.title)}</b>"),
                        listOf(u("Date & time"), u("${Ist.formatFull(m.meetingDate)} · ${formatTime12(m.meetingTime)} (${formatMinutes(m.durationMinutes)})")),
                        project?.let { listOf(u("Project"), u(it)) },
                        listOf(u("Attendees"), u(m.attendees.joinToString(", ").ifBlank { "—" })),
                    ),
                )
            )
            if (m.agenda.isNotBlank()) { append(heading("Agenda")); append(para(m.agenda)) }
            if (m.discussionNotes.isNotBlank()) { append(heading("Discussion")); append(para(m.discussionNotes)) }
            if (m.keyDecisions.any { it.isNotBlank() }) {
                append(heading("Key decisions"))
                append("<ol style=\"margin:0 0 10px 18px;padding:0;font-size:13px\">")
                m.keyDecisions.filter { it.isNotBlank() }.forEach { append("<li style=\"margin-bottom:4px\">${u(it)}</li>") }
                append("</ol>")
            }
            val items = m.actionItems.filter { it.description.isNotBlank() }
            if (items.isNotEmpty()) {
                append(heading("Action points"))
                append(
                    table(
                        listOf("#", "Action", "Owner", "Target date", "Status"),
                        items.mapIndexed { i, a ->
                            val overdue = !a.completed && a.dueDate != null && a.dueDate < today
                            listOf(
                                "${i + 1}",
                                "<b>${u(a.description)}</b>",
                                u(a.assignee.ifBlank { "Unassigned" }),
                                if (a.dueDate == null) "—" else if (overdue) colored("OVERDUE · " + Ist.formatFull(a.dueDate), RED) else u(Ist.formatFull(a.dueDate)),
                                if (a.completed) colored("COMPLETED", GREEN) else u("OPEN"),
                            )
                        },
                    )
                )
            }
        }
        val subject = "MOM: ${m.title.uppercase()} – ${Ist.formatFull(m.meetingDate).uppercase()}"
        return EmailDraft(
            kind = EmailKind.MOM,
            key = key,
            to = to,
            cc = cc,
            subject = subject,
            html = page("Minutes of Meeting", "${m.title} · ${Ist.formatFull(m.meetingDate)}", body, es),
            summary = "${m.actionItems.count { it.description.isNotBlank() }} ACTION POINTS · ${names.size} PEOPLE",
            warnings = buildList {
                if (missing.isNotEmpty()) add("NO E-MAIL FOR: " + missing.joinToString(", ").uppercase() + " — ADD IT UNDER BY PERSON")
                if (to.isBlank()) add("NO RECIPIENTS YET — ADD E-MAIL ADDRESSES OR TYPE THEM IN 'TO'")
            },
        )
    }

    // -------------------------------------------------------------- reminders

    /** One reminder per person with open actions that are overdue or due within [EmailSettings.reminderDaysAhead] days. */
    fun reminders(data: PlannerData, es: EmailSettings, today: LocalDate = Ist.today()): Pair<List<EmailDraft>, List<String>> {
        val todayStr = today.toString()
        val until = today.plusDays(es.reminderDaysAhead.toLong()).toString()
        val open = data.tasks.filter { !it.done && it.dueDate != null && !it.assignee.isNullOrBlank() }
        val relevant = open.filter { (es.remindOverdue && it.dueDate!! < todayStr) || (it.dueDate!! in todayStr..until) }
        val byPerson = relevant.groupBy { it.assignee!!.trim().uppercase() }
        val missing = ArrayList<String>()
        val drafts = byPerson.mapNotNull { (name, list) ->
            if (sameAssignee(name, es.myName)) return@mapNotNull null // you get the daily digest instead
            val email = emailOf(name, data.persons)
            if (email == null) { missing += name; return@mapNotNull null }
            if (email.equals(es.myEmail, true)) return@mapNotNull null
            val overdue = list.filter { it.dueDate!! < todayStr }.sortedBy { it.dueDate }
            val dueToday = list.filter { it.dueDate == todayStr }.sortedWith(rootOrder)
            val soon = list.filter { it.dueDate!! > todayStr }.sortedBy { it.dueDate }
            val first = name.split(" ").first()
            val body = buildString {
                append(para("Dear $first,"))
                append(para("This is a friendly reminder of the action points assigned to you. Please update the status or let me know if a target date needs to change."))
                if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(table(taskHeaders(false), taskRows(overdue, data.projects, todayStr, false))) }
                if (dueToday.isNotEmpty()) { append(heading("Due today (${dueToday.size})", AMBER)); append(table(taskHeaders(false), taskRows(dueToday, data.projects, todayStr, false))) }
                if (soon.isNotEmpty()) { append(heading("Coming up (${soon.size})")); append(table(taskHeaders(false), taskRows(soon, data.projects, todayStr, false))) }
            }
            val counts = listOfNotNull(
                overdue.size.takeIf { it > 0 }?.let { "$it OVERDUE" },
                dueToday.size.takeIf { it > 0 }?.let { "$it DUE TODAY" },
                soon.size.takeIf { it > 0 }?.let { "$it COMING UP" },
            ).joinToString(" · ")
            EmailDraft(
                kind = EmailKind.REMINDER,
                key = "rem:$email:$todayStr",
                to = email,
                cc = if (es.ccMe) es.myEmail else "",
                subject = "REMINDER: ACTION POINTS – $counts",
                html = page("Action point reminder", "For $name · ${Ist.formatFull(todayStr)}", body, es),
                summary = "$name · $counts",
            )
        }
        return drafts to missing
    }

    // ------------------------------------------------------------- daily digest

    fun digest(data: PlannerData, es: EmailSettings, ps: PlannerSettings, manualOrder: Boolean, today: LocalDate = Ist.today()): EmailDraft {
        val todayStr = today.toString()
        val tomorrowStr = today.plusDays(1).toString()
        val index = TaskIndex(data.tasks)
        val plan = buildSchedule(index, manualOrder, ps, data.meetings)
        val todays = plan.items.filter { it.day == todayStr }
        val meetingsToday = data.meetings.filter { it.meetingDate == todayStr }.sortedBy { it.meetingTime }
        val open = data.tasks.filter { !it.done }
        val overdue = open.filter { it.dueDate != null && it.dueDate < todayStr }.sortedBy { it.dueDate }
        val dueToday = open.filter { it.dueDate == todayStr }.sortedWith(rootOrder)
        val dueTomorrow = open.filter { it.dueDate == tomorrowStr }.sortedWith(rootOrder)
        val plannedMin = todays.sumOf { ((it.endMillis - it.startMillis) / 60_000L).toInt() }

        val kpi = { label: String, value: String, color: String ->
            "<td align=\"center\" style=\"padding:10px;border:1px solid $BORDER\"><div style=\"font-size:22px;font-weight:bold;color:$color\">${esc(value)}</div><div style=\"font-size:10px;color:$SLATE;letter-spacing:1px\">${u(label)}</div></td>"
        }
        val body = buildString {
            append(para("Good morning${if (es.myName.isNotBlank()) " " + es.myName.split(" ").first() else ""}, here is your plan for today."))
            append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse\"><tr>")
            append(kpi("Overdue", "${overdue.size}", if (overdue.isEmpty()) GREEN else RED))
            append(kpi("Due today", "${dueToday.size}", AMBER))
            append(kpi("Meetings", "${meetingsToday.size}", SKY))
            append(kpi("Planned", formatMinutes(plannedMin).uppercase(), NAVY))
            append("</tr></table>")
            if (meetingsToday.isNotEmpty()) {
                append(heading("Meetings today"))
                append(table(listOf("Time", "Meeting", "Attendees"), meetingsToday.map {
                    listOf(u(formatTime12(it.meetingTime)), "<b>${u(it.title)}</b>", u(it.attendees.joinToString(", ")))
                }))
            }
            if (todays.isNotEmpty()) {
                append(heading("Today's schedule"))
                append(table(listOf("Time", "Activity", "Owner", "Target", "Priority"), todays.map { si ->
                    val t = si.task
                    listOf(
                        u(formatMillisTime24(si.startMillis) + "–" + formatMillisTime24(si.endMillis)),
                        "<b>${u(t.text)}</b>" + (si.path?.let { "<br><span style=\"color:$SLATE;font-size:11px\">${u(it)}</span>" } ?: ""),
                        u(t.assignee ?: "—"),
                        if (t.dueDate != null && t.dueDate < todayStr) colored("OVERDUE · " + target(t), RED) else u(target(t)),
                        colored(priorityLabel(t.priority), priorityColor(t.priority)),
                    )
                }))
            }
            if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(table(taskHeaders(true), taskRows(overdue, data.projects, todayStr, true))) }
            if (dueToday.isNotEmpty()) { append(heading("Due today (${dueToday.size})", AMBER)); append(table(taskHeaders(true), taskRows(dueToday, data.projects, todayStr, true))) }
            if (dueTomorrow.isNotEmpty()) { append(heading("Due tomorrow (${dueTomorrow.size})")); append(table(taskHeaders(true), taskRows(dueTomorrow, data.projects, todayStr, true))) }
            if (todays.isEmpty() && overdue.isEmpty() && dueToday.isEmpty()) append(para("Nothing scheduled today. 👍"))
        }
        return EmailDraft(
            kind = EmailKind.DIGEST,
            key = "digest:$todayStr",
            to = es.myEmail,
            subject = "KAR PLANNER – MY DAY – ${Ist.formatFull(todayStr).uppercase()} (${overdue.size} OVERDUE · ${dueToday.size} DUE TODAY)",
            html = page("My day", Ist.formatFull(todayStr), body, es, signed = false),
            summary = "${todays.size} SCHEDULED · ${meetingsToday.size} MEETINGS · ${overdue.size} OVERDUE",
            warnings = if (es.myEmail.isBlank()) listOf("SET YOUR E-MAIL IN SETTINGS → E-MAIL & OUTLOOK") else emptyList(),
        )
    }

    // ----------------------------------------------------------- weekly update

    fun weekly(data: PlannerData, es: EmailSettings, today: LocalDate = Ist.today()): Pair<List<EmailDraft>, List<String>> {
        val todayStr = today.toString()
        val weekAgo = today.minusDays(7)
        val week = "${today.get(IsoFields.WEEK_BASED_YEAR)}-W${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        val names = data.tasks.mapNotNull { it.assignee?.trim()?.uppercase()?.ifBlank { null } }.distinct()
        val missing = ArrayList<String>()
        val drafts = names.mapNotNull { name ->
            if (sameAssignee(name, es.myName)) return@mapNotNull null
            val mine = data.tasks.filter { sameAssignee(it.assignee, name) }
            val open = mine.filter { !it.done }
            if (open.isEmpty()) return@mapNotNull null
            val email = emailOf(name, data.persons)
            if (email == null) { missing += name; return@mapNotNull null }
            if (email.equals(es.myEmail, true)) return@mapNotNull null
            val overdue = open.filter { it.dueDate != null && it.dueDate < todayStr }.sortedBy { it.dueDate }
            val initiated = open.filter { it !in overdue && it.status == TaskStatus.INITIATED }.sortedWith(rootOrder)
            val notStarted = open.filter { it !in overdue && it.status != TaskStatus.INITIATED }.sortedWith(rootOrder)
            val doneWeek = mine.filter { t ->
                t.done && t.completedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(Ist.ZONE).toLocalDate() }?.let { !it.isBefore(weekAgo) } == true
            }
            val first = name.split(" ").first()
            val body = buildString {
                append(para("Dear $first,"))
                append(para("Here is your weekly update of open activities. Please review the target dates and update the status where needed."))
                if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(table(taskHeaders(false), taskRows(overdue, data.projects, todayStr, false))) }
                if (initiated.isNotEmpty()) { append(heading("Initiated (${initiated.size})")); append(table(taskHeaders(false), taskRows(initiated, data.projects, todayStr, false))) }
                if (notStarted.isNotEmpty()) { append(heading("Not started (${notStarted.size})", SLATE)); append(table(taskHeaders(false), taskRows(notStarted, data.projects, todayStr, false))) }
                if (doneWeek.isNotEmpty()) {
                    append(heading("Completed in the last 7 days (${doneWeek.size}) — thank you!", GREEN))
                    append(table(listOf("#", "Activity", "Completed", "Remarks"), doneWeek.mapIndexed { i, t ->
                        listOf("${i + 1}", "<b>${u(t.text)}</b>", u(t.completedAt?.let { Ist.formatStamp(it) } ?: ""), u(t.completionRemarks ?: ""))
                    }))
                }
            }
            EmailDraft(
                kind = EmailKind.WEEKLY,
                key = "weekly:$email:$week",
                to = email,
                cc = if (es.ccMe) es.myEmail else "",
                subject = "WEEKLY UPDATE: YOUR OPEN ACTIVITIES (${open.size}) – ${Ist.formatFull(todayStr).uppercase()}",
                html = page("Weekly update", "For $name · week of ${Ist.formatFull(todayStr)}", body, es),
                summary = "$name · ${open.size} OPEN · ${overdue.size} OVERDUE",
            )
        }
        return drafts to missing
    }

    fun test(es: EmailSettings): EmailDraft = EmailDraft(
        kind = EmailKind.TEST,
        key = "test:${System.currentTimeMillis()}",
        to = es.myEmail,
        subject = "KAR PLANNER – TEST E-MAIL",
        html = page("Test e-mail", "Outlook is connected", para("If you can read this, KAR Planner can send e-mails through your Outlook."), es),
        summary = "CHECKS THAT OUTLOOK IS CONNECTED",
    )
}
