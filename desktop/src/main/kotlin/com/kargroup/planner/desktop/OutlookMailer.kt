package com.kargroup.planner.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Sends e-mails through the classic Outlook app installed on this PC (Outlook's COM
 * automation, driven by a small PowerShell script). Mail goes out from your own mailbox
 * and appears in Sent Items, exactly as if you had written it — no passwords or IT setup.
 */
object OutlookMailer {

    enum class Mode(val arg: String) {
        /** Send immediately. */
        SEND("send"),
        /** Open each e-mail in an Outlook window so you can edit it and press Send yourself. */
        DISPLAY("display"),
        /** Save into Outlook's Drafts folder. */
        DRAFT("draft"),
    }

    data class Result(val ok: Set<String>, val errors: Map<String, String>, val fatal: String? = null)

    private val script = """
param([string]${'$'}Path, [string]${'$'}Mode)
${'$'}ErrorActionPreference = 'Stop'
try {
  try { ${'$'}ol = [Runtime.InteropServices.Marshal]::GetActiveObject('Outlook.Application') }
  catch { ${'$'}ol = New-Object -ComObject Outlook.Application }
} catch {
  Write-Output ("FATAL Outlook could not be started: " + ${'$'}_.Exception.Message)
  exit 2
}
${'$'}items = Get-Content -Raw -Encoding UTF8 -LiteralPath ${'$'}Path | ConvertFrom-Json
foreach (${'$'}m in @(${'$'}items)) {
  try {
    ${'$'}mail = ${'$'}ol.CreateItem(0)
    ${'$'}mail.To = [string]${'$'}m.to
    if (${'$'}m.cc) { ${'$'}mail.CC = [string]${'$'}m.cc }
    ${'$'}mail.Subject = [string]${'$'}m.subject
    ${'$'}mail.HTMLBody = [string]${'$'}m.html
    if (${'$'}Mode -eq 'display') { ${'$'}mail.Display(${'$'}false) }
    elseif (${'$'}Mode -eq 'draft') { ${'$'}mail.Save() }
    else {
      [void]${'$'}mail.Recipients.ResolveAll()
      ${'$'}mail.Send()
    }
    Write-Output ("OK " + ${'$'}m.id)
  } catch {
    Write-Output ("ERR " + ${'$'}m.id + " " + ${'$'}_.Exception.Message)
  }
}
""".trimIndent()

    val isSupported: Boolean get() = AppDirs.isWindows

    fun run(drafts: List<EmailDraft>, mode: Mode): Result {
        if (drafts.isEmpty()) return Result(emptySet(), emptyMap())
        if (!isSupported) return Result(emptySet(), emptyMap(), "Outlook e-mail works on Windows with the classic Outlook app installed.")
        val dir = AppDirs.tempDir
        val json = File(dir, "outlook-${System.currentTimeMillis()}.json")
        val ps1 = File(dir, "kar-outlook.ps1")
        return try {
            json.writeText(
                JSONArray(drafts.map {
                    JSONObject().put("id", it.id).put("to", it.to).put("cc", it.cc).put("subject", it.subject).put("html", it.html)
                }).toString(),
                Charsets.UTF_8,
            )
            // Windows PowerShell 5.1 needs a BOM to read the script itself as UTF-8.
            ps1.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + script.toByteArray(Charsets.UTF_8))
            val p = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", ps1.absolutePath, "-Path", json.absolutePath, "-Mode", mode.arg,
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(180, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return Result(emptySet(), emptyMap(), "Outlook did not respond in time. Is a dialog open in Outlook?")
            }
            val ok = HashSet<String>()
            val errors = HashMap<String, String>()
            var fatal: String? = null
            out.lineSequence().map { it.trim() }.forEach { line ->
                when {
                    line.startsWith("OK ") -> ok += line.removePrefix("OK ").trim()
                    line.startsWith("ERR ") -> {
                        val rest = line.removePrefix("ERR ")
                        errors[rest.substringBefore(' ')] = rest.substringAfter(' ', "Unknown error")
                    }
                    line.startsWith("FATAL ") -> fatal = line.removePrefix("FATAL ")
                }
            }
            if (ok.isEmpty() && errors.isEmpty() && fatal == null) {
                fatal = out.lines().firstOrNull { it.isNotBlank() }?.take(200) ?: "Outlook did not answer."
            }
            Result(ok, errors, fatal)
        } catch (e: Exception) {
            Result(emptySet(), emptyMap(), e.message ?: e.toString())
        } finally {
            json.delete()
        }
    }

    // ---------------------------------------------------------------- startup

    private fun startupShortcut(): File? =
        System.getenv("APPDATA")?.let { File(it, "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\KAR Planner.lnk") }

    /** Adds or removes a "start with Windows" shortcut (the app then starts minimised to the tray). */
    fun setStartWithWindows(on: Boolean): Boolean {
        if (!isSupported) return false
        val lnk = startupShortcut() ?: return false
        if (!on) return lnk.delete() || !lnk.exists()
        val exe = System.getProperty("jpackage.app-path") ?: return false
        val ps = "\$s=(New-Object -ComObject WScript.Shell).CreateShortcut('${lnk.absolutePath.replace("'", "''")}');" +
            "\$s.TargetPath='${exe.replace("'", "''")}';\$s.Arguments='--minimized';\$s.Save()"
        return runCatching {
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps).redirectErrorStream(true).start()
            p.inputStream.readAllBytes()
            p.waitFor(30, TimeUnit.SECONDS) && lnk.exists()
        }.getOrDefault(false)
    }
}
