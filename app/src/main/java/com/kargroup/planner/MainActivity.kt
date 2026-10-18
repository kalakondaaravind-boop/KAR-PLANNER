package com.kargroup.planner

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import com.kargroup.planner.reminders.ReminderNotifications
import com.kargroup.planner.ui.KarPlannerApp
import com.kargroup.planner.ui.MainViewModel
import com.kargroup.planner.ui.theme.KarTheme

/** FragmentActivity (a ComponentActivity) so the fingerprint prompt can be shown. */
class MainActivity : FragmentActivity() {

    /** Same instance the Compose UI gets from viewModel(). */
    private val vm: MainViewModel by viewModels()

    /** Incremented whenever a reminder notification is tapped, so the UI opens the reminder popup. */
    private val openRemindersSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            KarTheme {
                KarPlannerApp(openRemindersSignal = openRemindersSignal.intValue)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ReminderNotifications.EXTRA_OPEN_REMINDERS, false) == true) {
            openRemindersSignal.intValue = openRemindersSignal.intValue + 1
            intent.removeExtra(ReminderNotifications.EXTRA_OPEN_REMINDERS)
        }
    }
}
