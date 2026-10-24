package com.kargroup.planner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Desktop text size, set in Settings → Display (or Ctrl + / Ctrl − / Ctrl 0).
 * [textSize] is the share of the normal Windows text size, from 50% to 120%.
 */
object DisplayPrefs {
    const val MIN = 0.50f
    const val MAX = 1.20f
    const val DEFAULT = 0.60f
    private const val KEY = "text_size"

    var textSize by mutableFloatStateOf(DEFAULT)
        private set

    fun load(app: DesktopApp) {
        textSize = app.uiPrefs.getInt(KEY, (DEFAULT * 100).toInt()).coerceIn((MIN * 100).toInt(), (MAX * 100).toInt()) / 100f
    }

    fun set(app: DesktopApp, value: Float) {
        val v = (Math.round(value * 20) / 20f).coerceIn(MIN, MAX) // 5% steps
        textSize = v
        app.uiPrefs.edit().putInt(KEY, Math.round(v * 100)).apply()
    }

    fun bigger(app: DesktopApp) = set(app, textSize + 0.05f)
    fun smaller(app: DesktopApp) = set(app, textSize - 0.05f)
    fun reset(app: DesktopApp) = set(app, DEFAULT)

    /** Font scale handed to Compose: the shared theme already draws text at 75%. */
    val fontScale: Float get() = textSize / (0.75f * DESKTOP_UI_SCALE)
}
