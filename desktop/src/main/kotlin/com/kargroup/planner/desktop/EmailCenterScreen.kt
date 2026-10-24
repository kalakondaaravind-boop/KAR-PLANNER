package com.kargroup.planner.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val HeaderShape = RoundedCornerShape(16.dp)

private fun kindColor(kind: String): Color = when (kind) {
    EmailKind.MOM -> Color(0xFF7C3AED)
    EmailKind.REMINDER -> KarColors.Amber
    EmailKind.DIGEST -> KarColors.SkyDeep
    EmailKind.WEEKLY -> KarColors.Green
    else -> KarColors.Slate
}

/** Opens the e-mail in the default browser so the full layout can be checked before sending. */
private fun previewInBrowser(d: EmailDraft) {
    runCatching {
        val f = File(AppDirs.tempDir, "preview-${d.id}.html")
        f.writeText(d.html, Charsets.UTF_8)
        java.awt.Desktop.getDesktop().browse(f.toURI())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmailCenterScreen(
    center: EmailCenter,
    meetings: List<MeetingMinuteEntity>,
    wide: Boolean,
    onMessage: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val queue by center.store.queue.collectAsState()
    val sent by center.store.sent.collectAsState()
    val es by center.store.settings.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var prepareMenu by remember { mutableStateOf(false) }
    var pickMeeting by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(center.missingPeople) }
    val scope = rememberCoroutineScope()

    // New drafts are selected by default; removed ones drop out of the selection.
    var known by remember { mutableStateOf(queue.map { it.id }.toSet()) }
    LaunchedEffect(queue) {
        val ids = queue.map { it.id }.toSet()
        selected = (selected intersect ids) + (ids - known)
        known = ids
    }

    fun deliver(ids: Collection<String>, mode: OutlookMailer.Mode) {
        if (ids.isEmpty() || busy) return
        busy = true
        scope.launch {
            val msg = center.deliver(ids, mode)
            busy = false
            onMessage(msg)
        }
    }

    fun prepare(what: String) {
        prepareMenu = false
        scope.launch {
            val n = withContext(Dispatchers.IO) {
                when (what) {
                    "digest" -> center.prepareDigest()
                    "reminders" -> center.prepareReminders()
                    "weekly" -> center.prepareWeekly()
                    else -> { center.prepareTest(); 1 }
                }
            }
            missing = center.missingPeople
            onMessage(if (n == 0) "Nothing to prepare right now" else "$n e-mail${if (n == 1) "" else "s"} ready to review")
        }
    }

    val hPad = if (wide) 24.dp else 12.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 16.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().depthShadow(10.dp, HeaderShape).background(KarBrush.SkyHeader, HeaderShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("Outlook E-mails", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Review, then send through your Outlook" + if (es.myEmail.isNotBlank()) " · from ${es.myEmail}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        HeaderAction("Prepare", Icons.Outlined.PlaylistAdd, primary = false) { prepareMenu = true }
                        DropdownMenu(expanded = prepareMenu, onDismissRequest = { prepareMenu = false }) {
                            DropdownMenuItem(text = { Text("Daily digest for me") }, onClick = { prepare("digest") })
                            DropdownMenuItem(text = { Text("Action reminders to owners") }, onClick = { prepare("reminders") })
                            DropdownMenuItem(text = { Text("Weekly update to each person") }, onClick = { prepare("weekly") })
                            DropdownMenuItem(text = { Text("MoM for a meeting…") }, onClick = { prepareMenu = false; pickMeeting = true })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Test e-mail to me") }, onClick = { prepare("test") })
                        }
                    }
                    HeaderAction("Send selected (${selected.size})", Icons.Outlined.Send, primary = true, enabled = selected.isNotEmpty() && !busy) {
                        deliver(selected, OutlookMailer.Mode.SEND)
                    }
                    HeaderAction("Open in Outlook", Icons.Outlined.OpenInNew, enabled = selected.isNotEmpty() && !busy) {
                        deliver(selected, OutlookMailer.Mode.DISPLAY)
                    }
                    HeaderAction("Save to Drafts", Icons.Outlined.Drafts, enabled = selected.isNotEmpty() && !busy) {
                        deliver(selected, OutlookMailer.Mode.DRAFT)
                    }
                    HeaderAction("Remove", Icons.Outlined.Delete, enabled = selected.isNotEmpty() && !busy) {
                        center.store.remove(selected)
                    }
                    if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp).padding(4.dp))
                }
            }
        }

        // Warnings
        val notes = buildList {
            if (!OutlookMailer.isSupported) add("Sending needs Windows with the classic Outlook app. You can still prepare and preview e-mails.")
            if (es.myEmail.isBlank()) add("Add your name and e-mail in Settings → E-mail & Outlook (used for the daily digest and as CC).")
            if (missing.isNotEmpty()) add("No e-mail address for: ${missing.joinToString(", ")}. Add it under By Person so they get reminders.")
        }
        if (notes.isNotEmpty()) {
            item {
                Surface(color = KarColors.Amber.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        notes.forEach { n ->
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                                Icon(Icons.Outlined.WarningAmber, null, tint = KarColors.Amber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(n, style = MaterialTheme.typography.bodySmall, color = KarColors.Ink, modifier = Modifier.weight(1f))
                            }
                        }
                        if (es.myEmail.isBlank()) TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                    }
                }
            }
        }

        // Automatic schedule note
        item {
            Text(
                buildString {
                    append("Prepared automatically at ${com.kargroup.planner.data.minutesToHHmm(es.digestTime)} IST on working days: ")
                    append(listOfNotNull(
                        "daily digest".takeIf { es.digestOn },
                        "action reminders".takeIf { es.remindersOn },
                        "weekly update (${listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[es.weeklyDay - 1]})".takeIf { es.weeklyOn },
                        "MoM after saving a meeting".takeIf { es.momAuto },
                    ).joinToString(", ").ifBlank { "nothing (turned off in Settings)" })
                    append(". Nothing is sent until you press Send.")
                },
                style = MaterialTheme.typography.labelMedium,
                color = KarColors.Slate,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (queue.isEmpty()) {
            item {
                DepthCard(Modifier.fillMaxWidth(), elevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Email, null, tint = KarColors.SkyMid, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(6.dp))
                        Text("No e-mails waiting", style = MaterialTheme.typography.titleMedium, color = KarColors.Navy)
                        Text("Use Prepare, or save a meeting with action points.", style = MaterialTheme.typography.bodySmall, color = KarColors.Slate)
                    }
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Waiting for review (${queue.size})", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = if (selected.size == queue.size) emptySet() else queue.map { it.id }.toSet() }) {
                        Text(if (selected.size == queue.size) "Select none" else "Select all")
                    }
                }
            }
            items(queue.sortedByDescending { it.createdAt }, key = { it.id }) { d ->
                DraftCard(
                    d = d,
                    checked = d.id in selected,
                    busy = busy,
                    onCheck = { on -> selected = if (on) selected + d.id else selected - d.id },
                    onChange = { center.store.update(it) },
                    onPreview = { previewInBrowser(d) },
                    onSend = { deliver(listOf(d.id), OutlookMailer.Mode.SEND) },
                    onOutlook = { deliver(listOf(d.id), OutlookMailer.Mode.DISPLAY) },
                    onRemove = { center.store.remove(listOf(d.id)) },
                )
            }
        }

        if (sent.isNotEmpty()) {
            item {
                DepthCard(Modifier.fillMaxWidth(), elevation = 3.dp) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Recently sent", style = MaterialTheme.typography.titleSmall, color = KarColors.Navy)
                        Spacer(Modifier.height(6.dp))
                        sent.take(25).forEach { s ->
                            HorizontalDivider(color = KarColors.Border)
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill(EmailKind.label(s.kind), kindColor(s.kind))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.subject, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${s.to} · ${Ist.formatStamp(s.at)} · ${when (s.how) { "display" -> "opened in Outlook"; "draft" -> "saved to Drafts"; else -> "sent" }}",
                                        style = MaterialTheme.typography.labelSmall, color = KarColors.Slate, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickMeeting) {
        AlertDialog(
            onDismissRequest = { pickMeeting = false },
            containerColor = Color.White,
            title = { Text("E-mail the MoM of…") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (meetings.isEmpty()) item { Text("No meetings yet", color = KarColors.Slate) }
                    items(meetings.take(40), key = { it.id }) { m ->
                        Surface(
                            onClick = {
                                pickMeeting = false
                                center.prepareMom(m)
                                onMessage("MoM e-mail ready to review")
                            },
                            color = Color.Transparent,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(m.title, fontWeight = FontWeight.SemiBold, color = KarColors.Navy)
                                Text(
                                    "${Ist.formatFull(m.meetingDate)} · ${m.actionItems.count { it.description.isNotBlank() }} action points",
                                    style = MaterialTheme.typography.labelSmall, color = KarColors.Slate,
                                )
                            }
                        }
                        HorizontalDivider(color = KarColors.Border)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickMeeting = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun HeaderAction(label: String, icon: ImageVector, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (primary) Color.White else Color.White.copy(alpha = if (enabled) 0.18f else 0.08f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val c = if (primary) KarColors.SkyDeep else Color.White.copy(alpha = if (enabled) 1f else 0.5f)
            Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = c, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(
    d: EmailDraft,
    checked: Boolean,
    busy: Boolean,
    onCheck: (Boolean) -> Unit,
    onChange: (EmailDraft) -> Unit,
    onPreview: () -> Unit,
    onSend: () -> Unit,
    onOutlook: () -> Unit,
    onRemove: () -> Unit,
) {
    var to by remember(d.id) { mutableStateOf(d.to) }
    var cc by remember(d.id) { mutableStateOf(d.cc) }
    val emailKeys = KeyboardOptions(keyboardType = KeyboardType.Email)
    DepthCard(Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = checked, onCheckedChange = onCheck, colors = CheckboxDefaults.colors(checkedColor = KarColors.SkyDeep))
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(EmailKind.label(d.kind), kindColor(d.kind))
                    Spacer(Modifier.width(8.dp))
                    Text(Ist.formatStamp(d.createdAt), style = MaterialTheme.typography.labelSmall, color = KarColors.SlateLight)
                }
                Spacer(Modifier.height(4.dp))
                Text(d.subject, fontWeight = FontWeight.SemiBold, color = KarColors.Navy, style = MaterialTheme.typography.titleSmall)
                Text(d.summary, style = MaterialTheme.typography.labelMedium, color = KarColors.Slate)
                d.warnings.forEach { w ->
                    Text("⚠ $w", style = MaterialTheme.typography.labelMedium, color = KarColors.Red, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it; onChange(d.copy(to = it.trim())) },
                        label = { Text("To (separate with ;)") },
                        singleLine = true,
                        keyboardOptions = emailKeys,
                        modifier = Modifier.weight(1.4f),
                    )
                    OutlinedTextField(
                        value = cc,
                        onValueChange = { cc = it; onChange(d.copy(cc = it.trim())) },
                        label = { Text("CC") },
                        singleLine = true,
                        keyboardOptions = emailKeys,
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onPreview) { Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Preview") }
                    TextButton(onClick = onOutlook, enabled = !busy) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Edit in Outlook") }
                    TextButton(onClick = onSend, enabled = !busy && to.isNotBlank()) { Icon(Icons.Outlined.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Send now") }
                    TextButton(onClick = onRemove) { Text("Remove", color = KarColors.Slate) }
                }
            }
        }
    }
}
