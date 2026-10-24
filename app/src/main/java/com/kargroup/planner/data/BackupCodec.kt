package com.kargroup.planner.data

import com.kargroup.planner.util.Ist
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Converts the whole database to/from a formatted, human-readable JSON backup. */
object BackupCodec {

    const val FORMAT = "kar-planner-backup"
    const val VERSION = 2

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
            .put("persons", JSONArray().apply {
                s.persons.forEach { p ->
                    put(
                        JSONObject()
                            .put("id", p.id)
                            .put("name", p.name)
                            .put("role", p.role ?: JSONObject.NULL)
                            .put("email", p.email ?: JSONObject.NULL)
                            .put("phone", p.phone ?: JSONObject.NULL)
                            .put("created_at", p.createdAt)
                    )
                }
            })
            .put("history", JSONArray().apply {
                s.history.forEach { h ->
                    put(
                        JSONObject().put("id", h.id).put("task_id", h.taskId).put("at", h.at)
                            .put("kind", h.kind).put("detail", h.detail ?: JSONObject.NULL)
                    )
                }
            })
            .put("journal", JSONArray().apply {
                s.journal.forEach { j ->
                    put(
                        JSONObject()
                            .put("id", j.id)
                            .put("date", j.date)
                            .put("mood", j.mood ?: JSONObject.NULL)
                            .put("title", j.title ?: JSONObject.NULL)
                            .put("content", j.content)
                            .put("created_at", j.createdAt)
                            .put("updated_at", j.updatedAt)
                    )
                }
            })
        return root.toString(2)
    }

    /**
     * Reads a backup. Besides this app's own format it tolerates exports from earlier versions and
     * similar planner apps: camelCase or snake_case keys, nested subtasks, missing ids, ISO / dd-MM-yyyy
     * dates and different priority / status words.
     * @throws IllegalArgumentException with a readable message when nothing usable is found.
     */
    fun decode(raw: String): BackupSnapshot {
        val text = raw.trim().removePrefix("\uFEFF")
        val root: JSONObject = try {
            if (text.startsWith("[")) JSONObject().put("tasks", JSONArray(text)) else JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException("This file is not valid JSON.")
        }
        // Some exports wrap everything in { "data": { ... } } or { "state": { ... } }.
        val r = listOf("data", "state", "backup", "planner").firstNotNullOfOrNull { k ->
            root.optJSONObject(k)?.takeIf { it.hasAny(TASK_KEYS + PROJECT_KEYS + MEETING_KEYS) }
        } ?: root

        val tasks = ArrayList<TaskEntity>()
        r.firstArray(TASK_KEYS).objects().forEach { flattenTask(it, null, tasks) }

        val projects = r.firstArray(PROJECT_KEYS).objects().mapNotNull { o ->
            val name = o.str("name", "title", "label") ?: return@mapNotNull null
            ProjectEntity(
                id = o.str("id", "_id", "uuid") ?: newId(),
                name = name,
                description = o.str("description", "desc", "notes"),
                color = o.str("color", "colour")?.takeIf { it.startsWith("#") } ?: "#1E3A6E",
                createdAt = o.millis("created_at", "createdAt", "created") ?: System.currentTimeMillis(),
            )
        }
        val contexts = r.firstArray(listOf("contexts", "contextTags", "context_tags", "tags")).objects().mapNotNull { o ->
            val name = o.str("name", "title", "label") ?: return@mapNotNull null
            ContextTagEntity(
                id = o.str("id", "_id") ?: newId(),
                name = name,
                color = o.str("color")?.takeIf { it.startsWith("#") } ?: "#1E3A6E",
                icon = o.str("icon") ?: "bolt",
            )
        }
        val meetings = r.firstArray(MEETING_KEYS).objects().mapNotNull { o -> meetingFromJson(o) }
        val journal = r.firstArray(listOf("journal", "journal_entries", "journalEntries", "diary", "entries")).objects()
            .mapNotNull { o ->
                val date = parseDate(o.any("date", "entry_date", "entryDate", "day")) ?: return@mapNotNull null
                JournalEntry(
                    id = o.str("id", "_id") ?: newId(),
                    date = date,
                    mood = o.str("mood"),
                    title = o.str("title", "headline"),
                    content = o.str("content", "text", "body", "notes") ?: "",
                    createdAt = o.millis("created_at", "createdAt") ?: System.currentTimeMillis(),
                    updatedAt = o.millis("updated_at", "updatedAt") ?: System.currentTimeMillis(),
                )
            }
            .groupBy { it.date }.map { (_, list) -> list.maxBy { it.updatedAt } } // one per date

        val persons = ArrayList<PersonEntity>()
        r.firstArray(listOf("persons", "people", "team", "members", "assignees")).let { arr ->
            if (arr != null) for (k in 0 until arr.length()) {
                val v = arr.opt(k)
                when (v) {
                    is JSONObject -> v.str("name", "fullName", "full_name")?.let { n ->
                        persons.add(
                            PersonEntity(
                                id = v.str("id", "_id") ?: newId(),
                                name = n.trim(),
                                role = v.str("role", "designation", "title"),
                                email = v.str("email"),
                                phone = v.str("phone", "mobile"),
                                createdAt = v.millis("created_at", "createdAt") ?: System.currentTimeMillis(),
                            )
                        )
                    }
                    is String -> if (v.isNotBlank()) persons.add(PersonEntity(name = v.trim()))
                }
            }
        }
        // Make sure everyone named on an activity or action item exists in the people list.
        val knownNames = persons.map { it.name.lowercase() }.toMutableSet()
        (tasks.mapNotNull { it.assignee } + meetings.flatMap { m -> m.actionItems.map { it.assignee } })
            .map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { n -> if (knownNames.add(n.lowercase())) persons.add(PersonEntity(name = n)) }

        if (tasks.isEmpty() && projects.isEmpty() && meetings.isEmpty() && journal.isEmpty() && persons.isEmpty()) {
            throw IllegalArgumentException("No activities, projects, meetings or journal entries were found in this file.")
        }
        val history = r.optJSONArray("history").objects().mapNotNull { o ->
            val taskId = o.str("task_id", "taskId") ?: return@mapNotNull null
            TaskHistoryEntity(
                id = o.str("id") ?: newId(),
                taskId = taskId,
                at = o.millis("at") ?: System.currentTimeMillis(),
                kind = o.str("kind") ?: HistoryKind.UPDATED,
                detail = o.str("detail"),
            )
        }
        return BackupSnapshot(tasks, projects, contexts, meetings, journal, persons.distinctBy { it.name.lowercase() }, history)
    }

    private val TASK_KEYS = listOf("tasks", "activities", "items", "todos", "todoItems", "task_list", "taskList")
    private val PROJECT_KEYS = listOf("projects", "projectList", "project_list")
    private val MEETING_KEYS = listOf("meeting_minutes", "meetingMinutes", "meetings", "moms", "minutes", "mom")

    /** Adds the task and any nested "subtasks"/"children" to [out]. */
    private fun flattenTask(o: JSONObject, parentId: String?, out: MutableList<TaskEntity>) {
        val title = o.str("text", "title", "name", "task", "description") ?: return
        val id = o.str("id", "_id", "uuid", "key") ?: newId()
        val statusWord = o.str("status", "state")?.lowercase()?.replace('-', ' ')?.replace('_', ' ')
        val done = o.bool("done", "completed", "isDone", "is_done", "isCompleted", "is_completed", "complete")
            ?: (statusWord in setOf("done", "completed", "complete", "closed", "finished"))
        val due = parseDate(o.any("due_date", "dueDate", "due", "target_date", "targetDate", "deadline", "date"))
        val dueTime = parseTime(o.any("due_time", "dueTime", "target_time", "targetTime", "time"))
            ?: parseTime(o.any("due_date", "dueDate", "due", "deadline"))
        val freq = o.str("recurrence_frequency", "recurrenceFrequency", "frequency", "repeat", "recurrence")
            ?.lowercase()?.let { f ->
                when {
                    f.startsWith("day") || f == "daily" -> Frequency.DAILY
                    f.startsWith("biweek") || f.contains("fortnight") -> Frequency.BIWEEKLY
                    f.startsWith("week") -> Frequency.WEEKLY
                    f.startsWith("month") -> Frequency.MONTHLY
                    else -> null
                }
            }
        val recurring = o.bool("is_recurring", "isRecurring", "recurring") ?: (freq != null)
        val days = o.optJSONArray(o.firstKey("recurrence_days_of_week", "recurrenceDaysOfWeek", "daysOfWeek", "days"))?.let { arr ->
            List(arr.length()) { arr.optInt(it, 0) }.filter { it in 1..7 }
        }
        val isOpenStarted = statusWord in setOf("initiated", "in progress", "inprogress", "started", "ongoing", "doing", "active", "wip")
        val estimate = o.num("estimated_minutes", "estimatedMinutes", "estimate", "estimate_minutes", "duration_minutes", "durationMinutes", "duration")?.toInt()?.takeIf { it > 0 }

        out.add(
            TaskEntity(
                id = id,
                parentId = parentId ?: o.str("parent_id", "parentId", "parent"),
                projectId = o.str("project_id", "projectId", "project"),
                contextId = o.str("context_id", "contextId"),
                text = title.trim(),
                notes = o.str("notes", "note", "details", "remarks_text")?.takeIf { it != title },
                done = done,
                priority = parsePriority(o.any("priority", "prio", "importance")),
                dueDate = due,
                dueTime = if (due != null) dueTime else null,
                estimatedMinutes = estimate,
                status = if (isOpenStarted) TaskStatus.INITIATED else o.str("status")?.takeIf { it in TaskStatus.ALL && it != TaskStatus.COMPLETED },
                assignee = o.str("assignee", "assignedTo", "assigned_to", "owner", "person", "responsible")?.trim()?.ifBlank { null },
                completionRemarks = o.str("completion_remarks", "completionRemarks", "remarks", "closing_remarks"),
                statusNote = o.str("status_note", "statusNote", "current_status", "currentStatus", "update", "latest_update"),
                plannedStart = o.millis("planned_start", "plannedStart"),
                manualOrder = o.num("manual_order", "manualOrder")?.toLong(),
                isRecurring = recurring && freq != null,
                recurrenceFrequency = if (recurring) freq else null,
                recurrenceInterval = (o.num("recurrence_interval", "recurrenceInterval", "interval")?.toInt() ?: 1).coerceAtLeast(1),
                recurrenceDaysOfWeek = days?.takeIf { it.isNotEmpty() },
                meetingId = o.str("meeting_id", "meetingId"),
                meetingActionItemId = o.str("meeting_action_item_id", "meetingActionItemId"),
                isCollapsed = o.bool("is_collapsed", "isCollapsed", "collapsed") ?: false,
                createdAt = o.millis("created_at", "createdAt", "created") ?: System.currentTimeMillis(),
                completedAt = if (done) (o.millis("completed_at", "completedAt", "doneAt") ?: System.currentTimeMillis()) else null,
            )
        )
        listOf("subtasks", "subTasks", "children", "items", "steps").forEach { k ->
            o.optJSONArray(k)?.objects()?.forEach { flattenTask(it, id, out) }
        }
    }

    private fun meetingFromJson(o: JSONObject): MeetingMinuteEntity? {
        val title = o.str("title", "name", "subject") ?: return null
        val itemsArr = o.optJSONArray(o.firstKey("action_items", "actionItems", "actions", "tasks"))
        val items = itemsArr.objects().mapNotNull { a ->
            val desc = a.str("description", "text", "title", "task", "action") ?: return@mapNotNull null
            MeetingActionItem(
                id = a.str("id", "_id") ?: newId(),
                description = desc,
                assignee = a.str("assignee", "owner", "assignedTo", "assigned_to", "responsible") ?: "",
                dueDate = parseDate(a.any("due_date", "dueDate", "due", "deadline")),
                completed = a.bool("completed", "done", "isDone") ?: false,
            )
        }
        return MeetingMinuteEntity(
            id = o.str("id", "_id") ?: newId(),
            projectId = o.str("project_id", "projectId", "project"),
            title = title,
            meetingDate = parseDate(o.any("meeting_date", "meetingDate", "date")) ?: Ist.todayStr(),
            meetingTime = parseTime(o.any("meeting_time", "meetingTime", "time", "date")) ?: "10:00",
            durationMinutes = o.num("duration_minutes", "durationMinutes", "duration")?.toInt() ?: 30,
            attendees = o.list("attendees", "participants", "members"),
            agenda = o.list("agenda", "agendaItems", "agenda_items").joinToString("\n"),
            discussionNotes = o.str("discussion_notes", "discussionNotes", "discussion", "notes", "minutes") ?: "",
            keyDecisions = o.list("key_decisions", "keyDecisions", "decisions"),
            actionItems = items,
            createdAt = o.millis("created_at", "createdAt") ?: System.currentTimeMillis(),
        )
    }

    // ------------------------------------------------------- tolerant readers

    private fun JSONObject.hasAny(keys: List<String>) = keys.any { has(it) }

    private fun JSONObject.firstKey(vararg keys: String): String = keys.firstOrNull { has(it) && !isNull(it) } ?: keys.first()

    private fun JSONObject.firstArray(keys: List<String>): JSONArray? =
        keys.firstNotNullOfOrNull { k -> optJSONArray(k) ?: optJSONObject(k)?.let { obj -> objectValues(obj) } }

    /** Some apps store collections as { id: {...}, id2: {...} }. */
    private fun objectValues(obj: JSONObject): JSONArray? {
        val arr = JSONArray()
        obj.keys().forEach { k -> obj.optJSONObject(k)?.let { v -> if (!v.has("id")) v.put("id", k); arr.put(v) } }
        return if (arr.length() > 0) arr else null
    }

    private fun JSONObject.any(vararg keys: String): Any? =
        keys.firstNotNullOfOrNull { k -> if (has(k) && !isNull(k)) opt(k) else null }

    private fun JSONObject.str(vararg keys: String): String? = when (val v = any(*keys)) {
        null -> null
        is String -> v.trim().ifBlank { null }
        is Number, is Boolean -> v.toString()
        is JSONObject -> v.optString("name").ifBlank { null } ?: v.optString("id").ifBlank { null }
        else -> null
    }

    private fun JSONObject.num(vararg keys: String): Double? = when (val v = any(*keys)) {
        is Number -> v.toDouble()
        is String -> v.trim().replace(Regex("[^0-9.]"), "").toDoubleOrNull()
        else -> null
    }

    private fun JSONObject.bool(vararg keys: String): Boolean? = when (val v = any(*keys)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "true", "yes", "y", "1", "done", "completed" -> true
            "false", "no", "n", "0", "" -> false
            else -> null
        }
        else -> null
    }

    private fun JSONObject.millis(vararg keys: String): Long? = when (val v = any(*keys)) {
        is Number -> v.toLong().let { if (it in 1..99_999_999_999L) it * 1000 else it } // seconds -> ms
        is String -> v.trim().toLongOrNull()?.let { if (it < 100_000_000_000L) it * 1000 else it }
            ?: runCatching { java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(v).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(v).atZone(Ist.ZONE).toInstant().toEpochMilli() }.getOrNull()
            ?: parseDate(v)?.let { java.time.LocalDate.parse(it).atStartOfDay(Ist.ZONE).toInstant().toEpochMilli() }
        else -> null
    }

    private fun JSONObject.list(vararg keys: String): List<String> = when (val v = any(*keys)) {
        is JSONArray -> List(v.length()) { i ->
            when (val e = v.opt(i)) {
                is JSONObject -> e.optString("name").ifBlank { e.optString("text") }
                null -> ""
                else -> e.toString()
            }.trim()
        }.filter { it.isNotEmpty() }
        is String -> v.split(Regex("[\n;]|,(?=\\s*[A-Za-z])")).map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    /** Accepts YYYY-MM-DD, ISO date-times, dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy and epoch numbers; returns YYYY-MM-DD (IST). */
    fun parseDate(v: Any?): String? {
        when (v) {
            null -> return null
            is Number -> {
                val n = v.toLong()
                val ms = if (n < 100_000_000_000L) n * 1000 else n
                return java.time.Instant.ofEpochMilli(ms).atZone(Ist.ZONE).toLocalDate().toString()
            }
            is String -> {
                val s = v.trim()
                if (s.isEmpty()) return null
                Ist.parse(s)?.let { return it.toString() }
                // ISO date-time with zone: convert to IST date.
                runCatching { return java.time.OffsetDateTime.parse(s).atZoneSameInstant(Ist.ZONE).toLocalDate().toString() }
                runCatching { return java.time.Instant.parse(s).atZone(Ist.ZONE).toLocalDate().toString() }
                Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(s)?.let { m ->
                    return runCatching {
                        java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).toString()
                    }.getOrNull()
                }
                Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})").find(s)?.let { m ->
                    var y = m.groupValues[3].toInt()
                    if (y < 100) y += 2000
                    return runCatching {
                        java.time.LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()).toString() // day first (India)
                    }.getOrNull()
                }
                return s.toLongOrNull()?.let { parseDate(it) }
            }
            else -> return null
        }
    }

    /** Accepts "14:30", "9:05", "02:30 PM" and ISO date-times; returns HH:mm or null. */
    fun parseTime(v: Any?): String? {
        val s = (v as? String)?.trim() ?: return null
        runCatching {
            if (s.contains('T')) {
                val t = runCatching { java.time.OffsetDateTime.parse(s).atZoneSameInstant(Ist.ZONE).toLocalTime() }.getOrNull()
                    ?: java.time.LocalDateTime.parse(s).toLocalTime()
                return if (t.hour == 0 && t.minute == 0) null else "%02d:%02d".format(t.hour, t.minute)
            }
        }
        val m = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*([AaPp][Mm])?$").find(s) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val ap = m.groupValues[3].lowercase()
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
    }

    fun parsePriority(v: Any?): String? {
        val s = when (v) {
            is Number -> when (v.toInt()) { 0, 1 -> "very high"; 2 -> "high"; 3 -> "medium"; else -> "" }
            is String -> v
            else -> return null
        }.trim().lowercase().replace('_', ' ').replace('-', ' ')
        return when (s) {
            "very high", "veryhigh", "urgent", "critical", "highest", "p0", "top" -> Priority.VERY_HIGH
            "high", "p1", "important" -> Priority.HIGH
            "medium", "med", "moderate", "p2" -> Priority.MEDIUM
            else -> null // standard / low / none / normal
        }
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
        .put("completion_remarks", t.completionRemarks ?: JSONObject.NULL)
        .put("status_note", t.statusNote ?: JSONObject.NULL)
        .put("planned_start", t.plannedStart ?: JSONObject.NULL)
        .put("status", t.status ?: JSONObject.NULL)
        .put("assignee", t.assignee ?: JSONObject.NULL)
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

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return List(length()) { i ->
            optJSONObject(i) ?: JSONObject()
        }
    }
}
