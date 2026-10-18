package com.kargroup.planner.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.security.MessageDigest

/** User preferences for planning, reminders, backups and security. Times are minutes from midnight (IST). */
data class PlannerSettings(
    val workStart: Int = 9 * 60,
    val workEnd: Int = 19 * 60,
    val breakEnabled: Boolean = true,
    val breakStart: Int = 13 * 60,
    val breakEnd: Int = 14 * 60,
    /** ISO day numbers: 1 = Monday ... 7 = Sunday. */
    val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** 'YYYY-MM-DD' dates that are never booked. */
    val holidays: Set<String> = emptySet(),
    val reminderLeadMinutes: Int = 15,
    val exactReminders: Boolean = true,
    val autoBackup: Boolean = false,
    val backupFolder: String? = null,
    val lastBackupAt: Long? = null,
    val lockMode: String = LockMode.OFF,
    val pinHash: String? = null,
    val useBiometric: Boolean = true,
) {
    val workDayMinutes: Int
        get() = (workEnd - workStart - if (breakEnabled) (breakEnd - breakStart).coerceAtLeast(0) else 0).coerceAtLeast(0)

    fun isWorkingDay(date: java.time.LocalDate): Boolean =
        date.dayOfWeek.value in workDays && date.toString() !in holidays
}

object LockMode {
    const val OFF = "off"
    const val APP = "app"
    const val JOURNAL = "journal"
}

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("kar_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PlannerSettings> = _state
    val current: PlannerSettings get() = _state.value

    @Synchronized
    fun update(transform: (PlannerSettings) -> PlannerSettings) {
        val next = transform(_state.value).sanitized()
        save(next)
        _state.value = next
    }

    private fun PlannerSettings.sanitized(): PlannerSettings {
        val ws = workStart.coerceIn(0, 23 * 60)
        val we = workEnd.coerceIn(ws + 30, 24 * 60)
        val bs = breakStart.coerceIn(ws, we)
        val be = breakEnd.coerceIn(bs, we)
        return copy(
            workStart = ws, workEnd = we, breakStart = bs, breakEnd = be,
            workDays = workDays.filter { it in 1..7 }.toSet().ifEmpty { setOf(1, 2, 3, 4, 5) },
            reminderLeadMinutes = reminderLeadMinutes.coerceIn(0, 24 * 60),
        )
    }

    private fun load(): PlannerSettings {
        val d = PlannerSettings()
        return PlannerSettings(
            workStart = sp.getInt("work_start", d.workStart),
            workEnd = sp.getInt("work_end", d.workEnd),
            breakEnabled = sp.getBoolean("break_enabled", d.breakEnabled),
            breakStart = sp.getInt("break_start", d.breakStart),
            breakEnd = sp.getInt("break_end", d.breakEnd),
            workDays = readList("work_days")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: d.workDays,
            holidays = readList("holidays")?.toSet() ?: d.holidays,
            reminderLeadMinutes = sp.getInt("reminder_lead", d.reminderLeadMinutes),
            exactReminders = sp.getBoolean("exact_reminders", d.exactReminders),
            autoBackup = sp.getBoolean("auto_backup", d.autoBackup),
            backupFolder = sp.getString("backup_folder", null),
            lastBackupAt = sp.getLong("last_backup_at", 0L).takeIf { it > 0 },
            lockMode = sp.getString("lock_mode", d.lockMode) ?: d.lockMode,
            pinHash = sp.getString("pin_hash", null),
            useBiometric = sp.getBoolean("use_biometric", d.useBiometric),
        )
    }

    private fun save(s: PlannerSettings) {
        sp.edit()
            .putInt("work_start", s.workStart)
            .putInt("work_end", s.workEnd)
            .putBoolean("break_enabled", s.breakEnabled)
            .putInt("break_start", s.breakStart)
            .putInt("break_end", s.breakEnd)
            .putString("work_days", JSONArray(s.workDays.sorted().map { it.toString() }).toString())
            .putString("holidays", JSONArray(s.holidays.sorted()).toString())
            .putInt("reminder_lead", s.reminderLeadMinutes)
            .putBoolean("exact_reminders", s.exactReminders)
            .putBoolean("auto_backup", s.autoBackup)
            .putString("backup_folder", s.backupFolder)
            .putLong("last_backup_at", s.lastBackupAt ?: 0L)
            .putString("lock_mode", s.lockMode)
            .putString("pin_hash", s.pinHash)
            .putBoolean("use_biometric", s.useBiometric)
            .apply()
    }

    private fun readList(key: String): List<String>? {
        val raw = sp.getString(key, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrNull()
    }

    companion object {
        fun hashPin(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(("kar-planner:" + pin).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** "09:30" for 570. */
fun minutesToHHmm(m: Int): String = "%02d:%02d".format((m / 60) % 24, m % 60)
