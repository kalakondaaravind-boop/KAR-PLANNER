package com.kargroup.planner.data

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.kargroup.planner.util.Recurrence
import kotlinx.coroutines.flow.Flow

class KarRepository(private val db: KarDatabase) {

    private val taskDao = db.taskDao()
    private val projectDao = db.projectDao()
    private val contextDao = db.contextDao()
    private val meetingDao = db.meetingDao()

    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val projects: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val contexts: Flow<List<ContextTagEntity>> = contextDao.observeAll()
    val meetings: Flow<List<MeetingMinuteEntity>> = meetingDao.observeAll()

    suspend fun allTasksOnce(): List<TaskEntity> = taskDao.getAll()

    // ---------------------------------------------------------------- seeding

    suspend fun seedDefaultsIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (contextDao.count() == 0) {
            contextDao.upsertAll(
                listOf(
                    ContextTagEntity(name = "@deep-work", color = "#1E3A6E", icon = "bolt"),
                    ContextTagEntity(name = "@calls", color = "#10B981", icon = "phone"),
                    ContextTagEntity(name = "@meetings", color = "#F5A623", icon = "groups"),
                    ContextTagEntity(name = "@desk", color = "#64748B", icon = "desk"),
                    ContextTagEntity(name = "@errands", color = "#EF4444", icon = "cart"),
                )
            )
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    // ------------------------------------------------------------------ tasks

    suspend fun saveTask(task: TaskEntity) = taskDao.upsert(task)

    suspend fun setCollapsed(id: String, collapsed: Boolean) = taskDao.setCollapsed(id, collapsed)

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
            return
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
                    )
                )
            }
            taskDao.upsertAll(clones)
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
        doomed.map { it.id }.chunked(500).forEach { taskDao.deleteByIds(it) }

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
            val title = buildString {
                append(item.description.trim())
                if (item.assignee.isNotBlank()) append(" — ").append(item.assignee.trim())
            }
            val due = item.dueDate?.takeIf { it.isNotBlank() }
            val existing = byItem[item.id]
            if (existing == null) {
                taskDao.upsert(
                    TaskEntity(
                        text = title,
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
    )

    /** Merges a backup into the database (records with the same id are replaced). */
    suspend fun importSnapshot(snapshot: BackupSnapshot) {
        db.withTransaction {
            projectDao.upsertAll(snapshot.projects)
            contextDao.upsertAll(snapshot.contexts)
            meetingDao.upsertAll(snapshot.meetings)
            snapshot.tasks.chunked(300).forEach { taskDao.upsertAll(it) }
        }
    }

    suspend fun counts(): DataCounts = DataCounts(
        tasks = taskDao.count(),
        projects = projectDao.count(),
        contexts = contextDao.count(),
        meetings = meetingDao.count(),
    )

    suspend fun deleteAll() {
        db.withTransaction {
            taskDao.deleteAll()
            projectDao.deleteAll()
            contextDao.deleteAll()
            meetingDao.deleteAll()
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
