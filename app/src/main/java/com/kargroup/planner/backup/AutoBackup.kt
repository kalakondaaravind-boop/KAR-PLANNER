package com.kargroup.planner.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kargroup.planner.KarApp
import com.kargroup.planner.data.BackupCodec
import com.kargroup.planner.util.Ist
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Daily backup of all data as JSON into a folder the user picked (e.g. Google Drive). */
object AutoBackup {
    private const val WORK = "kar_auto_backup"
    private const val PREFIX = "KAR-Planner-auto-"
    private const val KEEP = 14

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Writes one backup file now. Returns the file name, or throws with a readable message. */
    suspend fun runNow(context: Context): String {
        val app = context.applicationContext as KarApp
        val folder = app.settings.current.backupFolder ?: error("Choose a backup folder first")
        val tree = Uri.parse(folder)
        val resolver = context.contentResolver
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val stamp = Ist.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ENGLISH))
        val name = "$PREFIX$stamp.json"
        val file = DocumentsContract.createDocument(resolver, dir, "application/json", name)
            ?: error("Could not create the backup file in the chosen folder")
        val json = BackupCodec.encode(app.repository.snapshot())
        resolver.openOutputStream(file)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not write to the chosen folder")
        app.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        runCatching { prune(context, tree) }
        return name
    }

    /** Keeps only the newest [KEEP] automatic backups in the folder. */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, String>>() // name, docId
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                if (n.startsWith(PREFIX)) found.add(n to c.getString(1))
            }
        }
        found.sortedByDescending { it.first }.drop(KEEP).forEach { (_, docId) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId)) }
        }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KarApp
        val s = app.settings.current
        if (!s.autoBackup || s.backupFolder == null) return Result.success()
        return runCatching { AutoBackup.runNow(applicationContext) }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}
