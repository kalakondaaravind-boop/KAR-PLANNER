package com.kargroup.planner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.kargroup.planner.backup.AutoBackup
import com.kargroup.planner.data.KarDatabase
import com.kargroup.planner.data.KarRepository
import com.kargroup.planner.data.SettingsStore
import com.kargroup.planner.reminders.ReminderNotifications
import com.kargroup.planner.reminders.ReminderPrefs
import com.kargroup.planner.reminders.TaskAlarms
import com.kargroup.planner.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KarApp : Application() {

    val database: KarDatabase by lazy { KarDatabase.build(this) }
    val repository: KarRepository by lazy { KarRepository(database) }
    val reminderPrefs: ReminderPrefs by lazy { ReminderPrefs(this) }
    val appPrefs: SharedPreferences by lazy { getSharedPreferences("kar_app", Context.MODE_PRIVATE) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    /** Background scope that lives as long as the app process. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        ReminderNotifications.schedule(this)

        // Keep exact reminders and the home-screen widget in step with the data.
        appScope.launch {
            combine(repository.tasks, repository.meetings, settings.state) { t, m, s -> Triple(t, m, s) }
                .collectLatest { (tasks, _, s) ->
                    delay(800) // settle bursts of changes
                    runCatching { TaskAlarms.rescheduleAll(this@KarApp, tasks, s) }
                    runCatching { TodayWidget.refresh(this@KarApp) }
                }
        }
        // Daily automatic backup on/off.
        appScope.launch {
            settings.state.map { it.autoBackup && it.backupFolder != null }.distinctUntilChanged().collectLatest { on ->
                if (on) AutoBackup.schedule(this@KarApp) else AutoBackup.cancel(this@KarApp)
            }
        }
    }
}
