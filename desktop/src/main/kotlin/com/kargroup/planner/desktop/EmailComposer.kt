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
 * Builds plain, professional Outlook e-mails (Calibri 11 pt, no banner) with tables and
 * Excel-style conditional colouring where it adds meaning (overdue, due today, priority, status).
 */
object EmailComposer {

    private const val INK = "#000000"
    private const val HEAD = "#1F3864"   // dark blue section headings (Office theme)
    private const val GREY = "#595959"
    private const val RED = "#C00000"
    private const val AMBER = "#C55A11"
    private const val GREEN = "#00703C"
    private const val GRID = "#BFBFBF"
    private const val HEADER_FILL = "#DDEBF7"
    private const val FONT = "font-family:Calibri,'Segoe UI',Arial,sans-serif"

    /** Marks the message body so Outlook's own signature can be added below it. */
    const val BODY_START = "<!--KAR-BODY-START-->"
    const val BODY_END = "<!--KAR-BODY-END-->"

    // ------------------------------------------------------------------ helpers

    private fun esc(s: String?): String = (s ?: "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** HTML-escaped text with line breaks kept (entries keep the case they were saved in). */
    private fun u(s: String?): String = esc(s).replace("\n", "<br>")

    /** "PRIYA SHARMA" -> "Priya" for greetings. */
    private fun firstName(name: String): String =
        name.trim().split(" ").first().lowercase().replaceFirstChar { it.titlecase() }

    fun emailOf(name: String?, persons: List<PersonEntity>): String? {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return null
        if (n.contains('@')) return n.lowercase()
        return persons.firstOrNull { sameAssignee(it.name, n) }?.email?.trim()?.takeIf { it.contains('@') }?.lowercase()
    }

    private fun projectName(t: TaskEntity, projects: List<ProjectEntity>) = t.projectId?.let { id -> projects.firstOrNull { it.id == id }?.name }

    private fun target(t: TaskEntity): String =
        if (t.dueDate == null) "–" else Ist.formatFull(t.dueDate) + (t.dueTime?.let { ", " + formatTime12(it) } ?: "")

    private fun priorityLabel(p: String?): String = when (p) {
        Priority.VERY_HIGH -> "Very High"
        Priority.HIGH -> "High"
        Priority.MEDIUM -> "Medium"
        else -> "Standard"
    }

    private fun priorityColor(p: String?): String = when (p) {
        Priority.VERY_HIGH, Priority.HIGH -> RED
        Priority.MEDIUM -> AMBER
        else -> INK
    }

    private fun statusLabel(t: TaskEntity): String = when {
        t.done -> "Completed"
        t.status == TaskStatus.INITIATED -> "Initiated"
        else -> "Not started"
    }

    /** Typed signature from Settings, laid out like a standard Outlook signature. */
    private fun typedSignature(es: EmailSettings): String {
        val lines = es.signature.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sb = StringBuilder("<p style=\"margin:18px 0 0 0;$FONT;font-size:11pt;color:$INK\">")
        if (lines.isEmpty() || !lines.first().lowercase().startsWith("regards")) {
            sb.append("Regards,<br>").append(esc(es.myName.ifBlank { "" }))
            if (lines.isNotEmpty()) sb.append("<br>")
        }
        lines.forEachIndexed { i, l ->
            // address / contact lines in the smaller grey style, the rest in normal text
            val small = i >= 4 || l.contains("mob", true) || l.contains("+91") || l.startsWith("www", true) || l.contains("@")
            val text = if (l.startsWith("www", true) || l.startsWith("http", true))
                "<a href=\"${esc(if (l.startsWith("http", true)) l else "http://$l")}\" style=\"color:#0563C1\">${esc(l)}</a>" else esc(l)
            sb.append(if (small) "<span style=\"font-size:9pt;color:$GREY\">$text</span>" else text)
            if (i < lines.lastIndex) sb.append("<br>")
        }
        return sb.append("</p>").toString()
    }

    /** Plain business e-mail: no banner, Calibri 11 pt, tables for the details. */
    private fun page(body: String, es: EmailSettings, signed: Boolean = true): String {
        val sign = if (signed && !es.useOutlookSignature) typedSignature(es) else ""
        return "<html><head><meta charset=\"utf-8\"></head><body style=\"$FONT;font-size:11pt;color:$INK\">" +
            BODY_START + "<div style=\"$FONT;font-size:11pt;color:$INK\">" + body + legend(body) + "</div>" + BODY_END +
            sign + "</body></html>"
    }

    private fun heading(text: String, color: String = HEAD): String =
        "<p style=\"margin:16px 0 6px 0;$FONT;font-size:11pt;font-weight:bold;color:$color\">${esc(text)}</p>"

    private fun para(text: String): String = "<p style=\"margin:0 0 10px 0;$FONT;font-size:11pt\">${u(text)}</p>"

    // ------------------------------------------------- conditional formatting
    // Excel-style fills: light fill with dark text of the same hue.
    private const val RED_BG = "#FFC7CE"; private const val RED_FG = "#9C0006"
    private const val AMB_BG = "#FFEB9C"; private const val AMB_FG = "#9C5700"
    private const val GRN_BG = "#C6EFCE"; private const val GRN_FG = "#006100"
    private const val BLU_BG = "#DDEBF7"; private const val BLU_FG = "#1F4E79"
    private const val GRY_BG = "#EDEDED"; private const val GRY_FG = "#404040"
    private const val ORG_BG = "#FCE4D6"; private const val ORG_FG = "#C00000"
    private const val MARK = "\u0007"

    /** A table cell with a coloured fill (read by [table]). */
    private fun fill(html: String, bg: String, fg: String, bold: Boolean = false) = "$MARK$bg|$fg|${if (bold) 1 else 0}$MARK$html"

    private fun targetCell(t: TaskEntity, today: String): String {
        val text = esc(target(t))
        return when {
            t.done -> fill(text, GRN_BG, GRN_FG)
            t.dueDate == null -> text
            t.dueDate < today -> fill("$text<br><span style=\"font-size:8pt\">Overdue by ${java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(t.dueDate), java.time.LocalDate.parse(today))} day(s)</span>", RED_BG, RED_FG, bold = true)
            t.dueDate == today -> fill("$text<br><span style=\"font-size:8pt\">Due today</span>", AMB_BG, AMB_FG, bold = true)
            else -> text
        }
    }

    private fun priorityCell(p: String?): String = when (p) {
        Priority.VERY_HIGH -> fill("Very High", RED_BG, RED_FG, bold = true)
        Priority.HIGH -> fill("High", ORG_BG, ORG_FG, bold = true)
        Priority.MEDIUM -> fill("Medium", AMB_BG, AMB_FG)
        else -> "Standard"
    }

    private fun statusCell(t: TaskEntity): String = when {
        t.done -> fill("Completed", GRN_BG, GRN_FG, bold = true)
        t.status == TaskStatus.INITIATED -> fill("Initiated", BLU_BG, BLU_FG)
        else -> fill("Not started", GRY_BG, GRY_FG)
    }

    /** Colour key shown under the tables. */
    private fun legend(body: String): String {
        fun key(bg: String, fg: String, label: String) =
            "<span style=\"background:$bg;color:$fg;padding:1px 6px;border:1px solid $GRID\">$label</span>"
        // only the colours actually used in this e-mail
        val keys = listOfNotNull(
            key(RED_BG, RED_FG, "Overdue / Very High").takeIf { body.contains("bgcolor=\"$RED_BG\"") },
            key(AMB_BG, AMB_FG, "Due today / Medium").takeIf { body.contains("bgcolor=\"$AMB_BG\"") },
            key(ORG_BG, ORG_FG, "High").takeIf { body.contains("bgcolor=\"$ORG_BG\"") },
            key(BLU_BG, BLU_FG, "Initiated / Open").takeIf { body.contains("bgcolor=\"$BLU_BG\"") },
            key(GRN_BG, GRN_FG, "Completed").takeIf { body.contains("bgcolor=\"$GRN_BG\"") },
        )
        if (keys.size < 2) return ""
        return "<p style=\"margin:10px 0 0 0;$FONT;font-size:8pt;color:$GREY\">Colour key: " + keys.joinToString(" &nbsp;") + "</p>"
    }


    private fun table(headers: List<String>, rows: List<List<String>>, widths: List<String>? = null): String {
        val cell = "$FONT;font-size:10pt;padding:4px 8px;border:1px solid $GRID"
        val th = headers.mapIndexed { i, h ->
            val w = widths?.getOrNull(i)?.let { " width=\"$it\"" } ?: ""
            "<th align=\"left\"$w style=\"$cell;background:$HEADER_FILL;color:$INK;font-weight:bold\">${esc(h)}</th>"
        }.joinToString("")
        val tr = rows.mapIndexed { ri, r ->
            val band = if (ri % 2 == 1) "background:#F7F9FC;" else ""
            "<tr>" + r.joinToString("") { c ->
                if (c.startsWith(MARK)) {
                    val end = c.indexOf(MARK, 1)
                    val (bg, fg, b) = c.substring(1, end).split("|")
                    val bold = if (b == "1") "font-weight:bold;" else ""
                    "<td valign=\"top\" bgcolor=\"$bg\" style=\"$cell;background:$bg;color:$fg;$bold\">${c.substring(end + 1)}</td>"
                } else "<td valign=\"top\" style=\"$cell;$band\">$c</td>"
            } + "</tr>"
        }.joinToString("")
        return "<table cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;border:1px solid $GRID;margin:0 0 6px 0\"><tr>$th</tr>$tr</table>"
    }

    private fun colored(text: String, color: String, bold: Boolean = true) =
        "<span style=\"color:$color;${if (bold) "font-weight:bold" else ""}\">${esc(text)}</span>"

    private fun taskRows(list: List<TaskEntity>, projects: List<ProjectEntity>, today: String, withOwner: Boolean): List<List<String>> =
        list.mapIndexed { i, t ->
            val overdue = !t.done && t.dueDate != null && t.dueDate < today
            buildList {
                add("${i + 1}")
                add(u(t.text) + (projectName(t, projects)?.let { "<br><span style=\"color:$GREY;font-size:9pt\">${u(it)}</span>" } ?: ""))
                if (withOwner) add(u(t.assignee ?: "Unassigned"))
                add(targetCell(t, today))
                add(priorityCell(t.priority))
                add(statusCell(t))
            }
        }

    /** Latest update for the "Current status" column (completion remarks once done). */
    private fun currentStatus(t: TaskEntity): String? =
        if (t.done) t.completionRemarks?.takeIf { it.isNotBlank() }?.let { "Completed: $it" }
        else t.statusNote?.takeIf { it.isNotBlank() }

    private fun notesCell(notes: String?): String =
        notes?.trim()?.takeIf { it.isNotEmpty() }?.let { n ->
            "<span style=\"font-size:9pt;color:$GREY\">${u(if (n.length > 300) n.take(300) + "…" else n)}</span>"
        } ?: ""

    /** Task table; "Current status" and "Notes" columns appear only when some row has them. */
    private fun taskTable(list: List<TaskEntity>, projects: List<ProjectEntity>, today: String, withOwner: Boolean): String {
        val showStatus = list.any { currentStatus(it) != null }
        val showNotes = list.any { !it.notes.isNullOrBlank() }
        val headers = taskHeaders(withOwner) + listOfNotNull("Current status".takeIf { showStatus }, "Notes".takeIf { showNotes })
        val rows = taskRows(list, projects, today, withOwner).mapIndexed { i, r ->
            val t = list[i]
            r + listOfNotNull(
                (currentStatus(t)?.let { u(it) } ?: "–").takeIf { showStatus },
                notesCell(t.notes).takeIf { showNotes },
            )
        }
        return table(headers, rows)
    }

    private fun taskHeaders(withOwner: Boolean) =
        if (withOwner) listOf("#", "Activity", "Owner", "Target date", "Priority", "Status")
        else listOf("#", "Activity", "Target date", "Priority", "Status")

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
            append(para("Hi All,"))
            append(para("Please find below the minutes of the meeting held on ${Ist.formatFull(m.meetingDate)}. Request you to close the action points by the target dates mentioned."))
            append(
                table(
                    listOf("Particulars", "Details"),
                    listOfNotNull(
                        listOf("<b>Meeting</b>", u(m.title)),
                        listOf("<b>Date &amp; time</b>", u("${Ist.formatFull(m.meetingDate)}, ${formatTime12(m.meetingTime)} (${formatMinutes(m.durationMinutes)})")),
                        project?.let { listOf("<b>Project</b>", u(it)) },
                        listOf("<b>Attendees</b>", u(m.attendees.joinToString(", ").ifBlank { "–" })),
                    ),
                    widths = listOf("140", "520"),
                )
            )
            if (m.agenda.isNotBlank()) { append(heading("Agenda")); append(para(m.agenda)) }
            if (m.discussionNotes.isNotBlank()) { append(heading("Discussion")); append(para(m.discussionNotes)) }
            if (m.keyDecisions.any { it.isNotBlank() }) {
                append(heading("Key decisions"))
                append("<ol style=\"margin:0 0 10px 22px;padding:0;$FONT;font-size:11pt\">")
                m.keyDecisions.filter { it.isNotBlank() }.forEach { append("<li style=\"margin-bottom:3px\">${u(it)}</li>") }
                append("</ol>")
            }
            val items = m.actionItems.filter { it.description.isNotBlank() }
            val linked = items.associate { a -> a.id to data.tasks.firstOrNull { it.meetingActionItemId == a.id } }
            val showStatus = linked.values.any { it != null && currentStatus(it) != null }
            val showNotes = linked.values.any { !it?.notes.isNullOrBlank() }
            if (items.isNotEmpty()) {
                append(heading("Action points"))
                append(
                    table(
                        listOf("#", "Action point", "Responsibility", "Target date", "Status") +
                            listOfNotNull("Current status".takeIf { showStatus }, "Notes".takeIf { showNotes }),
                        items.mapIndexed { i, a ->
                            val lt = linked[a.id]
                            val overdue = !a.completed && a.dueDate != null && a.dueDate < today
                            listOf(
                                "${i + 1}",
                                u(a.description),
                                u(a.assignee.ifBlank { "Unassigned" }),
                                when {
                                    a.dueDate == null -> "–"
                                    a.completed -> fill(esc(Ist.formatFull(a.dueDate)), GRN_BG, GRN_FG)
                                    overdue -> fill(esc(Ist.formatFull(a.dueDate)) + "<br><span style=\"font-size:8pt\">Overdue</span>", RED_BG, RED_FG, bold = true)
                                    a.dueDate == today -> fill(esc(Ist.formatFull(a.dueDate)), AMB_BG, AMB_FG, bold = true)
                                    else -> esc(Ist.formatFull(a.dueDate))
                                },
                                if (a.completed) fill("Closed", GRN_BG, GRN_FG, bold = true) else fill("Open", BLU_BG, BLU_FG),
                            ) + listOfNotNull(
                                (lt?.let { currentStatus(it) }?.let { u(it) } ?: "–").takeIf { showStatus },
                                notesCell(lt?.notes).takeIf { showNotes },
                            )
                        },
                    )
                )
            }
        }
        val subject = "MoM: ${m.title} – ${Ist.formatFull(m.meetingDate)}"
        return EmailDraft(
            kind = EmailKind.MOM,
            key = key,
            to = to,
            cc = cc,
            subject = subject,
            html = page(body, es),
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
            val body = buildString {
                append(para("Hi ${firstName(name)},"))
                append(para("This is a gentle reminder of the action points assigned to you. Request you to update the status, or let me know if any target date needs revision."))
                if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(taskTable(overdue, data.projects, todayStr, false)) }
                if (dueToday.isNotEmpty()) { append(heading("Due today (${dueToday.size})", AMBER)); append(taskTable(dueToday, data.projects, todayStr, false)) }
                if (soon.isNotEmpty()) { append(heading("Due in the coming days (${soon.size})")); append(taskTable(soon, data.projects, todayStr, false)) }
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
                subject = "Reminder: Action points – ${counts.lowercase().replaceFirstChar { it.titlecase() }}",
                html = page(body, es),
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

        val body = buildString {
            append(para("Hi${if (es.myName.isNotBlank()) " " + firstName(es.myName) else ""},"))
            append(para("Please find below the plan for ${Ist.formatFull(todayStr)}."))
            append(heading("Summary"))
            append(table(
                listOf("Overdue", "Due today", "Due tomorrow", "Meetings today", "Time planned"),
                listOf(listOf(
                    if (overdue.isEmpty()) fill("0", GRN_BG, GRN_FG, bold = true) else fill("${overdue.size}", RED_BG, RED_FG, bold = true),
                    if (dueToday.isEmpty()) "0" else fill("${dueToday.size}", AMB_BG, AMB_FG, bold = true),
                    "${dueTomorrow.size}", "${meetingsToday.size}", u(formatMinutes(plannedMin)),
                )),
            ))
            if (meetingsToday.isNotEmpty()) {
                append(heading("Meetings today"))
                append(table(listOf("Time", "Meeting", "Attendees"), meetingsToday.map {
                    listOf(u(formatTime12(it.meetingTime)), u(it.title), u(it.attendees.joinToString(", ")))
                }))
            }
            if (todays.isNotEmpty()) {
                append(heading("Today's schedule"))
                val schedStatus = todays.any { currentStatus(it.task) != null }
                append(table(listOf("Time", "Activity", "Owner", "Target date", "Priority") + listOfNotNull("Current status".takeIf { schedStatus }), todays.map { si ->
                    val t = si.task
                    listOf(
                        u(formatMillisTime24(si.startMillis) + "–" + formatMillisTime24(si.endMillis)),
                        u(t.text) + (si.path?.let { "<br><span style=\"color:$GREY;font-size:9pt\">${u(it)}</span>" } ?: ""),
                        u(t.assignee ?: "–"),
                        targetCell(t, todayStr),
                        priorityCell(t.priority),
                    ) + listOfNotNull((currentStatus(t)?.let { u(it) } ?: "–").takeIf { schedStatus })
                }))
            }
            if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(taskTable(overdue, data.projects, todayStr, true)) }
            if (dueToday.isNotEmpty()) { append(heading("Due today (${dueToday.size})", AMBER)); append(taskTable(dueToday, data.projects, todayStr, true)) }
            if (dueTomorrow.isNotEmpty()) { append(heading("Due tomorrow (${dueTomorrow.size})")); append(taskTable(dueTomorrow, data.projects, todayStr, true)) }
            if (todays.isEmpty() && overdue.isEmpty() && dueToday.isEmpty()) append(para("Nothing is scheduled for today."))
        }
        return EmailDraft(
            kind = EmailKind.DIGEST,
            key = "digest:$todayStr",
            to = es.myEmail,
            subject = "My day – ${Ist.formatFull(todayStr)} (${overdue.size} overdue, ${dueToday.size} due today)",
            html = page(body, es, signed = false),
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
            val body = buildString {
                append(para("Hi ${firstName(name)},"))
                append(para("Please find below your weekly update of open activities. Request you to review the target dates and update the status where required."))
                if (overdue.isNotEmpty()) { append(heading("Overdue (${overdue.size})", RED)); append(taskTable(overdue, data.projects, todayStr, false)) }
                if (initiated.isNotEmpty()) { append(heading("Initiated (${initiated.size})")); append(taskTable(initiated, data.projects, todayStr, false)) }
                if (notStarted.isNotEmpty()) { append(heading("Not started (${notStarted.size})")); append(taskTable(notStarted, data.projects, todayStr, false)) }
                if (doneWeek.isNotEmpty()) {
                    append(heading("Completed in the last 7 days (${doneWeek.size}) – thank you", GREEN))
                    append(table(listOf("#", "Activity", "Completed", "Remarks"), doneWeek.mapIndexed { i, t ->
                        listOf("${i + 1}", u(t.text), u(t.completedAt?.let { Ist.formatStamp(it) } ?: ""), u(t.completionRemarks ?: ""))
                    }))
                }
            }
            EmailDraft(
                kind = EmailKind.WEEKLY,
                key = "weekly:$email:$week",
                to = email,
                cc = if (es.ccMe) es.myEmail else "",
                subject = "Weekly update: Open activities (${open.size}) – ${Ist.formatFull(todayStr)}",
                html = page(body, es),
                summary = "$name · ${open.size} OPEN · ${overdue.size} OVERDUE",
            )
        }
        return drafts to missing
    }

    fun test(es: EmailSettings): EmailDraft = EmailDraft(
        kind = EmailKind.TEST,
        key = "test:${System.currentTimeMillis()}",
        to = es.myEmail,
        subject = "KAR Planner – test e-mail",
        html = page(para("Hi,") + para("This is a test e-mail from KAR Planner. If you can read this, e-mails are going out through your Outlook correctly."), es),
        summary = "CHECKS THAT OUTLOOK IS CONNECTED",
    )
}
