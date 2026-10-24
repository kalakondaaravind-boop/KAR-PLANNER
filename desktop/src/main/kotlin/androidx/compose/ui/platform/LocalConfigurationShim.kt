package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Desktop stand-in for Android's Configuration: the window size in dp (shared screens use it for layout). */
class Configuration(val screenWidthDp: Int, val screenHeightDp: Int)

val LocalConfiguration = staticCompositionLocalOf { Configuration(1280, 800) }
