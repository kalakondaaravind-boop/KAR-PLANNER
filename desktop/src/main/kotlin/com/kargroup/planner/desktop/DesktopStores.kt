package com.kargroup.planner.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A coloured label that can be put on activities (desktop feature). */
data class Tag(val name: String, val color: String)

/** Reusable activity with its subtasks. */
data class ActivityTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val title: String,
    val notes: String = "",
    val priority: String? = null,
    val estimatedMinutes: Int? = null,
    val assignee: String = "",
    val projectId: String? = null,
    val dueInDays: Int? = null,
    val subtasks: List<Pair<String, String>> = emptyList(), // text, assignee
)

/** Reusable meeting (Minutes of Meeting) set-up. */
data class MomTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val title: String,
    val agenda: String = "",
    val attendees: List<String> = emptyList(),
    val durationMinutes: Int = 30,
    val meetingTime: String = "10:00",
    val projectId: String? = null,
)

private fun File.readJsonObject(): JSONObject = runCatching { JSONObject(readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }

private fun File.writeJson(o: JSONObject) {
    runCatching {
        val tmp = File(parentFile, "$name.tmp")
        tmp.writeText(o.toString(2), Charsets.UTF_8)
        if (!tmp.renameTo(this)) { delete(); tmp.renameTo(this) }
    }
}

private fun JSONArray?.strings(): List<String> = this?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()

/** Tags and which activities carry them (saved in tags.json next to the data file). */
class TagStore(private val file: File = File(AppDirs.root, "tags.json")) {

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags

    /** Activity id → tag names. */
    private val _assigned = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val assigned: StateFlow<Map<String, Set<String>>> = _assigned

    init {
        val o = file.readJsonObject()
        val defs = o.optJSONArray("tags")
        _tags.value = if (defs == null) DEFAULT_TAGS else (0 until defs.length()).map {
            val t = defs.getJSONObject(it)
            Tag(t.optString("name"), t.optString("color", "#0EA5E9"))
        }.filter { it.name.isNotBlank() }
        val a = o.optJSONObject("assigned")
        _assigned.value = a?.keySet()?.associateWith { k -> a.optJSONArray(k).strings().toSet() }?.filterValues { it.isNotEmpty() } ?: emptyMap()
    }

    private fun save() {
        val o = JSONObject()
        o.put("tags", JSONArray(_tags.value.map { JSONObject().put("name", it.name).put("color", it.color) }))
        val a = JSONObject()
        _assigned.value.forEach { (k, v) -> if (v.isNotEmpty()) a.put(k, JSONArray(v.toList())) }
        o.put("assigned", a)
        file.writeJson(o)
    }

    fun tagsOf(taskId: String): Set<String> = _assigned.value[taskId] ?: emptySet()

    fun colorOf(name: String): String = _tags.value.firstOrNull { it.name.equals(name, true) }?.color ?: "#64748B"

    fun saveTag(tag: Tag, oldName: String? = null) {
        val name = tag.name.trim().uppercase()
        if (name.isEmpty()) return
        val clean = tag.copy(name = name)
        _tags.value = _tags.value.filter { !it.name.equals(oldName ?: name, true) && !it.name.equals(name, true) } + clean
        if (oldName != null && !oldName.equals(name, true)) {
            _assigned.value = _assigned.value.mapValues { (_, v) -> v.map { if (it.equals(oldName, true)) name else it }.toSet() }
        }
        save()
    }

    fun deleteTag(name: String) {
        _tags.value = _tags.value.filter { !it.name.equals(name, true) }
        _assigned.value = _assigned.value.mapValues { (_, v) -> v.filter { !it.equals(name, true) }.toSet() }.filterValues { it.isNotEmpty() }
        save()
    }

    fun add(taskIds: Collection<String>, tag: String) {
        val m = _assigned.value.toMutableMap()
        taskIds.forEach { id -> m[id] = (m[id] ?: emptySet()) + tag }
        _assigned.value = m
        save()
    }

    fun remove(taskIds: Collection<String>, tag: String) {
        val m = _assigned.value.toMutableMap()
        taskIds.forEach { id -> m[id]?.let { m[id] = it - tag } }
        _assigned.value = m.filterValues { it.isNotEmpty() }
        save()
    }

    /** Drops tags of activities that no longer exist. */
    fun prune(existingIds: Set<String>) {
        val m = _assigned.value.filterKeys { it in existingIds }
        if (m.size != _assigned.value.size) { _assigned.value = m; save() }
    }

    companion object {
        val DEFAULT_TAGS = listOf(
            Tag("CLIENT", "#2563EB"), Tag("AUDIT", "#DC2626"), Tag("FINANCE", "#059669"),
            Tag("HR", "#DB2777"), Tag("FOLLOW-UP", "#D97706"), Tag("WAITING", "#7C3AED"),
        )
        val COLORS = listOf("#2563EB", "#0EA5E9", "#059669", "#65A30D", "#D97706", "#DC2626", "#DB2777", "#7C3AED", "#475569")
    }
}

/** Activity and meeting templates (saved in templates.json). */
class TemplateStore(private val file: File = File(AppDirs.root, "templates.json")) {

    private val _activities = MutableStateFlow<List<ActivityTemplate>>(emptyList())
    val activities: StateFlow<List<ActivityTemplate>> = _activities

    private val _meetings = MutableStateFlow<List<MomTemplate>>(emptyList())
    val meetings: StateFlow<List<MomTemplate>> = _meetings

    init {
        val o = file.readJsonObject()
        val acts = o.optJSONArray("activities")
        _activities.value = if (acts == null) DEFAULT_ACTIVITIES else (0 until acts.length()).map { i ->
            val a = acts.getJSONObject(i)
            ActivityTemplate(
                id = a.optString("id", UUID.randomUUID().toString()),
                name = a.optString("name"),
                title = a.optString("title"),
                notes = a.optString("notes", ""),
                priority = a.optString("priority", "").ifBlank { null },
                estimatedMinutes = a.optInt("estimate", 0).takeIf { it > 0 },
                assignee = a.optString("assignee", ""),
                projectId = a.optString("projectId", "").ifBlank { null },
                dueInDays = if (a.has("dueInDays") && !a.isNull("dueInDays")) a.optInt("dueInDays") else null,
                subtasks = a.optJSONArray("subtasks")?.let { s ->
                    (0 until s.length()).map { j -> s.getJSONObject(j).let { it.optString("text") to it.optString("assignee", "") } }
                } ?: emptyList(),
            )
        }
        val moms = o.optJSONArray("meetings")
        _meetings.value = if (moms == null) DEFAULT_MEETINGS else (0 until moms.length()).map { i ->
            val m = moms.getJSONObject(i)
            MomTemplate(
                id = m.optString("id", UUID.randomUUID().toString()),
                name = m.optString("name"),
                title = m.optString("title"),
                agenda = m.optString("agenda", ""),
                attendees = m.optJSONArray("attendees").strings(),
                durationMinutes = m.optInt("duration", 30),
                meetingTime = m.optString("time", "10:00"),
                projectId = m.optString("projectId", "").ifBlank { null },
            )
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("activities", JSONArray(_activities.value.map { a ->
            JSONObject().put("id", a.id).put("name", a.name).put("title", a.title).put("notes", a.notes)
                .put("priority", a.priority ?: "").put("estimate", a.estimatedMinutes ?: 0).put("assignee", a.assignee)
                .put("projectId", a.projectId ?: "").put("dueInDays", a.dueInDays ?: JSONObject.NULL)
                .put("subtasks", JSONArray(a.subtasks.map { JSONObject().put("text", it.first).put("assignee", it.second) }))
        }))
        o.put("meetings", JSONArray(_meetings.value.map { m ->
            JSONObject().put("id", m.id).put("name", m.name).put("title", m.title).put("agenda", m.agenda)
                .put("attendees", JSONArray(m.attendees)).put("duration", m.durationMinutes).put("time", m.meetingTime)
                .put("projectId", m.projectId ?: "")
        }))
        file.writeJson(o)
    }

    fun saveActivity(t: ActivityTemplate) {
        _activities.value = _activities.value.filter { it.id != t.id } + t
        save()
    }

    fun deleteActivity(id: String) { _activities.value = _activities.value.filter { it.id != id }; save() }

    fun saveMeeting(t: MomTemplate) {
        _meetings.value = _meetings.value.filter { it.id != t.id } + t
        save()
    }

    fun deleteMeeting(id: String) { _meetings.value = _meetings.value.filter { it.id != id }; save() }

    companion object {
        val DEFAULT_ACTIVITIES = listOf(
            ActivityTemplate(
                name = "MONTH-END CLOSE", title = "MONTH-END CLOSE", priority = "high", estimatedMinutes = 240, dueInDays = 3,
                subtasks = listOf(
                    "BANK RECONCILIATIONS" to "", "ACCRUALS AND PROVISIONS" to "", "INTERCOMPANY RECONCILIATION" to "",
                    "TRIAL BALANCE REVIEW" to "", "MIS PACK TO MANAGEMENT" to "",
                ),
            ),
            ActivityTemplate(
                name = "VENDOR PAYMENT RUN", title = "VENDOR PAYMENT RUN", priority = "medium", estimatedMinutes = 60, dueInDays = 1,
                subtasks = listOf("COLLECT APPROVED INVOICES" to "", "PREPARE PAYMENT FILE" to "", "APPROVAL AND RELEASE" to ""),
            ),
        )
        val DEFAULT_MEETINGS = listOf(
            MomTemplate(
                name = "WEEKLY TEAM REVIEW", title = "WEEKLY TEAM REVIEW", durationMinutes = 60, meetingTime = "10:00",
                agenda = "1. OPEN ACTION POINTS\n2. OVERDUE ITEMS\n3. PRIORITIES FOR THE WEEK\n4. ANY OTHER BUSINESS",
            ),
            MomTemplate(
                name = "CLIENT STEERING COMMITTEE", title = "CLIENT STEERING COMMITTEE", durationMinutes = 60, meetingTime = "15:00",
                agenda = "1. PROGRAMME STATUS\n2. RISKS AND ISSUES\n3. DECISIONS REQUIRED\n4. NEXT STEPS",
            ),
        )
    }
}
