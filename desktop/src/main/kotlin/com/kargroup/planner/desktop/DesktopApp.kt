package com.kargroup.planner.desktop

import android.content.Context
import android.content.SharedPreferences
import com.kargroup.planner.data.KarDatabase
import com.kargroup.planner.data.KarRepository
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.SettingsStore
import com.kargroup.planner.reminders.ReminderPrefs
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The desktop "Context": preferences live as JSON files in the app's data folder. */
object DesktopContext : Context()

/** Holds the app's long-lived pieces (the desktop equivalent of the tablet's KarApp). */
class DesktopApp {
    val database: KarDatabase = KarDatabase.open()
    val repository = KarRepository(database)
    val settings = SettingsStore(DesktopContext)
    val reminderPrefs = ReminderPrefs(DesktopContext)
    val appPrefs: SharedPreferences = DesktopContext.getSharedPreferences("kar_app", Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val emails = EmailCenter(this)
    val tags = TagStore()
    val templates = TemplateStore()
    val plans = ProjectPlanStore()
    val uiPrefs: SharedPreferences = DesktopContext.getSharedPreferences("kar_desktop", Context.MODE_PRIVATE)
}

/**
 * Prepares e-mails for review and sends them through Outlook.
 * Nothing is sent without you pressing Send in the Outlook E-mails page.
 */
class EmailCenter(private val app: DesktopApp) {

    val store = EmailStore(
        DesktopContext.getSharedPreferences("kar_email", Context.MODE_PRIVATE),
        File(AppDirs.root, "email-queue.json"),
        File(AppDirs.root, "email-sent.json"),
    )

    /** Short messages for the tray / snackbar ("4 e-mails ready to review"). */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices

    /** Warnings about people without an e-mail address, from the last preparation. */
    @Volatile var missingPeople: List<String> = emptyList()
        private set

    private fun data(): PlannerData {
        val s = app.database.snapshot()
        return PlannerData(s.tasks, s.persons, s.projects, s.meetings)
    }

    private val manualOrder get() = app.appPrefs.getBoolean("schedule_manual", false)

    // ---------------------------------------------------------------- prepare

    fun prepareMom(meeting: MeetingMinuteEntity, force: Boolean = true): EmailDraft {
        val key = if (force) "mom:${meeting.id}:${System.currentTimeMillis()}" else "mom:${meeting.id}"
        val d = EmailComposer.mom(meeting, data(), store.settings.value, key)
        // replace any unsent MoM draft for the same meeting
        store.remove(store.queue.value.filter { it.kind == EmailKind.MOM && it.key.startsWith("mom:${meeting.id}") }.map { it.id })
        store.enqueue(listOf(d))
        return d
    }

    /** Called after a meeting is saved: prepares its MoM e-mail when that's switched on. */
    fun onMeetingSaved(meeting: MeetingMinuteEntity): Boolean {
        if (!store.settings.value.momAuto) return false
        if (meeting.actionItems.none { it.description.isNotBlank() } && meeting.discussionNotes.isBlank() && meeting.keyDecisions.isEmpty()) return false
        if (store.alreadyHandled("mom:${meeting.id}")) {
            // already sent once: refresh only if an updated draft is still waiting
            if (store.queue.value.none { it.key.startsWith("mom:${meeting.id}") }) return false
        }
        prepareMom(meeting, force = false)
        return true
    }

    fun prepareDigest(): Int {
        val d = EmailComposer.digest(data(), store.settings.value, app.settings.current, manualOrder)
        store.enqueue(listOf(d))
        return 1
    }

    fun prepareReminders(): Int {
        val (drafts, missing) = EmailComposer.reminders(data(), store.settings.value)
        missingPeople = missing
        val fresh = drafts.filter { !store.alreadyHandled(it.key) }
        store.enqueue(fresh)
        return fresh.size
    }

    fun prepareWeekly(): Int {
        val (drafts, missing) = EmailComposer.weekly(data(), store.settings.value)
        missingPeople = (missingPeople + missing).distinct()
        val fresh = drafts.filter { !store.alreadyHandled(it.key) }
        store.enqueue(fresh)
        return fresh.size
    }

    /** Update e-mail with one person's open activities (from their By Person page). */
    fun preparePersonUpdate(name: String): Int {
        val es = store.settings.value
        val data = data()
        val email = EmailComposer.emailOf(name, data.persons) ?: return 0
        val (drafts, _) = EmailComposer.weekly(data, es.copy(myName = if (com.kargroup.planner.ui.sameAssignee(name, es.myName)) "" else es.myName))
        val d = drafts.firstOrNull { it.to.equals(email, true) } ?: return 0
        store.enqueue(listOf(d.copy(key = "person:$email:${System.currentTimeMillis()}")))
        return 1
    }

    fun prepareTest() = store.enqueue(listOf(EmailComposer.test(store.settings.value)))

    // ------------------------------------------------------------------- send

    /** Sends / opens / saves the given drafts through Outlook; handled ones leave the queue. */
    suspend fun deliver(ids: Collection<String>, mode: OutlookMailer.Mode): String = withContext(Dispatchers.IO) {
        val drafts = store.queue.value.filter { it.id in ids }
        val missingTo = drafts.filter { it.to.isBlank() }
        val ready = drafts - missingTo.toSet()
        if (ready.isEmpty()) return@withContext "Add at least one recipient in 'To' first."
        val r = OutlookMailer.run(ready, mode, store.settings.value.useOutlookSignature)
        if (r.fatal != null) return@withContext "Outlook: ${r.fatal}"
        val done = ready.filter { it.id in r.ok }
        store.recordSent(done, mode.arg)
        store.remove(done.map { it.id })
        val verb = when (mode) {
            OutlookMailer.Mode.SEND -> "sent"
            OutlookMailer.Mode.DISPLAY -> "opened in Outlook"
            OutlookMailer.Mode.DRAFT -> "saved to Outlook Drafts"
        }
        buildString {
            append("${done.size} e-mail${if (done.size == 1) "" else "s"} $verb")
            if (r.errors.isNotEmpty()) append(" · ${r.errors.size} failed: ${r.errors.values.first().take(120)}")
            if (missingTo.isNotEmpty()) append(" · ${missingTo.size} skipped (no recipient)")
        }
    }

    // -------------------------------------------------------------- automatic

    /** Every minute: at the digest time, prepares the day's e-mails (once per day) and announces them. */
    fun startAutomation() {
        app.scope.launch {
            delay(5_000)
            while (isActive) {
                runCatching { tick() }
                delay(60_000)
            }
        }
    }

    private fun tick() {
        val es = store.settings.value
        val now = Ist.now()
        val today = now.toLocalDate()
        val todayStr = today.toString()
        val minutes = now.hour * 60 + now.minute
        if (minutes < es.digestTime) return
        val ps = app.settings.current
        val workingDay = ps.isWorkingDay(today)
        var prepared = 0
        if (es.digestOn && workingDay && store.lastRun("digest") != todayStr && es.myEmail.isNotBlank()) {
            prepared += prepareDigest(); store.markRun("digest", todayStr)
        }
        if (es.remindersOn && workingDay && store.lastRun("reminders") != todayStr) {
            prepared += prepareReminders(); store.markRun("reminders", todayStr)
        }
        if (es.weeklyOn && today.dayOfWeek.value == es.weeklyDay && store.lastRun("weekly") != todayStr) {
            prepared += prepareWeekly(); store.markRun("weekly", todayStr)
        }
        if (prepared > 0) {
            _notices.tryEmit("$prepared e-mail${if (prepared == 1) " is" else "s are"} ready to review and send")
        }
    }
}
