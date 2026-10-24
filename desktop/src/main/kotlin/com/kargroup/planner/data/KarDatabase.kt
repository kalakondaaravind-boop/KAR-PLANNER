package com.kargroup.planner.data

import androidx.room.RoomDatabase
import com.kargroup.planner.desktop.AppDirs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/**
 * Desktop database: the same DAO interfaces as the tablet, kept in memory and saved to
 * one JSON file (the same format as the tablet's backup) shortly after every change.
 * A dated copy is kept for each day in the backups folder (newest 30).
 */
class KarDatabase private constructor(private val file: File) : RoomDatabase() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val tasks = Table<TaskEntity> { it.id }
    private val projects = Table<ProjectEntity> { it.id }
    private val contexts = Table<ContextTagEntity> { it.id }
    private val meetings = Table<MeetingMinuteEntity> { it.id }
    private val journal = Table<JournalEntry> { it.id }
    private val persons = Table<PersonEntity> { it.id }
    private val history = Table<TaskHistoryEntity> { it.id }

    /** One table: rows by id, observable. */
    private inner class Table<T>(val idOf: (T) -> String) {
        val state = MutableStateFlow<Map<String, T>>(emptyMap())
        val all: Collection<T> get() = state.value.values
        fun get(id: String): T? = state.value[id]
        fun upsert(items: Collection<T>) {
            if (items.isEmpty()) return
            state.value = state.value + items.associateBy(idOf)
            changed()
        }
        fun removeIf(pred: (T) -> Boolean) {
            val before = state.value
            val after = before.filterValues { !pred(it) }
            if (after.size != before.size) { state.value = after; changed() }
        }
        fun updateIf(pred: (T) -> Boolean, f: (T) -> T) {
            var touched = false
            val after = state.value.mapValues { (_, v) -> if (pred(v)) { touched = true; f(v) } else v }
            if (touched) { state.value = after; changed() }
        }
        fun clear() { if (state.value.isNotEmpty()) { state.value = emptyMap(); changed() } }
        fun observe(sort: (Collection<T>) -> List<T>): Flow<List<T>> = state.map { sort(it.values) }
        fun load(items: Collection<T>) { state.value = items.associateBy(idOf) }
    }

    private fun changed() { changes.tryEmit(Unit) }

    // ------------------------------------------------------------------ persistence

    @OptIn(FlowPreview::class)
    private fun startAutoSave() {
        scope.launch { changes.debounce(400).collect { saveNow() } }
    }

    fun snapshot(): BackupSnapshot = BackupSnapshot(
        tasks = tasks.all.toList(),
        projects = projects.all.toList(),
        contexts = contexts.all.toList(),
        meetings = meetings.all.toList(),
        journal = journal.all.toList(),
        persons = persons.all.toList(),
        history = history.all.toList(),
    )

    @Synchronized
    fun saveNow() {
        runCatching {
            val json = BackupCodec.encode(snapshot())
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            dailyCopy()
        }
    }

    private fun dailyCopy() {
        val dir = AppDirs.backupsDir
        val today = File(dir, "KAR-Planner-${LocalDate.now()}.json")
        runCatching { Files.copy(file.toPath(), today.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        dir.listFiles { f -> f.name.startsWith("KAR-Planner-") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
    }

    private fun load() {
        if (!file.exists()) return
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val snap = runCatching { BackupCodec.decode(text) }.getOrNull() ?: return
        tasks.load(snap.tasks)
        projects.load(snap.projects)
        contexts.load(snap.contexts)
        meetings.load(snap.meetings)
        journal.load(snap.journal)
        persons.load(snap.persons)
        history.load(snap.history)
    }

    // ------------------------------------------------------------------------ DAOs

    private val taskDao = object : TaskDao {
        override fun observeAll() = tasks.observe { c -> c.sortedBy { it.createdAt } }
        override suspend fun getAll() = tasks.all.sortedBy { it.createdAt }
        override suspend fun getById(id: String) = tasks.get(id)
        override suspend fun getByMeeting(meetingId: String) = tasks.all.filter { it.meetingId == meetingId }
        override suspend fun upsert(task: TaskEntity) = tasks.upsert(listOf(task))
        override suspend fun upsertAll(tasks: List<TaskEntity>) = this@KarDatabase.tasks.upsert(tasks)
        override suspend fun deleteByIds(ids: List<String>) { val set = ids.toHashSet(); tasks.removeIf { it.id in set } }
        override suspend fun renameAssignee(oldName: String, newName: String?) {
            val key = oldName.trim().lowercase()
            tasks.updateIf({ it.assignee?.trim()?.lowercase() == key }) { it.copy(assignee = newName) }
        }
        override suspend fun setPlannedStart(id: String, start: Long?) = tasks.updateIf({ it.id == id }) { it.copy(plannedStart = start) }
        override suspend fun setRemarks(id: String, remarks: String?) = tasks.updateIf({ it.id == id }) { it.copy(completionRemarks = remarks) }
        override suspend fun setStatus(id: String, status: String?) = tasks.updateIf({ it.id == id }) { it.copy(status = status) }
        override suspend fun setManualOrder(id: String, order: Long?) = tasks.updateIf({ it.id == id }) { it.copy(manualOrder = order) }
        override suspend fun setCollapsed(id: String, collapsed: Boolean) = tasks.updateIf({ it.id == id }) { it.copy(isCollapsed = collapsed) }
        override suspend fun clearProject(projectId: String) = tasks.updateIf({ it.projectId == projectId }) { it.copy(projectId = null) }
        override suspend fun clearContext(contextId: String) = tasks.updateIf({ it.contextId == contextId }) { it.copy(contextId = null) }
        override suspend fun unlinkMeeting(meetingId: String) =
            tasks.updateIf({ it.meetingId == meetingId }) { it.copy(meetingId = null, meetingActionItemId = null) }
        override suspend fun count() = tasks.all.size
        override suspend fun deleteAll() = tasks.clear()
    }

    private val projectDao = object : ProjectDao {
        override fun observeAll() = projects.observe { c -> c.sortedBy { it.createdAt } }
        override suspend fun getAll() = projects.all.toList()
        override suspend fun upsert(project: ProjectEntity) = projects.upsert(listOf(project))
        override suspend fun upsertAll(projects: List<ProjectEntity>) = this@KarDatabase.projects.upsert(projects)
        override suspend fun delete(id: String) = projects.removeIf { it.id == id }
        override suspend fun count() = projects.all.size
        override suspend fun deleteAll() = projects.clear()
    }

    private val contextDao = object : ContextDao {
        override fun observeAll() = contexts.observe { c -> c.sortedBy { it.name } }
        override suspend fun getAll() = contexts.all.toList()
        override suspend fun upsert(context: ContextTagEntity) = contexts.upsert(listOf(context))
        override suspend fun upsertAll(contexts: List<ContextTagEntity>) = this@KarDatabase.contexts.upsert(contexts)
        override suspend fun delete(id: String) = contexts.removeIf { it.id == id }
        override suspend fun count() = contexts.all.size
        override suspend fun deleteAll() = contexts.clear()
    }

    private val meetingDao = object : MeetingDao {
        override fun observeAll() = meetings.observe { c ->
            c.sortedWith(compareByDescending<MeetingMinuteEntity> { it.meetingDate }.thenByDescending { it.meetingTime })
        }
        override suspend fun getAll() = meetings.all.toList()
        override suspend fun getById(id: String) = meetings.get(id)
        override suspend fun upsert(meeting: MeetingMinuteEntity) = meetings.upsert(listOf(meeting))
        override suspend fun upsertAll(meetings: List<MeetingMinuteEntity>) = this@KarDatabase.meetings.upsert(meetings)
        override suspend fun delete(id: String) = meetings.removeIf { it.id == id }
        override suspend fun clearProject(projectId: String) = meetings.updateIf({ it.projectId == projectId }) { it.copy(projectId = null) }
        override suspend fun count() = meetings.all.size
        override suspend fun deleteAll() = meetings.clear()
    }

    private val journalDao = object : JournalDao {
        override fun observeAll() = journal.observe { c -> c.sortedByDescending { it.date } }
        override suspend fun getAll() = journal.all.toList()
        override suspend fun getByDate(date: String) = journal.all.firstOrNull { it.date == date }
        override suspend fun upsert(entry: JournalEntry) {
            // entry_date is unique: replace any other entry for the same day
            journal.removeIf { it.date == entry.date && it.id != entry.id }
            journal.upsert(listOf(entry))
        }
        override suspend fun delete(id: String) = journal.removeIf { it.id == id }
        override suspend fun count() = journal.all.size
        override suspend fun deleteAll() = journal.clear()
    }

    private val personDao = object : PersonDao {
        override fun observeAll() = persons.observe { c -> c.sortedBy { it.name.lowercase() } }
        override suspend fun getAll() = persons.all.toList()
        override suspend fun upsert(person: PersonEntity) = persons.upsert(listOf(person))
        override suspend fun upsertAll(persons: List<PersonEntity>) = this@KarDatabase.persons.upsert(persons)
        override suspend fun delete(id: String) = persons.removeIf { it.id == id }
        override suspend fun count() = persons.all.size
        override suspend fun deleteAll() = persons.clear()
    }

    private val historyDao = object : HistoryDao {
        override fun observeForTask(taskId: String) =
            history.observe { c -> c.filter { it.taskId == taskId }.sortedByDescending { it.at } }
        override suspend fun getAll() = history.all.toList()
        override suspend fun upsert(entry: TaskHistoryEntity) = history.upsert(listOf(entry))
        override suspend fun upsertAll(entries: List<TaskHistoryEntity>) = history.upsert(entries)
        override suspend fun deleteForTasks(taskIds: List<String>) { val set = taskIds.toHashSet(); history.removeIf { it.taskId in set } }
        override suspend fun deleteAll() = history.clear()
    }

    fun taskDao(): TaskDao = taskDao
    fun projectDao(): ProjectDao = projectDao
    fun contextDao(): ContextDao = contextDao
    fun meetingDao(): MeetingDao = meetingDao
    fun journalDao(): JournalDao = journalDao
    fun personDao(): PersonDao = personDao
    fun historyDao(): HistoryDao = historyDao

    companion object {
        fun open(file: File = AppDirs.dataFile): KarDatabase =
            KarDatabase(file).apply { load(); startAutoSave() }
    }
}
