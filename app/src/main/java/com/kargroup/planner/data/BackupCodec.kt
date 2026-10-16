package com.kargroup.planner.data

import com.kargroup.planner.util.Ist
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Converts the whole database to/from a formatted, human-readable JSON backup. */
object BackupCodec {

    const val FORMAT = "kar-planner-backup"
    const val VERSION = 1

    fun encode(s: BackupSnapshot): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", "KAR Planner")
            .put("exported_at", Ist.now().toString())
            .put("timezone", "Asia/Kolkata")
            .put("tasks", JSONArray().apply { s.tasks.forEach { put(taskToJson(it)) } })
            .put("projects", JSONArray().apply { s.projects.forEach { put(projectToJson(it)) } })
            .put("contexts", JSONArray().apply { s.contexts.forEach { put(contextToJson(it)) } })
            .put("meeting_minutes", JSONArray().apply { s.meetings.forEach { put(meetingToJson(it)) } })
        return root.toString(2)
    }

    /** @throws IllegalArgumentException with a readable message when the file is not a valid backup. */
    fun decode(raw: String): BackupSnapshot {
        val root = try {
            JSONObject(raw.trim())
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not valid JSON.")
        }
        if (!root.has("tasks") && !root.has("projects") && !root.has("meeting_minutes")) {
            throw IllegalArgumentException("This file does not look like a KAR Planner backup.")
        }
        val version = root.optInt("version", 1)
        if (version > VERSION) {
            throw IllegalArgumentException("Backup was made by a newer version of KAR Planner (v$version).")
        }

        val tasks = root.optJSONArray("tasks").objects().mapIndexed { i, o -> taskFromJson(o, i + 1) }
        val projects = root.optJSONArray("projects").objects().mapIndexed { i, o ->
            ProjectEntity(
                id = o.req("id", "Project", i + 1),
                name = o.req("name", "Project", i + 1),
                description = o.optStringOrNull("description"),
                color = o.optStringOrNull("color") ?: "#1E3A6E",
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
            )
        }
        val contexts = root.optJSONArray("contexts").objects().mapIndexed { i, o ->
            ContextTagEntity(
                id = o.req("id", "Context", i + 1),
                name = o.req("name", "Context", i + 1),
                color = o.optStringOrNull("color") ?: "#1E3A6E",
                icon = o.optStringOrNull("icon") ?: "bolt",
            )
        }
        val meetings = root.optJSONArray("meeting_minutes").objects().mapIndexed { i, o ->
            MeetingMinuteEntity(
                id = o.req("id", "Meeting", i + 1),
                projectId = o.optStringOrNull("project_id"),
                title = o.req("title", "Meeting", i + 1),
                meetingDate = o.optStringOrNull("meeting_date") ?: Ist.todayStr(),
                meetingTime = o.optStringOrNull("meeting_time") ?: "10:00",
                durationMinutes = o.optInt("duration_minutes", 30),
                attendees = o.optJSONArray("attendees").strings(),
                agenda = o.optString("agenda", ""),
                discussionNotes = o.optString("discussion_notes", ""),
                keyDecisions = o.optJSONArray("key_decisions").strings(),
                actionItems = o.optJSONArray("action_items")?.let { JsonCodec.actionItemsFromJson(it) } ?: emptyList(),
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
            )
        }
        return BackupSnapshot(tasks, projects, contexts, meetings)
    }

    // ---------------------------------------------------------------- encode

    private fun taskToJson(t: TaskEntity): JSONObject = JSONObject()
        .put("id", t.id)
        .put("parent_id", t.parentId ?: JSONObject.NULL)
        .put("project_id", t.projectId ?: JSONObject.NULL)
        .put("context_id", t.contextId ?: JSONObject.NULL)
        .put("text", t.text)
        .put("notes", t.notes ?: JSONObject.NULL)
        .put("done", t.done)
        .put("priority", t.priority ?: JSONObject.NULL)
        .put("due_date", t.dueDate ?: JSONObject.NULL)
        .put("due_time", t.dueTime ?: JSONObject.NULL)
        .put("estimated_minutes", t.estimatedMinutes ?: JSONObject.NULL)
        .put("manual_order", t.manualOrder ?: JSONObject.NULL)
        .put("is_recurring", t.isRecurring)
        .put("recurrence_frequency", t.recurrenceFrequency ?: JSONObject.NULL)
        .put("recurrence_interval", t.recurrenceInterval)
        .put(
            "recurrence_days_of_week",
            t.recurrenceDaysOfWeek?.let { days -> JSONArray().apply { days.forEach { put(it) } } } ?: JSONObject.NULL
        )
        .put("meeting_id", t.meetingId ?: JSONObject.NULL)
        .put("meeting_action_item_id", t.meetingActionItemId ?: JSONObject.NULL)
        .put("is_collapsed", t.isCollapsed)
        .put("created_at", t.createdAt)
        .put("completed_at", t.completedAt ?: JSONObject.NULL)

    private fun projectToJson(p: ProjectEntity): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("description", p.description ?: JSONObject.NULL)
        .put("color", p.color)
        .put("created_at", p.createdAt)

    private fun contextToJson(c: ContextTagEntity): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("color", c.color)
        .put("icon", c.icon)

    private fun meetingToJson(m: MeetingMinuteEntity): JSONObject = JSONObject()
        .put("id", m.id)
        .put("project_id", m.projectId ?: JSONObject.NULL)
        .put("title", m.title)
        .put("meeting_date", m.meetingDate)
        .put("meeting_time", m.meetingTime)
        .put("duration_minutes", m.durationMinutes)
        .put("attendees", JSONArray().apply { m.attendees.forEach { put(it) } })
        .put("agenda", m.agenda)
        .put("discussion_notes", m.discussionNotes)
        .put("key_decisions", JSONArray().apply { m.keyDecisions.forEach { put(it) } })
        .put("action_items", JsonCodec.actionItemsToJson(m.actionItems))
        .put("created_at", m.createdAt)

    // ---------------------------------------------------------------- decode

    private fun taskFromJson(o: JSONObject, index: Int): TaskEntity {
        val priority = o.optStringOrNull("priority")?.lowercase()
        if (priority != null && priority !in Priority.ALL) {
            throw IllegalArgumentException("Task #$index has an unknown priority \"$priority\".")
        }
        val due = o.optStringOrNull("due_date")
        if (due != null && Ist.parse(due) == null) {
            throw IllegalArgumentException("Task #$index has an invalid due date \"$due\" (expected YYYY-MM-DD).")
        }
        val freq = o.optStringOrNull("recurrence_frequency")
        if (freq != null && freq !in Frequency.ALL) {
            throw IllegalArgumentException("Task #$index has an unknown recurrence \"$freq\".")
        }
        val days = o.optJSONArray("recurrence_days_of_week")?.let { arr ->
            List(arr.length()) { arr.getInt(it) }.filter { it in 1..7 }
        }
        return TaskEntity(
            id = o.req("id", "Task", index),
            parentId = o.optStringOrNull("parent_id"),
            projectId = o.optStringOrNull("project_id"),
            contextId = o.optStringOrNull("context_id"),
            text = o.req("text", "Task", index),
            notes = o.optStringOrNull("notes"),
            done = o.optBoolean("done", false),
            priority = priority,
            dueDate = due,
            dueTime = o.optStringOrNull("due_time")?.takeIf { Regex("^\\d{2}:\\d{2}$").matches(it) },
            estimatedMinutes = if (o.has("estimated_minutes") && !o.isNull("estimated_minutes")) o.optInt("estimated_minutes").takeIf { it > 0 } else null,
            manualOrder = if (o.has("manual_order") && !o.isNull("manual_order")) o.optLong("manual_order") else null,
            isRecurring = o.optBoolean("is_recurring", false),
            recurrenceFrequency = freq,
            recurrenceInterval = o.optInt("recurrence_interval", 1).coerceAtLeast(1),
            recurrenceDaysOfWeek = days,
            meetingId = o.optStringOrNull("meeting_id"),
            meetingActionItemId = o.optStringOrNull("meeting_action_item_id"),
            isCollapsed = o.optBoolean("is_collapsed", false),
            createdAt = o.optLong("created_at", System.currentTimeMillis()),
            completedAt = if (o.has("completed_at") && !o.isNull("completed_at")) o.optLong("completed_at") else null,
        )
    }

    private fun JSONObject.req(key: String, kind: String, index: Int): String {
        val v = optStringOrNull(key)
        if (v.isNullOrBlank()) throw IllegalArgumentException("$kind #$index is missing \"$key\".")
        return v
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return List(length()) { i ->
            optJSONObject(i) ?: throw IllegalArgumentException("Entry #${i + 1} is not a JSON object.")
        }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { optString(it, "") }.filter { it.isNotBlank() }
    }
}
