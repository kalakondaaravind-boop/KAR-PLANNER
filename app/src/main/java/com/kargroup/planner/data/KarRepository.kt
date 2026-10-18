package com.kargroup.planner.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.kargroup.planner.util.Ist
import com.kargroup.planner.util.Recurrence
import kotlinx.coroutines.flow.Flow

class KarRepository(private val db: KarDatabase) {

    private val taskDao = db.taskDao()
    private val projectDao = db.projectDao()
    private val contextDao = db.contextDao()
    private val meetingDao = db.meetingDao()
    private val journalDao = db.journalDao()
    private val personDao = db.personDao()
    private val historyDao = db.historyDao()

    fun historyFor(taskId: String): Flow<List<TaskHistoryEntity>> = historyDao.observeForTask(taskId)

    private suspend fun log(taskId: String, kind: String, detail: String?) {
        historyDao.upsert(TaskHistoryEntity(taskId = taskId, kind = kind, detail = detail))
    }

    private fun targetText(date: String?, time: String?): String =
        if (date == null) "no date" else Ist.formatFull(date) + (time?.let { " $it" } ?: "")

    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val projects: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val contexts: Flow<List<ContextTagEntity>> = contextDao.observeAll()
    val meetings: Flow<List<MeetingMinuteEntity>> = meetingDao.observeAll()
    val journal: Flow<List<JournalEntry>> = journalDao.observeAll()
    val persons: Flow<List<PersonEntity>> = personDao.observeAll()

    // ----------------------------------------------------------------- people

    /** Saves a person; a rename is carried through to their activities and MoM action items. */
    suspend fun savePerson(person: PersonEntity, oldName: String?) {
        db.withTransaction {
            personDao.upsert(person)
            if (oldName != null && !oldName.equals(person.name, ignoreCase = false)) {
                taskDao.renameAssignee(oldName, person.name)
                renameInMeetings(oldName, person.name)
            }
        }
    }

    /** Removes a person; their activities become unassigned. */
    suspend fun deletePerson(person: PersonEntity) {
        db.withTransaction {
            taskDao.renameAssignee(person.name, null)
            renameInMeetings(person.name, "")
            personDao.delete(person.id)
        }
    }

    private suspend fun renameInMeetings(oldName: String, newName: String) {
        meetingDao.getAll().forEach { m ->
            if (m.actionItems.any { it.assignee.trim().equals(oldName.trim(), ignoreCase = true) }) {
                meetingDao.upsert(
                    m.copy(actionItems = m.actionItems.map {
                        if (it.assignee.trim().equals(oldName.trim(), ignoreCase = true)) it.copy(assignee = newName) else it
                    })
                )
            }
        }
    }

    // ---------------------------------------------------------------- journal

    /** Saves the entry for a date; an entry left completely empty is removed. */
    suspend fun saveJournal(date: String, mood: String?, title: String?, content: String) {
        val existing = journalDao.getByDate(date)
        val empty = mood == null && title.isNullOrBlank() && content.isBlank()
        if (empty) {
            if (existing != null) journalDao.delete(existing.id)
            return
        }
        val now = System.currentTimeMillis()
        journalDao.upsert(
            (existing ?: JournalEntry(date = date, createdAt = now)).copy(
                mood = mood,
                title = title?.trim()?.ifBlank { null },
                content = content.trimEnd(),
                updatedAt = now,
            )
        )
    }

    suspend fun deleteJournal(id: String) = journalDao.delete(id)

    suspend fun allTasksOnce(): List<TaskEntity> = taskDao.getAll()

    // ---------------------------------------------------------------- seeding

    suspend fun seedDefaultsIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    // ------------------------------------------------------------------ tasks

    /** Saves an activity and records what changed in its history. */
    suspend fun saveTask(task: TaskEntity, rescheduleReason: String? = null) {
        val old = taskDao.getById(task.id)
        taskDao.upsert(task)
        if (old == null) {
            log(task.id, HistoryKind.CREATED, buildString {
                append("Created")
                task.assignee?.let { append(" · assigned to $it") }
                if (task.dueDate != null) append(" · target ${targetText(task.dueDate, task.dueTime)}")
            })
            return
        }
        if ((old.assignee ?: "") != (task.assignee ?: "")) {
            log(task.id, HistoryKind.ASSIGNED, "Assigned: ${old.assignee ?: "Unassigned"} → ${task.assignee ?: "Unassigned"}")
        }
        if (old.dueDate != task.dueDate || old.dueTime != task.dueTime) {
            log(task.id, HistoryKind.RESCHEDULED, buildString {
                append("Target: ${targetText(old.dueDate, old.dueTime)} → ${targetText(task.dueDate, task.dueTime)}")
                if (!rescheduleReason.isNullOrBlank()) append(" · Reason: ${rescheduleReason.trim()}")
            })
        }
        if ((old.status ?: TaskStatus.NOT_STARTED) != (task.status ?: TaskStatus.NOT_STARTED) && !task.done) {
            log(task.id, HistoryKind.STATUS, "Status: ${TaskStatus.label(old.status ?: TaskStatus.NOT_STARTED)} → ${TaskStatus.label(task.status ?: TaskStatus.NOT_STARTED)}")
        }
        val changed = buildList {
            if (old.text != task.text) add("title")
            if (old.priority != task.priority) add("priority")
            if (old.estimatedMinutes != task.estimatedMinutes) add("estimate")
            if (old.notes != task.notes) add("notes")
            if (old.projectId != task.projectId) add("project")
            if (old.isRecurring != task.isRecurring || old.recurrenceFrequency != task.recurrenceFrequency) add("recurrence")
        }
        if (changed.isNotEmpty()) log(task.id, HistoryKind.UPDATED, "Updated " + changed.joinToString(", "))
    }

    /** Moves an (overdue) activity to a new target with a compulsory reason. */
    suspend fun reschedule(taskId: String, newDate: String, newTime: String?, reason: String) {
        val t = taskDao.getById(taskId) ?: return
        saveTask(t.copy(dueDate = newDate, dueTime = newTime, plannedStart = null), reason)
    }

    /** Fixes (or clears) an activity's start time in the Scheduler. */
    suspend fun setPlannedStart(taskId: String, start: Long?) {
        taskDao.setPlannedStart(taskId, start)
        log(taskId, HistoryKind.UPDATED, if (start == null) "Scheduler time released" else "Scheduled for ${Ist.formatStamp(start)}")
    }

    suspend fun setCollapsed(id: String, collapsed: Boolean) = taskDao.setCollapsed(id, collapsed)

    /** Completes an activity with the remarks the user entered. */
    suspend fun completeWithRemarks(taskId: String, remarks: String, includeSubtasks: Boolean) {
        taskDao.setRemarks(taskId, remarks.trim())
        setDone(taskId, true, includeSubtasks)
    }

    /** Sets Not Started / Initiated on an activity (Completed goes through [setDone]). */
    suspend fun setStatus(id: String, status: String) {
        val old = taskDao.getById(id)
        taskDao.setStatus(id, status)
        if (old != null && (old.status ?: TaskStatus.NOT_STARTED) != status) {
            log(id, HistoryKind.STATUS, "Status: ${TaskStatus.label(old.effectiveStatus)} → ${TaskStatus.label(status)}")
        }
    }

    /** Saves the scheduler's manual order: position i gets order (i + 1) * 10. */
    suspend fun saveManualOrder(orderedIds: List<String>) {
        db.withTransaction {
            orderedIds.forEachIndexed { i, id -> taskDao.setManualOrder(id, (i + 1) * 10L) }
        }
    }

    suspend fun setDone(taskId: String, done: Boolean, includeSubtasks: Boolean) {
        db.withTransaction { setDoneInternal(taskId, done, includeSubtasks) }
    }

    private suspend fun setDoneInternal(taskId: String, done: Boolean, includeSubtasks: Boolean) {
        val all = taskDao.getAll()
        val task = all.firstOrNull { it.id == taskId } ?: return
        val now = System.currentTimeMillis()

        if (!done) {
            taskDao.upsert(task.copy(done = false, completedAt = null))
            syncActionItem(task, false)
            if (task.done) log(task.id, HistoryKind.REOPENED, "Reopened")
            return
        }
        if (!task.done) {
            log(task.id, HistoryKind.COMPLETED, "Completed" + (task.completionRemarks?.let { " · Remarks: $it" } ?: ""))
        }

        val descendants = descendantsOf(all, taskId)

        if (task.isRecurring) {
            // 1. Archive the current instance (and its open subtasks) as completed.
            taskDao.upsert(task.copy(done = true, completedAt = now, meetingActionItemId = null))
            taskDao.upsertAll(descendants.filter { !it.done }.map { it.copy(done = true, completedAt = now) })

            // 2. Create the next instance with the next IST due date, cloning the subtree.
            val next = task.copy(
                id = newId(),
                done = false,
                completedAt = null,
                createdAt = now,
                dueDate = Recurrence.nextDueDate(task).toString(),
                status = TaskStatus.NOT_STARTED,
                completionRemarks = null,
            )
            val idMap = HashMap<String, String>()
            idMap[task.id] = next.id
            val clones = ArrayList<TaskEntity>()
            clones.add(next)
            descendants.forEach { d ->
                val newChildId = newId()
                idMap[d.id] = newChildId
                clones.add(
                    d.copy(
                        id = newChildId,
                        parentId = d.parentId?.let { idMap[it] } ?: next.id,
                        done = false,
                        completedAt = null,
                        createdAt = now,
                        meetingActionItemId = null,
                        completionRemarks = null,
                    )
                )
            }
            taskDao.upsertAll(clones)
            log(next.id, HistoryKind.CREATED, "Next recurrence created · target ${targetText(next.dueDate, next.dueTime)}")
            return
        }

        taskDao.upsert(task.copy(done = true, completedAt = now))
        if (includeSubtasks) {
            taskDao.upsertAll(descendants.filter { !it.done }.map { it.copy(done = true, completedAt = now) })
            descendants.filter { !it.done }.forEach { syncActionItem(it, true) }
        }
        syncActionItem(task, true)
    }

    /** Keeps a meeting's action item "completed" flag in step with its synced task. */
    private suspend fun syncActionItem(task: TaskEntity, done: Boolean) {
        val meetingId = task.meetingId ?: return
        val itemId = task.meetingActionItemId ?: return
        val meeting = meetingDao.getById(meetingId) ?: return
        val updated = meeting.actionItems.map { if (it.id == itemId) it.copy(completed = done) else it }
        if (updated != meeting.actionItems) meetingDao.upsert(meeting.copy(actionItems = updated))
    }

    /** Deletes a task and its whole subtree. */
    suspend fun deleteTask(taskId: String) {
        db.withTransaction { deleteTaskInternal(taskId) }
    }

    private suspend fun deleteTaskInternal(taskId: String) {
        val all = taskDao.getAll()
        val root = all.firstOrNull { it.id == taskId } ?: return
        val doomed = descendantsOf(all, taskId) + root
        doomed.map { it.id }.chunked(500).forEach {
            taskDao.deleteByIds(it)
            historyDao.deleteForTasks(it)
        }

        // Remove matching action items from their meetings so they are not re-created.
        doomed.filter { it.meetingId != null && it.meetingActionItemId != null }
            .groupBy { it.meetingId!! }
            .forEach { (meetingId, linked) ->
                val meeting = meetingDao.getById(meetingId) ?: return@forEach
                val removeIds = linked.mapNotNull { it.meetingActionItemId }.toSet()
                meetingDao.upsert(meeting.copy(actionItems = meeting.actionItems.filter { it.id !in removeIds }))
            }
    }

    // --------------------------------------------------------------- projects

    suspend fun saveProject(project: ProjectEntity) = projectDao.upsert(project)

    suspend fun deleteProject(id: String) {
        db.withTransaction {
            taskDao.clearProject(id)
            meetingDao.clearProject(id)
            projectDao.delete(id)
        }
    }

    // --------------------------------------------------------------- contexts

    suspend fun saveContext(context: ContextTagEntity) = contextDao.upsert(context)

    suspend fun deleteContext(id: String) {
        db.withTransaction {
            taskDao.clearContext(id)
            contextDao.delete(id)
        }
    }

    // --------------------------------------------------------------- meetings

    /** Saves the meeting and cross-syncs every action item into a high-priority planner task. */
    suspend fun saveMeeting(meeting: MeetingMinuteEntity) {
        db.withTransaction { saveMeetingInternal(meeting) }
    }

    private suspend fun saveMeetingInternal(meeting: MeetingMinuteEntity) {
        val cleaned = meeting.copy(actionItems = meeting.actionItems.filter { it.description.isNotBlank() })
        meetingDao.upsert(cleaned)

        val now = System.currentTimeMillis()
        val linked = taskDao.getByMeeting(cleaned.id).filter { it.meetingActionItemId != null }
        val byItem = linked.associateBy { it.meetingActionItemId }

        cleaned.actionItems.forEach { item ->
            val title = item.description.trim()
            val person = item.assignee.trim().ifBlank { null }
            val due = item.dueDate?.takeIf { it.isNotBlank() }
            val existing = byItem[item.id]
            if (existing == null) {
                saveTask(
                    TaskEntity(
                        text = title,
                        assignee = person,
                        notes = "Action item from MoM: ${cleaned.title} (${cleaned.meetingDate})",
                        priority = Priority.HIGH,
                        dueDate = due,
                        projectId = cleaned.projectId,
                        meetingId = cleaned.id,
                        meetingActionItemId = item.id,
                        done = item.completed,
                        completedAt = if (item.completed) now else null,
                    )
                )
            } else {
                taskDao.upsert(
                    existing.copy(
                        text = title,
                        assignee = person,
                        dueDate = due,
                        projectId = cleaned.projectId ?: existing.projectId,
                        done = item.completed,
                        completedAt = if (item.completed) (existing.completedAt ?: now) else null,
                    )
                )
            }
        }

        val keepIds = cleaned.actionItems.map { it.id }.toSet()
        val orphanIds = linked.filter { it.meetingActionItemId !in keepIds }.map { it.id }
        if (orphanIds.isNotEmpty()) taskDao.deleteByIds(orphanIds)
    }

    /** Deletes the meeting; synced tasks are kept but unlinked. */
    suspend fun deleteMeeting(id: String) {
        db.withTransaction {
            taskDao.unlinkMeeting(id)
            meetingDao.delete(id)
        }
    }

    // ------------------------------------------------------ backup & wipe

    suspend fun snapshot(): BackupSnapshot = BackupSnapshot(
        tasks = taskDao.getAll(),
        projects = projectDao.getAll(),
        contexts = contextDao.getAll(),
        meetings = meetingDao.getAll(),
        journal = journalDao.getAll(),
        persons = personDao.getAll(),
        history = historyDao.getAll(),
    )

    /** Merges a backup into the database (records with the same id are replaced). */
    suspend fun importSnapshot(snapshot: BackupSnapshot) {
        db.withTransaction {
            projectDao.upsertAll(snapshot.projects)
            // People: skip names that already exist (case-insensitive).
            val known = personDao.getAll().map { it.name.trim().lowercase() }.toMutableSet()
            snapshot.persons.forEach { p -> if (known.add(p.name.trim().lowercase())) personDao.upsert(p) }
            contextDao.upsertAll(snapshot.contexts)
            meetingDao.upsertAll(snapshot.meetings)
            snapshot.tasks.chunked(300).forEach { taskDao.upsertAll(it) }
            snapshot.history.chunked(300).forEach { historyDao.upsertAll(it) }
            // One entry per date: an imported entry replaces the one already on that date.
            snapshot.journal.forEach { e ->
                val existing = journalDao.getByDate(e.date)
                journalDao.upsert(e.copy(id = existing?.id ?: e.id))
            }
        }
    }

    suspend fun counts(): DataCounts = DataCounts(
        tasks = taskDao.count(),
        projects = projectDao.count(),
        contexts = contextDao.count(),
        meetings = meetingDao.count(),
        journal = journalDao.count(),
        persons = personDao.count(),
    )

    suspend fun deleteAll() {
        db.withTransaction {
            taskDao.deleteAll()
            projectDao.deleteAll()
            contextDao.deleteAll()
            meetingDao.deleteAll()
            journalDao.deleteAll()
            personDao.deleteAll()
            historyDao.deleteAll()
        }
    }

    companion object {
        private const val KEY_SEEDED = "defaults_seeded"

        /** All descendants of [rootId]; every parent appears before its children. */
        fun descendantsOf(all: List<TaskEntity>, rootId: String): List<TaskEntity> {
            val byParent = all.groupBy { it.parentId }
            val out = ArrayList<TaskEntity>()
            val seen = HashSet<String>()
            seen.add(rootId)
            val queue = ArrayDeque<String>()
            queue.addLast(rootId)
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                byParent[id].orEmpty().forEach { child ->
                    if (seen.add(child.id)) {
                        out.add(child)
                        queue.addLast(child.id)
                    }
                }
            }
            return out
        }
    }
}
