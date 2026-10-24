package com.kargroup.planner.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.kargroup.planner.data.MeetingActionItem
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.ui.KarPlannerApp
import com.kargroup.planner.ui.MainViewModel
import com.kargroup.planner.ui.Screen
import com.kargroup.planner.ui.theme.KarTheme
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities

/**
 * Build check run on CI (not part of the installed app's normal use): fills a fresh data folder
 * with sample data, prepares every kind of e-mail, saves them as HTML and renders screenshots
 * of the main screens. Usage: smoke <output folder>
 */
object Smoke {
@JvmStatic
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "smoke-out").apply { mkdirs() }
    val app = DesktopApp()
    val repo = app.repository
    val today = Ist.today()
    val d = { n: Long -> today.plusDays(n).toString() }

    runBlocking {
        listOf(
            PersonEntity(name = "PRIYA SHARMA", role = "SENIOR CONSULTANT", email = "priya.sharma@example.com"),
            PersonEntity(name = "RAHUL MEHTA", role = "PROCUREMENT LEAD", email = "rahul.mehta@example.com"),
            PersonEntity(name = "VIKRAM IYER", role = "FINANCE MANAGER", email = "vikram.iyer@example.com"),
            PersonEntity(name = "ANITA RAO", role = "ANALYST"),
        ).forEach { repo.savePerson(it, null) }
        val aurora = ProjectEntity(name = "PROJECT AURORA", color = "#0EA5E9")
        repo.saveProject(aurora)
        listOf(
            TaskEntity(text = "SUBMIT Q3 COMPLIANCE REPORT TO AUDIT COMMITTEE", assignee = "PRIYA SHARMA", priority = Priority.VERY_HIGH, dueDate = d(-3), dueTime = "11:00", estimatedMinutes = 60, status = TaskStatus.INITIATED, statusNote = "DRAFT SHARED WITH CFO; AWAITING SIGN-OFF", notes = "ATTACH SIGNED RECONCILIATION AND VARIANCE MEMO"),
            TaskEntity(text = "SHARE REVISED BUDGET WITH FINANCE", assignee = "PRIYA SHARMA", priority = Priority.HIGH, dueDate = d(0), estimatedMinutes = 20),
            TaskEntity(text = "VENDOR CONTRACT REDLINES", assignee = "RAHUL MEHTA", priority = Priority.HIGH, dueDate = d(0), dueTime = "17:00", estimatedMinutes = 30, projectId = aurora.id, statusNote = "LEGAL COMMENTS RECEIVED"),
            TaskEntity(text = "APPROVE TRAVEL REIMBURSEMENT", priority = Priority.MEDIUM, dueDate = d(0), estimatedMinutes = 5),
            TaskEntity(text = "UPDATE TEAM CAPACITY TRACKER", assignee = "VIKRAM IYER", dueDate = d(1), estimatedMinutes = 45),
            TaskEntity(text = "DRAFT Q4 HIRING PLAN", assignee = "ANITA RAO", priority = Priority.MEDIUM, dueDate = d(1), estimatedMinutes = 90),
        ).forEach { repo.saveTask(it) }
        repo.saveMeeting(
            MeetingMinuteEntity(
                title = "PROJECT AURORA STEERING COMMITTEE",
                meetingDate = d(0), meetingTime = "10:00", durationMinutes = 60, projectId = aurora.id,
                attendees = listOf("PRIYA SHARMA", "RAHUL MEHTA", "VIKRAM IYER", "ANITA RAO"),
                agenda = "1. PROGRAMME STATUS\n2. BUDGET RE-FORECAST\n3. VENDOR ONBOARDING",
                discussionNotes = "PROGRAMME IS AMBER ON SCHEDULE; REPORTING MODULE MOVED TO PHASE 2.",
                keyDecisions = listOf("REPORTING MODULE MOVED TO PHASE 2", "BUDGET RE-FORECAST TO BE SHARED BY FRIDAY"),
                actionItems = listOf(
                    MeetingActionItem(description = "CIRCULATE REVISED PROJECT PLAN", assignee = "PRIYA SHARMA", dueDate = d(2)),
                    MeetingActionItem(description = "CLOSE VENDOR ONBOARDING CHECKLIST", assignee = "RAHUL MEHTA", dueDate = d(1)),
                    MeetingActionItem(description = "SHARE BUDGET RE-FORECAST", assignee = "VIKRAM IYER", dueDate = d(4)),
                ),
            )
        )
    }
    app.emails.store.updateSettings {
        it.copy(myName = "KALAKONDA ARAVIND", myEmail = "aravind@example.com", signature = "DIRECTOR · KAR GROUP", reminderDaysAhead = 1)
    }
    val meeting = app.database.snapshot().meetings.first()
    app.emails.prepareMom(meeting)
    app.emails.prepareDigest()
    app.emails.prepareReminders()
    app.emails.prepareWeekly()
    val queue = app.emails.store.queue.value
    println("SMOKE: prepared ${queue.size} e-mails, missing e-mail for ${app.emails.missingPeople}")
    queue.forEachIndexed { i, e ->
        File(out, "email-${i + 1}-${e.kind}.html").writeText(e.html, Charsets.UTF_8)
        println("SMOKE: ${e.kind} to=${e.to} cc=${e.cc} subject=${e.subject} warnings=${e.warnings}")
    }
    check(queue.any { it.kind == EmailKind.MOM }) { "MoM e-mail missing" }
    check(queue.any { it.kind == EmailKind.DIGEST }) { "Digest e-mail missing" }
    check(queue.any { it.kind == EmailKind.REMINDER }) { "Reminder e-mails missing" }
    app.database.saveNow()
    check(AppDirs.dataFile.length() > 100) { "Data file not written" }

    // Screenshots of the main screens (rendered off-screen).
    com.kargroup.planner.ui.theme.KarPalette.depth = 0.35f
    run {
        val all = app.database.snapshot().tasks
        all.firstOrNull { it.text.startsWith("SUBMIT Q3") }?.let { app.tags.add(listOf(it.id), "AUDIT") }
        all.firstOrNull { it.text.startsWith("VENDOR CONTRACT") }?.let { app.tags.add(listOf(it.id), "CLIENT") }
        all.firstOrNull { it.text.startsWith("SHARE REVISED") }?.let { app.tags.add(listOf(it.id), "FINANCE") }
    }
    // Special project sample (Gantt planner)
    val plan = SpecialProject(name = "MINIMUM WAGES REVISION", lead = "PRIYA SHARMA", health = ProjectHealth.AT_RISK,
        objective = "Implement the revised minimum wages notification across all units by the statutory date.",
        start = d(-20), targetEnd = d(40), color = "#7C3AED")
    app.plans.saveProject(plan)
    app.plans.saveProject(SpecialProject(name = "PLANT ERP ROLLOUT", lead = "RAHUL MEHTA", start = d(-5), targetEnd = d(70), color = "#0EA5E9"))
    run {
        val s = app.settings.current
        fun t(title: String, parent: PlanTask?, o: Double, st: Long, days: Int, pct: Int, res: String, pr: String = PlanPriority.NORMAL,
              ms: Boolean = false, pred: List<PlanTask> = emptyList()): PlanTask {
            val a = PlanCalc.nextWorking(today.plusDays(st), s)
            return PlanTask(projectId = plan.id, parentId = parent?.id, order = o, title = title, start = a.toString(),
                finish = (if (ms) a else PlanCalc.finishFor(a, days, s)).toString(), percent = pct, resource = res, priority = pr, milestone = ms,
                predecessors = pred.map { Dependency(it.id) }, workHours = if (ms) null else days * 4.0)
        }
        val p1 = t("ASSESSMENT", null, 1.0, -20, 10, 0, "")
        val a1 = t("COLLECT NOTIFICATION & RATES", p1, 1.0, -20, 4, 100, "ANITA RAO")
        val a2 = t("GAP ANALYSIS BY UNIT", p1, 2.0, -14, 6, 100, "PRIYA SHARMA", PlanPriority.HIGH, pred = listOf(a1))
        val p2 = t("IMPLEMENTATION", null, 2.0, -6, 20, 0, "")
        val b1 = t("PAYROLL MASTER UPDATE", p2, 1.0, -6, 5, 60, "VIKRAM IYER", PlanPriority.CRITICAL, pred = listOf(a2))
        val b2 = t("ARREARS CALCULATION", p2, 2.0, -3, 4, 20, "VIKRAM IYER", PlanPriority.HIGH)
        val b3 = t("CONTRACTOR COMPLIANCE CHECK", p2, 3.0, 2, 8, 0, "RAHUL MEHTA")
        val b4 = t("REVISED WAGES EFFECTIVE", p2, 4.0, 14, 1, 0, "PRIYA SHARMA", PlanPriority.CRITICAL, ms = true, pred = listOf(b1, b2))
        val p3 = t("CLOSURE", null, 3.0, 16, 10, 0, "")
        val c1 = t("STATUTORY RETURNS & REGISTERS", p3, 1.0, 16, 6, 0, "ANITA RAO", pred = listOf(b4))
        val c2 = t("SIGN-OFF", p3, 2.0, 26, 1, 0, "PRIYA SHARMA", ms = true, pred = listOf(c1))
        val all = listOf(p1, a1, a2, p2, b1, b2, b3, b4, p3, c1, c2)
        app.plans.saveTasks(PlanCalc.baseline(all).map { if (it.id == b2.id) it.copy(start = PlanCalc.nextWorking(today.minusDays(1), s).toString(), finish = PlanCalc.finishFor(today.minusDays(1), 6, s).toString()) else if (it.id == a2.id) it.copy(percent = 80) else it })
    }
    var vm: MainViewModel? = null
    SwingUtilities.invokeAndWait { vm = MainViewModel(app).also { it.reminderPopupShown = true } }
    Thread.sleep(1500)
    val w = 1440
    val h = 900
    listOf(
        "screen-1-scheduler" to Screen.Schedule,
        "screen-2-outlook-emails" to Screen.Emails,
        "screen-3-settings" to Screen.Settings,
        "screen-4-by-person" to Screen.Person("PRIYA SHARMA"),
        "screen-5-meetings" to Screen.Meetings,
        "screen-6-dashboard" to Screen.Dashboard,
        "screen-7-board" to Screen.Board,
        "screen-8-table" to Screen.Table,
        "screen-9-templates" to Screen.Templates,
        "screen-10-dashboard-dark" to Screen.Dashboard,
        "screen-11-board-dark" to Screen.Board,
        "screen-12-special-portfolio" to Screen.Tracker,
        "screen-13-special-gantt" to Screen.Tracker,
        "screen-14-special-overview" to Screen.Tracker,
        "screen-15-special-sheet" to Screen.Tracker,
        "screen-16-special-workload" to Screen.Tracker,
        "screen-17-special-gantt-dark" to Screen.Tracker,
    ).forEach { (name, screen) ->
        val pid = app.plans.projects.value.firstOrNull { it.name.startsWith("MINIMUM") }?.id
        com.kargroup.planner.ui.PlanScreenState.projectId = if (name.contains("portfolio")) null else pid
        com.kargroup.planner.ui.PlanScreenState.tab = when {
            name.contains("overview") -> "OVERVIEW"; name.contains("sheet") -> "SHEET"; name.contains("workload") -> "WORKLOAD"; else -> "GANTT"
        }
        com.kargroup.planner.ui.theme.KarPalette.dark = name.endsWith("-dark")
        SwingUtilities.invokeAndWait { vm!!.navigate(screen) }
        Thread.sleep(500)
        val bytes = ImageComposeScene(w, h, Density(DESKTOP_UI_SCALE, DESKTOP_FONT_SCALE)) {
            CompositionLocalProvider(LocalConfiguration provides Configuration((w / DESKTOP_UI_SCALE).toInt(), (h / DESKTOP_UI_SCALE).toInt())) {
                KarTheme { KarPlannerApp(vm = vm!!, app = app) }
            }
        }.use { scene ->
            var t = 0L
            repeat(12) { scene.render(t); t += 100_000_000L; Thread.sleep(80) }
            scene.render(t).encodeToData(EncodedImageFormat.PNG)?.bytes
        }
        if (bytes != null) File(out, "$name.png").writeBytes(bytes)
        println("SMOKE: rendered $name")
    }
    println("SMOKE: OK")
    System.exit(0)
}
}
