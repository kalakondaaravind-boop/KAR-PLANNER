package android.content

import com.kargroup.planner.desktop.AppDirs
import org.json.JSONObject
import java.io.File

/*
 * Minimal desktop stand-ins for Android's Context / SharedPreferences so the shared
 * settings and reminder code runs unchanged. Each preferences file is a small JSON file
 * in the app's data folder.
 */

abstract class Context {
    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences = JsonPreferences.get(name)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

class JsonPreferences private constructor(private val file: File) : SharedPreferences {
    private val lock = Any()
    private val data: JSONObject = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) {
        if (data.has(key) && !data.isNull(key)) data.optString(key, defValue) else defValue
    }
    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) { data.optInt(key, defValue) }
    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) { data.optLong(key, defValue) }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) { data.optBoolean(key, defValue) }
    override fun contains(key: String): Boolean = synchronized(lock) { data.has(key) }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clearAll = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor { changes[key] = value; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { changes[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { changes[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { changes[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { changes[key] = null; return this }
        override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(lock) {
            if (clearAll) data.keySet().toList().forEach { data.remove(it) }
            changes.forEach { (k, v) -> if (v == null) data.remove(k) else data.put(k, v) }
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(data.toString(2), Charsets.UTF_8)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }.isSuccess
        }
    }

    companion object {
        private val cache = HashMap<String, JsonPreferences>()
        @Synchronized
        fun get(name: String): JsonPreferences =
            cache.getOrPut(name) { JsonPreferences(File(AppDirs.prefsDir, "$name.json")) }
    }
}
