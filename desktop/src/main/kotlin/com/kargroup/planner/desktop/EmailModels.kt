package com.kargroup.planner.desktop

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Kinds of e-mail the planner prepares. */
object EmailKind {
    const val MOM = "mom"
    const val REMINDER = "reminder"
    const val DIGEST = "digest"
    const val WEEKLY = "weekly"
    const val TEST = "test"

    fun label(k: String): String = when (k) {
        MOM -> "Minutes of Meeting"
        REMINDER -> "Action reminder"
        DIGEST -> "Daily digest"
        WEEKLY -> "Weekly update"
        else -> "Test"
    }
}

/** One e-mail waiting for review. [key] prevents the same reminder being prepared twice. */
data class EmailDraft(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val key: String,
    val to: String,
    val cc: String = "",
    val subject: String,
    val html: String,
    val summary: String,
    val warnings: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("kind", kind).put("key", key).put("to", to).put("cc", cc)
        .put("subject", subject).put("html", html).put("summary", summary)
        .put("warnings", JSONArray(warnings)).put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = EmailDraft(
            id = o.optString("id", UUID.randomUUID().toString()),
            kind = o.optString("kind", EmailKind.TEST),
            key = o.optString("key", ""),
            to = o.optString("to", ""),
            cc = o.optString("cc", ""),
            subject = o.optString("subject", ""),
            html = o.optString("html", ""),
            summary = o.optString("summary", ""),
            warnings = o.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }
}

/** A line in the "sent" history. */
data class SentEmail(val at: Long, val kind: String, val to: String, val subject: String, val key: String, val how: String)

/** E-mail preferences (Settings → E-mail & Outlook). Times are minutes after midnight IST. */
data class EmailSettings(
    val myName: String = "",
    val myEmail: String = "",
    val ccMe: Boolean = true,
    val signature: String = "",
    /** Use the default signature set up in Outlook (with logo) instead of the typed one. */
    val useOutlookSignature: Boolean = true,
    val digestOn: Boolean = true,
    val digestTime: Int = 8 * 60 + 30,
    val remindersOn: Boolean = true,
    val reminderDaysAhead: Int = 1,
    val remindOverdue: Boolean = true,
    val weeklyOn: Boolean = true,
    val weeklyDay: Int = 1, // ISO: 1 = Monday
    val momAuto: Boolean = true,
    val startWithWindows: Boolean = false,
)

/** Stores e-mail settings, the review queue and the sent history in the app's data folder. */
class EmailStore(private val prefs: SharedPreferences, private val queueFile: File, private val sentFile: File) {

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<EmailSettings> = _settings

    private val _queue = MutableStateFlow(loadQueue())
    val queue: StateFlow<List<EmailDraft>> = _queue

    private val _sent = MutableStateFlow(loadSent())
    val sent: StateFlow<List<SentEmail>> = _sent

    // ---------------------------------------------------------------- settings

    fun updateSettings(f: (EmailSettings) -> EmailSettings) {
        val s = f(_settings.value).let {
            it.copy(
                digestTime = it.digestTime.coerceIn(0, 23 * 60 + 59),
                reminderDaysAhead = it.reminderDaysAhead.coerceIn(0, 7),
                weeklyDay = it.weeklyDay.coerceIn(1, 7),
                myEmail = it.myEmail.trim(),
            )
        }
        _settings.value = s
        prefs.edit()
            .putString("my_name", s.myName).putString("my_email", s.myEmail).putBoolean("cc_me", s.ccMe)
            .putString("signature", s.signature).putBoolean("use_outlook_signature", s.useOutlookSignature)
            .putBoolean("digest_on", s.digestOn).putInt("digest_time", s.digestTime)
            .putBoolean("reminders_on", s.remindersOn).putInt("reminder_days", s.reminderDaysAhead)
            .putBoolean("remind_overdue", s.remindOverdue)
            .putBoolean("weekly_on", s.weeklyOn).putInt("weekly_day", s.weeklyDay)
            .putBoolean("mom_auto", s.momAuto).putBoolean("start_with_windows", s.startWithWindows)
            .apply()
    }

    private fun loadSettings(): EmailSettings {
        val d = EmailSettings()
        return EmailSettings(
            myName = prefs.getString("my_name", d.myName) ?: "",
            myEmail = prefs.getString("my_email", d.myEmail) ?: "",
            ccMe = prefs.getBoolean("cc_me", d.ccMe),
            signature = prefs.getString("signature", d.signature) ?: "",
            useOutlookSignature = prefs.getBoolean("use_outlook_signature", d.useOutlookSignature),
            digestOn = prefs.getBoolean("digest_on", d.digestOn),
            digestTime = prefs.getInt("digest_time", d.digestTime),
            remindersOn = prefs.getBoolean("reminders_on", d.remindersOn),
            reminderDaysAhead = prefs.getInt("reminder_days", d.reminderDaysAhead),
            remindOverdue = prefs.getBoolean("remind_overdue", d.remindOverdue),
            weeklyOn = prefs.getBoolean("weekly_on", d.weeklyOn),
            weeklyDay = prefs.getInt("weekly_day", d.weeklyDay),
            momAuto = prefs.getBoolean("mom_auto", d.momAuto),
            startWithWindows = prefs.getBoolean("start_with_windows", d.startWithWindows),
        )
    }

    /** Dates on which the automatic preparation last ran ("digest", "reminders", "weekly"). */
    fun lastRun(kind: String): String? = prefs.getString("last_$kind", null)
    fun markRun(kind: String, date: String) = prefs.edit().putString("last_$kind", date).apply()

    // ------------------------------------------------------------------- queue

    /** Adds drafts, replacing any unsent draft with the same key. */
    fun enqueue(drafts: List<EmailDraft>) {
        if (drafts.isEmpty()) return
        val keys = drafts.map { it.key }.toSet()
        _queue.value = _queue.value.filter { it.key !in keys } + drafts
        saveQueue()
    }

    fun update(draft: EmailDraft) {
        _queue.value = _queue.value.map { if (it.id == draft.id) draft else it }
        saveQueue()
    }

    fun remove(ids: Collection<String>) {
        val set = ids.toSet()
        _queue.value = _queue.value.filter { it.id !in set }
        saveQueue()
    }

    fun recordSent(drafts: List<EmailDraft>, how: String) {
        val now = System.currentTimeMillis()
        _sent.value = (drafts.map { SentEmail(now, it.kind, it.to, it.subject, it.key, how) } + _sent.value).take(300)
        saveSent()
    }

    /** True when an e-mail with this key was already sent or saved to Outlook. */
    fun alreadyHandled(key: String): Boolean = _sent.value.any { it.key == key }

    private fun loadQueue(): List<EmailDraft> = runCatching {
        val a = JSONArray(queueFile.readText(Charsets.UTF_8))
        (0 until a.length()).map { EmailDraft.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveQueue() {
        runCatching { queueFile.writeText(JSONArray(_queue.value.map { it.toJson() }).toString(), Charsets.UTF_8) }
    }

    private fun loadSent(): List<SentEmail> = runCatching {
        val a = JSONArray(sentFile.readText(Charsets.UTF_8))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SentEmail(o.optLong("at"), o.optString("kind"), o.optString("to"), o.optString("subject"), o.optString("key"), o.optString("how"))
        }
    }.getOrDefault(emptyList())

    private fun saveSent() {
        runCatching {
            sentFile.writeText(
                JSONArray(_sent.value.map {
                    JSONObject().put("at", it.at).put("kind", it.kind).put("to", it.to).put("subject", it.subject)
                        .put("key", it.key).put("how", it.how)
                }).toString(),
                Charsets.UTF_8,
            )
        }
    }
}
