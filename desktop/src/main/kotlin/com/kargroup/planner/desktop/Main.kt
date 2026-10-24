package com.kargroup.planner.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.kargroup.planner.ui.KarPlannerApp
import com.kargroup.planner.ui.MainViewModel
import com.kargroup.planner.ui.Screen
import com.kargroup.planner.ui.theme.KarTheme

/**
 * The shared theme draws text at 75% of the system size (tuned for the tablet). On a PC that
 * is too small, so the desktop scales fonts back up to about 95% of the Windows size.
 */
private const val DESKTOP_FONT_SCALE = 0.95f / 0.75f

@Suppress("DEPRECATION")
fun main(args: Array<String>) {
    val startHidden = args.any { it.equals("--minimized", ignoreCase = true) }
    val desktop = DesktopApp()
    desktop.emails.startAutomation()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { desktop.database.saveNow() } })

    application {
        val vm = remember { MainViewModel(desktop) }
        var visible by remember { mutableStateOf(!startHidden) }
        var openReminders by remember { mutableIntStateOf(0) }
        val trayState = rememberTrayState()
        val queue by desktop.emails.store.queue.collectAsState()
        val icon = painterResource("kar_icon.png")
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1366.dp, 860.dp))

        // "N e-mails ready" → tray notification and an in-app message.
        LaunchedEffect(Unit) {
            desktop.emails.notices.collect { msg ->
                trayState.sendNotification(Notification("KAR Planner", msg, Notification.Type.Info))
                vm.toast(msg)
            }
        }
        // Lock again after a minute away (when a PIN lock is on).
        LaunchedEffect(visible, windowState.isMinimized) {
            if (!visible || windowState.isMinimized) vm.onBackground() else vm.onForeground()
        }

        Tray(
            icon = icon,
            state = trayState,
            tooltip = "KAR Planner",
            onAction = { visible = true; windowState.isMinimized = false },
            menu = {
                Item("Open KAR Planner", onClick = { visible = true; windowState.isMinimized = false })
                Item(
                    if (queue.isEmpty()) "Outlook e-mails" else "Review ${queue.size} e-mail(s)",
                    onClick = { visible = true; windowState.isMinimized = false; vm.navigate(Screen.Emails) },
                )
                Item("Reminders", onClick = { visible = true; windowState.isMinimized = false; openReminders++ })
                Separator()
                Item("Exit", onClick = {
                    desktop.database.saveNow()
                    exitApplication()
                })
            },
        )

        Window(
            onCloseRequest = {
                // Closing keeps the app in the tray so e-mails can still be prepared on time.
                visible = false
                trayState.sendNotification(Notification("KAR Planner is still running", "Find it in the system tray. Use Exit there to close it completely.", Notification.Type.None))
            },
            visible = visible,
            title = "KAR Planner",
            icon = icon,
            state = windowState,
        ) {
            val base = LocalDensity.current
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val w = maxWidth.value.toInt()
                val h = maxHeight.value.toInt()
                CompositionLocalProvider(
                    LocalConfiguration provides Configuration(w, h),
                    LocalDensity provides Density(base.density, base.fontScale * DESKTOP_FONT_SCALE),
                ) {
                    KarTheme {
                        KarPlannerApp(vm = vm, app = desktop, openRemindersSignal = openReminders)
                    }
                }
            }
        }
    }
}
