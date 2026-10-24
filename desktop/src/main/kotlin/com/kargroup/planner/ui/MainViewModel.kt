package com.kargroup.planner.ui

import com.kargroup.planner.data.BackupCodec
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.DataCounts
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.JournalEntry
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.LockMode
import com.kargroup.planner.data.PlannerSettings
import com.kargroup.planner.data.SettingsStore
import com.kargroup.planner.data.TaskHistoryEntity
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.kargroup.planner.desktop.DesktopApp
import com.kargroup.planner.desktop.AppDirs
import java.io.File


/** Desktop counterpart of the tablet's MainViewModel (same state and actions, files instead of Android URIs). */
class MainViewModel(private val karApp: DesktopApp) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repo = karApp.repository
    val reminderPrefs = karApp.reminderPrefs

    val tasks: StateFlow<List<TaskEntity>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projects: StateFlow<List<ProjectEntity>> =
        repo.projects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val contexts: StateFlow<List<ContextTagEntity>> =
        repo.contexts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val meetings: StateFlow<List<MeetingMinuteEntity>> =
        repo.meetings.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val persons: StateFlow<List<PersonEntity>> =
        repo.persons.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val journal: StateFlow<List<JournalEntry>> =
        repo.journal.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val screen = MutableStateFlow<Screen>(Screen.Dashboard)

    /** Requests from keyboard shortcuts / the command bar, handled by the main screen. */
    val commands = kotlinx.coroutines.flow.MutableSharedFlow<DesktopCommand>(extraBufferCapacity = 8)
    fun command(c: DesktopCommand) { commands.tryEmit(c) }

    /** Working hours, holidays, reminders, backup and lock preferences. */
    val settings: StateFlow<PlannerSettings> = karApp.settings.state

    /** Privacy lock state for this app session. */
    val appUnlocked = MutableStateFlow(false)
    val journalUnlocked = MutableStateFlow(false)
    private var backgroundSince: Long? = null

    /** Date the journal should open on (set when coming from Search). */
    val journalDate = MutableStateFlow<String?>(null)

    /** Scheduler sort mode: false = automatic, true = manual. Remembered between launches. */
    val scheduleManual = MutableStateFlow(karApp.appPrefs.getBoolean(KEY_SCHEDULE_MANUAL, false))
    val drillRootId = MutableStateFlow<String?>(null)
    val showCompleted = MutableStateFlow(false)

    /** Current IST date (YYYY-MM-DD); refreshed so views roll over at IST midnight. */
    val today = MutableStateFlow(Ist.todayStr())
    val reminderVersion: StateFlow<Long> = reminderPrefs.version

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    var reminderPopupShown = false

    init {
        viewModelScope.launch { repo.seedDefaultsIfNeeded(karApp.appPrefs) }
        viewModelScope.launch {
            while (isActive) {
                today.value = Ist.todayStr()
                delay(30_000)
            }
        }
    }

    fun toast(msg: String) {
        _messages.trySend(msg.uppercase())
    }

    // ------------------------------------------------------------ navigation

    fun navigate(target: Screen) {
        screen.value = target
        drillRootId.value = null
        if (target != Screen.Journal) journalDate.value = null
    }

    fun openJournalOn(date: String) {
        journalDate.value = date
        screen.value = Screen.Journal
        drillRootId.value = null
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(transform: (PlannerSettings) -> PlannerSettings) {
        val before = settings.value.lockMode
        karApp.settings.update(transform)
        // Turning the lock on from Settings shouldn't lock you out of the screen you're on.
        if (settings.value.lockMode != before) {
            appUnlocked.value = true
            journalUnlocked.value = true
        }
    }

    fun toggleHoliday(day: String) {
        var added = false
        karApp.settings.update { s ->
            added = day !in s.holidays
            s.copy(holidays = if (added) s.holidays + day else s.holidays - day)
        }
        toast(if (added) "${Ist.friendly(day)} marked as holiday · activities moved to the next working day" else "${Ist.friendly(day)} is a working day again")
    }

    fun setPin(pin: String) {
        karApp.settings.update { it.copy(pinHash = SettingsStore.hashPin(pin)) }
        appUnlocked.value = true
        journalUnlocked.value = true
        toast("PIN saved")
    }

    /** Copies today's data into the backups folder right now. */
    fun runBackupNow() = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                karApp.database.saveNow()
                val f = File(AppDirs.backupsDir, "KAR-Planner-manual-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".json")
                AppDirs.dataFile.copyTo(f, overwrite = true)
                f.name
            }
        }
        r.onSuccess { toast("Backup saved: $it") }.onFailure { toast("Backup failed: ${it.message}") }
    }

    fun openDataFolder() {
        runCatching { java.awt.Desktop.getDesktop().open(AppDirs.root) }
    }

    // -------------------------------------------------------------------- lock

    fun unlockApp() { appUnlocked.value = true }
    fun unlockJournal() { journalUnlocked.value = true }

    /** Called when the app goes to the background / comes back. Re-locks after 60 s away. */
    fun onBackground() { backgroundSince = System.currentTimeMillis() }
    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (System.currentTimeMillis() - since > 60_000 && settings.value.lockMode != LockMode.OFF) {
            appUnlocked.value = false
            journalUnlocked.value = false
        }
    }

    fun drillInto(taskId: String?) {
        drillRootId.value = taskId
    }

    fun setShowCompleted(value: Boolean) {
        showCompleted.value = value
    }

    // ----------------------------------------------------------------- people

    fun savePerson(person: PersonEntity, oldName: String?) = viewModelScope.launch {
        repo.savePerson(person, oldName)
        toast(if (oldName == null) "${person.name} added" else "${person.name} updated")
    }

    fun deletePerson(person: PersonEntity) = viewModelScope.launch {
        if (screen.value == Screen.Person(person.name)) navigate(Screen.People)
        repo.deletePerson(person)
        toast("${person.name} removed · their activities are now unassigned")
    }

    // ---------------------------------------------------------------- journal

    fun saveJournal(date: String, mood: String?, title: String?, content: String, quiet: Boolean = false) =
        viewModelScope.launch {
            repo.saveJournal(date, mood, title, content)
            if (!quiet) toast("Journal saved for ${Ist.friendly(date)}")
        }

    fun deleteJournal(entry: JournalEntry) = viewModelScope.launch {
        repo.deleteJournal(entry.id)
        toast("Journal entry deleted")
    }

    // -------------------------------------------------------------- scheduler

    fun setScheduleManual(manual: Boolean) {
        scheduleManual.value = manual
        karApp.appPrefs.edit().putBoolean(KEY_SCHEDULE_MANUAL, manual).apply()
    }

    /** Moves an item in the manual schedule and saves the whole order. */
    fun moveInSchedule(orderedIds: List<String>, from: Int, to: Int) {
        if (from !in orderedIds.indices || to !in orderedIds.indices || from == to) return
        val list = orderedIds.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        viewModelScope.launch { repo.saveManualOrder(list) }
    }

    /** Copies the current automatic order into the manual order. */
    fun resetManualToAuto(autoOrderedIds: List<String>) = viewModelScope.launch {
        repo.saveManualOrder(autoOrderedIds)
        toast("Manual order reset to the automatic schedule")
    }

    // ------------------------------------------------------------------ tasks

    /**
     * Saves an activity. [wantCompleted] comes from the Status selector: switching to/from
     * Completed runs the normal completion logic (recurrence, subtasks, MoM sync).
     */
    fun saveTask(
        task: TaskEntity,
        isNew: Boolean,
        wantCompleted: Boolean = task.done,
        subtasks: List<SubtaskDraft> = emptyList(),
        removedSubtaskIds: List<String> = emptyList(),
        rescheduleReason: String? = null,
    ) = viewModelScope.launch {
        repo.saveTask(task, rescheduleReason)
        // Subtasks edited inside the pop-up: update, create or remove.
        val current = tasks.value.associateBy { it.id }
        val base = System.currentTimeMillis()
        subtasks.forEachIndexed { i, d ->
            val existing = d.id?.let { current[it] }
            if (existing != null) {
                if (existing.text != d.text || (existing.assignee ?: "") != d.assignee) {
                    repo.saveTask(existing.copy(text = d.text, assignee = d.assignee.ifBlank { null }))
                }
            } else {
                repo.saveTask(
                    TaskEntity(
                        text = d.text,
                        assignee = d.assignee.ifBlank { null },
                        parentId = task.id,
                        projectId = task.projectId,
                        createdAt = base + i, // keeps the order they were typed in
                    )
                )
            }
        }
        removedSubtaskIds.distinct().forEach { repo.deleteTask(it) }
        if (wantCompleted != task.done) repo.setDone(task.id, wantCompleted, includeSubtasks = true)
        val n = subtasks.count { it.id == null }
        toast(
            (if (isNew) "Activity created" else "Activity updated") +
                if (n > 0) " · $n subtask${if (n == 1) "" else "s"} added" else ""
        )
    }

    /** Activity typed into the Quick-add bar. */
    fun addQuick(task: TaskEntity) = viewModelScope.launch {
        repo.saveTask(task)
        toast("Added \"${task.text}\"" + (task.dueDate?.let { " · ${Ist.friendly(it)}" } ?: ""))
    }

    /** New target for an overdue activity; the reason is kept in its history. */
    fun reschedule(task: TaskEntity, date: String, time: String?, reason: String) = viewModelScope.launch {
        repo.reschedule(task.id, date, time, reason)
        toast("Rescheduled to ${Ist.friendly(date)}")
    }

    /** Fixes (or releases with null) the start time of an activity in the Scheduler. */
    fun setPlannedStart(task: TaskEntity, start: Long?) = viewModelScope.launch {
        repo.setPlannedStart(task.id, start)
        toast(if (start == null) "Start time released · scheduled automatically" else "Start time fixed at ${formatMillisTime(start)}")
    }

    fun historyFor(taskId: String): Flow<List<TaskHistoryEntity>> = repo.historyFor(taskId)

    // ----------------------------------------------------------------- reports

    /** Saves the report as a printable web page (open it and use Print → Save as PDF) and opens it. */
    fun exportReportHtml(file: File, period: ReportPeriod) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val report = buildReport(tasks.value, persons.value, projects.value, period)
                file.writeText(com.kargroup.planner.desktop.ReportHtml.render(report), Charsets.UTF_8)
                runCatching { java.awt.Desktop.getDesktop().browse(file.toURI()) }
            }
        }
        r.onSuccess { toast("Report saved · use Print → Save as PDF in the browser") }.onFailure { toast("Report export failed: ${it.message}") }
    }

    fun exportReportCsv(file: File, period: ReportPeriod) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val report = buildReport(tasks.value, persons.value, projects.value, period)
                file.writeText(ReportCsv.csv(report, tasks.value, projects.value), Charsets.UTF_8)
            }
        }
        r.onSuccess { toast("Excel (CSV) report saved") }.onFailure { toast("Excel export failed: ${it.message}") }
    }

    /** Completes an activity with its (compulsory) remarks. */
    fun completeWithRemarks(task: TaskEntity, remarks: String, includeSubtasks: Boolean) = viewModelScope.launch {
        repo.completeWithRemarks(task.id, remarks, includeSubtasks)
        if (task.isRecurring) toast("Next \"${task.text}\" scheduled")
    }

    /** Quick status change from a list row. */
    fun setStatus(task: TaskEntity, status: String) = viewModelScope.launch {
        when (status) {
            TaskStatus.COMPLETED -> if (!task.done) repo.setDone(task.id, true, includeSubtasks = true)
            else -> {
                repo.setStatus(task.id, status)
                if (task.done) repo.setDone(task.id, false, includeSubtasks = false)
            }
        }
    }

    fun setDone(task: TaskEntity, done: Boolean, includeSubtasks: Boolean) = viewModelScope.launch {
        repo.setDone(task.id, done, includeSubtasks)
        if (done && task.isRecurring) toast("Done — next \"${task.text}\" scheduled")
    }

    fun toggleCollapsed(task: TaskEntity) = viewModelScope.launch {
        repo.setCollapsed(task.id, !task.isCollapsed)
    }

    fun deleteTask(task: TaskEntity) = viewModelScope.launch {
        if (drillRootId.value == task.id) drillRootId.value = task.parentId
        repo.deleteTask(task.id)
        toast("Activity deleted")
    }

    // ------------------------------------------------------- projects/contexts

    fun saveProject(project: ProjectEntity) = viewModelScope.launch { repo.saveProject(project) }

    fun deleteProject(project: ProjectEntity) = viewModelScope.launch {
        if (screen.value == Screen.Project(project.id)) navigate(Screen.Projects)
        repo.deleteProject(project.id)
        toast("Project \"${project.name}\" deleted")
    }

    fun saveContext(context: ContextTagEntity) = viewModelScope.launch { repo.saveContext(context) }

    fun deleteContext(context: ContextTagEntity) = viewModelScope.launch {
        repo.deleteContext(context.id)
        toast("Context \"${context.name}\" deleted")
    }

    // ---------------------------------------------------------------- meetings

    fun saveMeeting(meeting: MeetingMinuteEntity) = viewModelScope.launch {
        repo.saveMeeting(meeting)
        val queued = karApp.emails.onMeetingSaved(meeting)
        if (queued) toast("MoM e-mail ready to review in Outlook E-mails")
        val n = meeting.actionItems.count { it.description.isNotBlank() }
        toast(if (n > 0) "Meeting saved · $n action item(s) synced to planner" else "Meeting saved")
    }

    fun deleteMeeting(meeting: MeetingMinuteEntity) = viewModelScope.launch {
        repo.deleteMeeting(meeting.id)
        toast("Meeting deleted")
    }

    // --------------------------------------------------------------- reminders

    fun snooze(ids: Collection<String>) {
        if (ids.isEmpty()) return
        reminderPrefs.snooze(ids)
        toast("Snoozed for 1 hour")
    }

    fun dismiss(ids: Collection<String>) {
        if (ids.isEmpty()) return
        reminderPrefs.dismiss(ids)
        toast("Reminder dismissed for today")
    }

    // ------------------------------------------------------- backup & restore

    fun exportBackup(file: File) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val json = BackupCodec.encode(repo.snapshot())
                file.writeText(json, Charsets.UTF_8)
            }
        }
        result.onSuccess { toast("Backup exported successfully") }
            .onFailure { toast("Export failed: ${it.message}") }
    }

    fun importBackup(file: File) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                val snapshot = BackupCodec.decode(text)
                repo.importSnapshot(snapshot)
                snapshot
            }
        }
        result.onSuccess {
            toast(
                "Imported ${it.tasks.size} activities, ${it.projects.size} projects, ${it.meetings.size} meetings" +
                    (if (it.persons.isNotEmpty()) ", ${it.persons.size} people" else "") +
                    (if (it.journal.isNotEmpty()) ", ${it.journal.size} journal entries" else "")
            )
        }.onFailure { toast("Import failed: ${it.message}") }
    }

    suspend fun counts(): DataCounts = repo.counts()

    fun deleteAllData() = viewModelScope.launch {
        repo.deleteAll()
        reminderPrefs.clear()
        navigate(Screen.Today)
        toast("All data deleted. Fresh start!")
    }

    // ------------------------------------------------------------- bulk edit

    private fun byIds(ids: Collection<String>): List<TaskEntity> { val set = ids.toSet(); return tasks.value.filter { it.id in set } }

    fun bulkAssign(ids: Collection<String>, person: String?) = viewModelScope.launch {
        val list = byIds(ids)
        list.forEach { repo.saveTask(it.copy(assignee = person?.trim()?.ifBlank { null })) }
        toast("${list.size} activit${if (list.size == 1) "y" else "ies"} assigned to ${person ?: "nobody"}")
    }

    fun bulkPriority(ids: Collection<String>, priority: String?) = viewModelScope.launch {
        val list = byIds(ids)
        list.forEach { repo.saveTask(it.copy(priority = priority)) }
        toast("Priority updated for ${list.size}")
    }

    fun bulkStatus(ids: Collection<String>, status: String) = viewModelScope.launch {
        val list = byIds(ids).filter { !it.done }
        list.forEach { repo.setStatus(it.id, status) }
        toast("Status set to ${TaskStatus.label(status)} for ${list.size}")
    }

    fun bulkCurrentStatus(ids: Collection<String>, text: String) = viewModelScope.launch {
        val list = byIds(ids)
        list.forEach { repo.saveTask(it.copy(statusNote = text.trim().ifBlank { null })) }
        toast("Current status updated for ${list.size}")
    }

    fun bulkReschedule(ids: Collection<String>, date: String, reason: String) = viewModelScope.launch {
        val list = byIds(ids).filter { !it.done }
        list.forEach { repo.reschedule(it.id, date, it.dueTime, reason) }
        toast("${list.size} moved to ${Ist.friendly(date)}")
    }

    fun bulkComplete(ids: Collection<String>, remarks: String) = viewModelScope.launch {
        val list = byIds(ids).filter { !it.done }
        list.forEach { repo.completeWithRemarks(it.id, remarks, includeSubtasks = true) }
        toast("${list.size} completed")
    }

    fun bulkDelete(ids: Collection<String>) = viewModelScope.launch {
        val list = byIds(ids)
        list.forEach { repo.deleteTask(it.id) }
        toast("${list.size} deleted")
    }

    // -------------------------------------------------------------- templates

    /** Creates an activity (and its subtasks) from a template; returns it so it can be opened for editing. */
    suspend fun createFromTemplate(t: com.kargroup.planner.desktop.ActivityTemplate): TaskEntity {
        val base = System.currentTimeMillis()
        val task = TaskEntity(
            text = t.title.uppercase(),
            notes = t.notes.ifBlank { null },
            priority = t.priority,
            estimatedMinutes = t.estimatedMinutes,
            assignee = t.assignee.ifBlank { null },
            projectId = t.projectId,
            dueDate = t.dueInDays?.let { Ist.today().plusDays(it.toLong()).toString() },
            createdAt = base,
        )
        repo.saveTask(task)
        t.subtasks.forEachIndexed { i, (text, who) ->
            repo.saveTask(TaskEntity(text = text.uppercase(), assignee = who.ifBlank { task.assignee }, parentId = task.id, projectId = task.projectId, createdAt = base + 1 + i))
        }
        toast("Created from template \"${t.name}\"")
        return task
    }

    fun meetingFromTemplate(t: com.kargroup.planner.desktop.MomTemplate): MeetingMinuteEntity = MeetingMinuteEntity(
        title = t.title,
        meetingDate = Ist.todayStr(),
        meetingTime = t.meetingTime,
        durationMinutes = t.durationMinutes,
        attendees = t.attendees,
        agenda = t.agenda,
        projectId = t.projectId,
    )

    companion object {
        private const val KEY_SCHEDULE_MANUAL = "schedule_manual"
    }
}
