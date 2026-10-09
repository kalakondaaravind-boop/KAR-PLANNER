package com.kargroup.planner.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun intListToJson(value: List<Int>?): String? {
        if (value == null) return null
        val arr = JSONArray()
        value.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToIntList(value: String?): List<Int>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val arr = JSONArray(value)
            List(arr.length()) { arr.getInt(it) }
        }.getOrNull()
    }

    @TypeConverter
    fun stringListToJson(value: List<String>): String {
        val arr = JSONArray()
        value.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun jsonToStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(value)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    @TypeConverter
    fun actionItemsToJson(value: List<MeetingActionItem>): String =
        JsonCodec.actionItemsToJson(value).toString()

    @TypeConverter
    fun jsonToActionItems(value: String): List<MeetingActionItem> {
        if (value.isBlank()) return emptyList()
        return runCatching { JsonCodec.actionItemsFromJson(JSONArray(value)) }.getOrDefault(emptyList())
    }
}

fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

/** Shared JSON helpers used by Room converters and Backup/Restore. */
object JsonCodec {

    fun actionItemsToJson(items: List<MeetingActionItem>): JSONArray {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("description", item.description)
                    .put("assignee", item.assignee)
                    .put("due_date", item.dueDate ?: JSONObject.NULL)
                    .put("completed", item.completed)
            )
        }
        return arr
    }

    fun actionItemsFromJson(arr: JSONArray): List<MeetingActionItem> =
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            MeetingActionItem(
                id = o.optStringOrNull("id") ?: newId(),
                description = o.optString("description", ""),
                assignee = o.optString("assignee", ""),
                dueDate = o.optStringOrNull("due_date"),
                completed = o.optBoolean("completed", false),
            )
        }
}
