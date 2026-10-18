package com.kargroup.planner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY created_at ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY created_at ASC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE meeting_id = :meetingId")
    suspend fun getByMeeting(meetingId: String): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE tasks SET assignee = :newName WHERE LOWER(TRIM(assignee)) = LOWER(TRIM(:oldName))")
    suspend fun renameAssignee(oldName: String, newName: String?)

    @Query("UPDATE tasks SET planned_start = :start WHERE id = :id")
    suspend fun setPlannedStart(id: String, start: Long?)

    @Query("UPDATE tasks SET completion_remarks = :remarks WHERE id = :id")
    suspend fun setRemarks(id: String, remarks: String?)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String?)

    @Query("UPDATE tasks SET manual_order = :order WHERE id = :id")
    suspend fun setManualOrder(id: String, order: Long?)

    @Query("UPDATE tasks SET is_collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: String, collapsed: Boolean)

    @Query("UPDATE tasks SET project_id = NULL WHERE project_id = :projectId")
    suspend fun clearProject(projectId: String)

    @Query("UPDATE tasks SET context_id = NULL WHERE context_id = :contextId")
    suspend fun clearContext(contextId: String)

    @Query("UPDATE tasks SET meeting_id = NULL, meeting_action_item_id = NULL WHERE meeting_id = :meetingId")
    suspend fun unlinkMeeting(meetingId: String)

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY created_at ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Upsert
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun count(): Int

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface ContextDao {
    @Query("SELECT * FROM contexts ORDER BY name ASC")
    fun observeAll(): Flow<List<ContextTagEntity>>

    @Query("SELECT * FROM contexts")
    suspend fun getAll(): List<ContextTagEntity>

    @Upsert
    suspend fun upsert(context: ContextTagEntity)

    @Upsert
    suspend fun upsertAll(contexts: List<ContextTagEntity>)

    @Query("DELETE FROM contexts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM contexts")
    suspend fun count(): Int

    @Query("DELETE FROM contexts")
    suspend fun deleteAll()
}

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meeting_minutes ORDER BY meeting_date DESC, meeting_time DESC")
    fun observeAll(): Flow<List<MeetingMinuteEntity>>

    @Query("SELECT * FROM meeting_minutes")
    suspend fun getAll(): List<MeetingMinuteEntity>

    @Query("SELECT * FROM meeting_minutes WHERE id = :id")
    suspend fun getById(id: String): MeetingMinuteEntity?

    @Upsert
    suspend fun upsert(meeting: MeetingMinuteEntity)

    @Upsert
    suspend fun upsertAll(meetings: List<MeetingMinuteEntity>)

    @Query("DELETE FROM meeting_minutes WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE meeting_minutes SET project_id = NULL WHERE project_id = :projectId")
    suspend fun clearProject(projectId: String)

    @Query("SELECT COUNT(*) FROM meeting_minutes")
    suspend fun count(): Int

    @Query("DELETE FROM meeting_minutes")
    suspend fun deleteAll()
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY entry_date DESC")
    fun observeAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries")
    suspend fun getAll(): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE entry_date = :date LIMIT 1")
    suspend fun getByDate(date: String): JournalEntry?

    @Upsert
    suspend fun upsert(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun count(): Int

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons")
    suspend fun getAll(): List<PersonEntity>

    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Upsert
    suspend fun upsertAll(persons: List<PersonEntity>)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int

    @Query("DELETE FROM persons")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM task_history WHERE task_id = :taskId ORDER BY at DESC")
    fun observeForTask(taskId: String): Flow<List<TaskHistoryEntity>>

    @Query("SELECT * FROM task_history")
    suspend fun getAll(): List<TaskHistoryEntity>

    @Upsert
    suspend fun upsert(entry: TaskHistoryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<TaskHistoryEntity>)

    @Query("DELETE FROM task_history WHERE task_id IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<String>)

    @Query("DELETE FROM task_history")
    suspend fun deleteAll()
}
