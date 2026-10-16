package com.kargroup.planner.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kargroup.planner.KarApp
import com.kargroup.planner.data.BackupCodec
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.DataCounts
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.JournalEntry
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.reminders.ReminderNotifications
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

sealed interface Screen {
    data object Schedule : Screen
    data object Today : Screen
    data object Inbox : Screen
    data object Overdue : Screen
    data object Upcoming : Screen
    data object Recurring : Screen
    data object AllTasks : Screen
    data object Completed : Screen
    data object Projects : Screen
    data object Meetings : Screen
    data object Journal : Screen
    data object People : Screen
    /** Review page for one person; [name] is null for "Unassigned". */
    data class Person(val name: String?) : Screen
    data class Project(val id: String) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val karApp = app as KarApp
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

    val screen = MutableStateFlow<Screen>(Screen.Schedule)

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

    private fun toast(msg: String) {
        _messages.trySend(msg)
    }

    // ------------------------------------------------------------ navigation

    fun navigate(target: Screen) {
        screen.value = target
        drillRootId.value = null
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
    ) = viewModelScope.launch {
        repo.saveTask(task)
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
        ReminderNotifications.scheduleOneOff(getApplication<Application>(), 61)
        toast("Snoozed for 1 hour")
    }

    fun dismiss(ids: Collection<String>) {
        if (ids.isEmpty()) return
        reminderPrefs.dismiss(ids)
        toast("Reminder dismissed for today")
    }

    // ------------------------------------------------------- backup & restore

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val json = BackupCodec.encode(repo.snapshot())
                val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: error("Could not open the selected file")
                stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        }
        result.onSuccess { toast("Backup exported successfully") }
            .onFailure { toast("Export failed: ${it.message}") }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Could not open the selected file")
                val snapshot = BackupCodec.decode(text)
                repo.importSnapshot(snapshot)
                snapshot
            }
        }
        result.onSuccess {
            toast("Imported ${it.tasks.size} activities, ${it.projects.size} projects, ${it.contexts.size} contexts, ${it.meetings.size} meetings")
        }.onFailure { toast("Import failed: ${it.message}") }
    }

    suspend fun counts(): DataCounts = repo.counts()

    fun deleteAllData() = viewModelScope.launch {
        repo.deleteAll()
        reminderPrefs.clear()
        navigate(Screen.Today)
        toast("All data deleted. Fresh start!")
    }

    companion object {
        private const val KEY_SCHEDULE_MANUAL = "schedule_manual"
    }
}
