package com.kargroup.planner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.kargroup.planner.data.KarDatabase
import com.kargroup.planner.data.KarRepository
import com.kargroup.planner.reminders.ReminderNotifications
import com.kargroup.planner.reminders.ReminderPrefs

class KarApp : Application() {

    val database: KarDatabase by lazy { KarDatabase.build(this) }
    val repository: KarRepository by lazy { KarRepository(database) }
    val reminderPrefs: ReminderPrefs by lazy { ReminderPrefs(this) }
    val appPrefs: SharedPreferences by lazy { getSharedPreferences("kar_app", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        ReminderNotifications.schedule(this)
    }
}
