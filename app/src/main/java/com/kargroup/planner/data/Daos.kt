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
