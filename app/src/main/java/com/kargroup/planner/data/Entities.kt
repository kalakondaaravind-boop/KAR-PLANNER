package com.kargroup.planner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

object Priority {
    const val VERY_HIGH = "very_high"
    const val HIGH = "high"
    const val MEDIUM = "medium"
    val ALL = listOf(VERY_HIGH, HIGH, MEDIUM)

    /** 0 = most urgent. */
    fun rank(p: String?): Int = when (p) {
        VERY_HIGH -> 0
        HIGH -> 1
        MEDIUM -> 2
        else -> 3
    }

    fun isHighOrAbove(p: String?): Boolean = p == VERY_HIGH || p == HIGH
}

object TaskStatus {
    const val NOT_STARTED = "not_started"
    const val INITIATED = "initiated"
    const val COMPLETED = "completed"
    val ALL = listOf(NOT_STARTED, INITIATED, COMPLETED)

    fun label(s: String): String = when (s) {
        INITIATED -> "Initiated"
        COMPLETED -> "Completed"
        else -> "Not Started"
    }
}

/** Activities estimated below this many minutes are scheduled first ("quick wins"). */
const val QUICK_TASK_MINUTES = 10

object Frequency {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val BIWEEKLY = "biweekly"
    const val MONTHLY = "monthly"
    val ALL = listOf(DAILY, WEEKLY, BIWEEKLY, MONTHLY)
}

@Entity(
    tableName = "tasks",
    indices = [Index("parent_id"), Index("project_id"), Index("context_id"), Index("meeting_id")]
)
data class TaskEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    @ColumnInfo(name = "context_id") val contextId: String? = null,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "done") val done: Boolean = false,
    @ColumnInfo(name = "priority") val priority: String? = null,
    /** 'YYYY-MM-DD' in IST */
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    /** Target time 'HH:mm' in IST (optional). */
    @ColumnInfo(name = "due_time") val dueTime: String? = null,
    /** Estimated effort in minutes (optional). */
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int? = null,
    /** Position in the scheduler when manual sorting is used. */
    @ColumnInfo(name = "manual_order") val manualOrder: Long? = null,
    /** Work status while open: not_started / initiated. "Completed" is derived from [done]. */
    @ColumnInfo(name = "status") val status: String? = null,
    /** Person the activity is assigned to (optional). */
    @ColumnInfo(name = "assignee") val assignee: String? = null,
    @ColumnInfo(name = "is_recurring") val isRecurring: Boolean = false,
    @ColumnInfo(name = "recurrence_frequency") val recurrenceFrequency: String? = null,
    @ColumnInfo(name = "recurrence_interval") val recurrenceInterval: Int = 1,
    /** ISO day numbers: 1 = Monday ... 7 = Sunday */
    @ColumnInfo(name = "recurrence_days_of_week") val recurrenceDaysOfWeek: List<Int>? = null,
    @ColumnInfo(name = "meeting_id") val meetingId: String? = null,
    /** Links this task to one action item inside a MeetingMinute (for two-way sync). */
    @ColumnInfo(name = "meeting_action_item_id") val meetingActionItemId: String? = null,
    @ColumnInfo(name = "is_collapsed") val isCollapsed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)

/** Not Started / Initiated / Completed - Completed always follows the done checkbox. */
val TaskEntity.effectiveStatus: String
    get() = when {
        done -> TaskStatus.COMPLETED
        status == TaskStatus.INITIATED -> TaskStatus.INITIATED
        else -> TaskStatus.NOT_STARTED
    }

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "color") val color: String = "#1E3A6E",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "contexts")
data class ContextTagEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "color") val color: String = "#1E3A6E",
    @ColumnInfo(name = "icon") val icon: String = "bolt",
)

data class MeetingActionItem(
    val id: String = newId(),
    val description: String = "",
    val assignee: String = "",
    val dueDate: String? = null,
    val completed: Boolean = false,
)

@Entity(tableName = "meeting_minutes", indices = [Index("project_id")])
data class MeetingMinuteEntity(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "project_id") val projectId: String? = null,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "meeting_date") val meetingDate: String,
    @ColumnInfo(name = "meeting_time") val meetingTime: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 30,
    @ColumnInfo(name = "attendees") val attendees: List<String> = emptyList(),
    @ColumnInfo(name = "agenda") val agenda: String = "",
    @ColumnInfo(name = "discussion_notes") val discussionNotes: String = "",
    @ColumnInfo(name = "key_decisions") val keyDecisions: List<String> = emptyList(),
    @ColumnInfo(name = "action_items") val actionItems: List<MeetingActionItem> = emptyList(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** One personal journal entry per calendar date (IST). */
@Entity(tableName = "journal_entries", indices = [Index(value = ["entry_date"], unique = true)])
data class JournalEntry(
    @PrimaryKey val id: String = newId(),
    /** 'YYYY-MM-DD' in IST */
    @ColumnInfo(name = "entry_date") val date: String,
    @ColumnInfo(name = "mood") val mood: String? = null,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "content") val content: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

data class DataCounts(val tasks: Int, val projects: Int, val contexts: Int, val meetings: Int, val journal: Int = 0)

data class BackupSnapshot(
    val tasks: List<TaskEntity>,
    val projects: List<ProjectEntity>,
    val contexts: List<ContextTagEntity>,
    val meetings: List<MeetingMinuteEntity>,
    val journal: List<JournalEntry> = emptyList(),
)
