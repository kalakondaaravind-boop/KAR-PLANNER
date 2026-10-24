package com.kargroup.planner.desktop

import java.io.File

/** Where the desktop app keeps its data: %APPDATA%\KAR Planner on Windows. */
object AppDirs {
    val root: File by lazy {
        val base = System.getenv("APPDATA")?.let { File(it) }
            ?: File(System.getProperty("user.home"), if (isWindows) "AppData/Roaming" else ".config")
        File(base, "KAR Planner").apply { mkdirs() }
    }
    val dataFile: File get() = File(root, "kar-planner-data.json")
    val prefsDir: File get() = File(root, "settings").apply { mkdirs() }
    val backupsDir: File get() = File(root, "backups").apply { mkdirs() }
    val tempDir: File get() = File(root, "temp").apply { mkdirs() }

    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
