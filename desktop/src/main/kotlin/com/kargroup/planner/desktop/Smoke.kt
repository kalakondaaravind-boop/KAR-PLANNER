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
            TaskEntity(text = "SUBMIT Q3 COMPLIANCE REPORT TO AUDIT COMMITTEE", assignee = "PRIYA SHARMA", priority = Priority.VERY_HIGH, dueDate = d(-3), dueTime = "11:00", estimatedMinutes = 60, status = TaskStatus.INITIATED),
            TaskEntity(text = "SHARE REVISED BUDGET WITH FINANCE", assignee = "PRIYA SHARMA", priority = Priority.HIGH, dueDate = d(0), estimatedMinutes = 20),
            TaskEntity(text = "VENDOR CONTRACT REDLINES", assignee = "RAHUL MEHTA", priority = Priority.HIGH, dueDate = d(0), dueTime = "17:00", estimatedMinutes = 30, projectId = aurora.id),
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
    ).forEach { (name, screen) ->
        SwingUtilities.invokeAndWait { vm!!.navigate(screen) }
        Thread.sleep(500)
        val bytes = ImageComposeScene(w, h, Density(1f, DESKTOP_FONT_SCALE)) {
            CompositionLocalProvider(LocalConfiguration provides Configuration(w, h)) {
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
