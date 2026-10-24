package com.kargroup.planner.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native Windows open / save dialogs. */
object FileDialogs {
    fun save(title: String, suggestedName: String): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.SAVE)
        d.file = suggestedName
        d.directory = System.getProperty("user.home") + File.separator + "Documents"
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name)
    }

    /** Open dialog that accepts several file types, e.g. listOf("xlsx", "csv"). */
    fun openAny(title: String, extensions: List<String>): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
        d.file = extensions.joinToString(";") { "*.$it" }
        d.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        d.directory = System.getProperty("user.home") + File.separator + "Downloads"
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name).takeIf { it.exists() }
    }

    fun open(title: String, extension: String): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
        d.file = "*.$extension"
        d.directory = System.getProperty("user.home") + File.separator + "Downloads"
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name).takeIf { it.exists() }
    }
}
