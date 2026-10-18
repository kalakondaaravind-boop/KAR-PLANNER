package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kargroup.planner.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import com.kargroup.planner.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.JournalEntry
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import java.time.LocalDate
import kotlinx.coroutines.launch

/** Mood options: stored key to (emoji, label). */
val JournalMoods: List<Triple<String, String, String>> = listOf(
    Triple("great", "😄", "Great"),
    Triple("good", "🙂", "Good"),
    Triple("okay", "😐", "Okay"),
    Triple("low", "😕", "Low"),
    Triple("tough", "😣", "Tough"),
)

fun moodEmoji(key: String?): String? = JournalMoods.firstOrNull { it.first == key }?.second

/** Consecutive days with an entry, counting back from today (or yesterday if today is still empty). */
private fun journalStreak(dates: Set<String>, today: String): Int {
    var d = Ist.parse(today) ?: return 0
    if (d.toString() !in dates) d = d.minusDays(1)
    var n = 0
    while (d.toString() in dates) {
        n++
        d = d.minusDays(1)
    }
    return n
}

private val HeaderShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    entries: List<JournalEntry>,
    today: String,
    wide: Boolean,
    onSave: (date: String, mood: String?, title: String?, content: String, quiet: Boolean) -> Unit,
    onDelete: (JournalEntry) -> Unit,
    initialDate: String? = null,
) {
    val hPad = if (wide) 24.dp else 12.dp
    var selectedDate by rememberSaveable(initialDate) { mutableStateOf(initialDate ?: today) }
    val existing = entries.firstOrNull { it.date == selectedDate }

    // Editor fields reload whenever the date (or the saved entry) changes.
    var mood by remember(selectedDate, existing?.updatedAt) { mutableStateOf(existing?.mood) }
    var title by remember(selectedDate, existing?.updatedAt) { mutableStateOf(existing?.title ?: "") }
    var content by remember(selectedDate, existing?.updatedAt) { mutableStateOf(existing?.content ?: "") }
    val dirty = mood != existing?.mood || title.trim() != (existing?.title ?: "") || content.trimEnd() != (existing?.content ?: "")

    var search by rememberSaveable { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<JournalEntry?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Save quietly whenever the user moves to another date or leaves the page.
    fun autoSave() {
        if (dirty) onSave(selectedDate, mood, title, content, true)
    }
    fun goTo(date: String) {
        if (date == selectedDate) return
        autoSave()
        selectedDate = date
        scope.launch { listState.animateScrollToItem(0) }
    }
    val latestAutoSave by rememberUpdatedState(newValue = { autoSave() })
    DisposableEffect(Unit) { onDispose { latestAutoSave() } }

    val dates = remember(entries) { entries.map { it.date }.toSet() }
    val streak = remember(dates, today) { journalStreak(dates, today) }
    val selected = Ist.parse(selectedDate) ?: LocalDate.now(Ist.ZONE)
    val filtered = remember(entries, search) {
        val q = search.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            (it.title ?: "").contains(q, true) || it.content.contains(q, true) || Ist.formatFull(it.date).contains(q, true)
        }
    }
    val words = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ------------------------------------------------------------ header + date bar
        item(key = "journal-head") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .depthShadow(10.dp, HeaderShape)
                    .background(KarBrush.SkyHeader, HeaderShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text("My Journal", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Private · stays on this device · ${entries.size} entr${if (entries.size == 1) "y" else "ies"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    if (streak > 0) {
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LocalFireDepartment, null, tint = KarColors.Amber, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$streak-day streak",
                                    color = KarColors.SkyDeep,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { goTo(selected.minusDays(1).toString()) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous day", tint = Color.White)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                selected.format(Ist.longDayFormat) + " " + selected.year,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val rel = Ist.friendly(selectedDate)
                            if (rel == "Today" || rel == "Yesterday" || rel == "Tomorrow") {
                                Text(rel, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Outlined.CalendarMonth, "Pick date", tint = Color.White)
                        }
                        IconButton(onClick = { goTo(selected.plusDays(1).toString()) }) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next day", tint = Color.White)
                        }
                    }
                }
                if (selectedDate != today) {
                    TextButton(onClick = { goTo(today) }, modifier = Modifier.align(Alignment.End)) {
                        Text("Back to today", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ------------------------------------------------------------------ editor
        item(key = "journal-editor") {
            DepthCard(modifier = Modifier.fillMaxWidth(), elevation = 6.dp) {
                Column(Modifier.padding(16.dp)) {
                    Text("How did the day feel?", style = MaterialTheme.typography.labelLarge, color = KarColors.Slate)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        JournalMoods.forEach { (key, emoji, label) ->
                            PillChip("$emoji $label", mood == key, KarColors.SkyDeep) {
                                mood = if (mood == key) null else key
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("Headline for the day (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Journal") },
                        placeholder = {
                            Text("What went well today? What did I learn? What am I grateful for? What will I do better tomorrow?")
                        },
                        minLines = if (wide) 10 else 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            buildString {
                                append("$words word${if (words == 1) "" else "s"}")
                                when {
                                    dirty -> append(" · unsaved changes")
                                    existing != null -> append(" · saved ${Ist.formatStamp(existing.updatedAt)}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dirty) KarColors.Amber else KarColors.Slate,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (existing != null) {
                                TextButton(onClick = { confirmDelete = existing }) {
                                    Icon(Icons.Outlined.Delete, null, tint = KarColors.Red, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Delete", color = KarColors.Red)
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            GoldButton(
                                text = if (existing == null) "Save Entry" else "Save Changes",
                                onClick = { onSave(selectedDate, mood, title, content, false) },
                                enabled = dirty,
                                height = 42.dp,
                            )
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ past entries
        item(key = "journal-past-head") {
            Column {
                Text(
                    "PAST ENTRIES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = KarColors.SkyDeep,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                )
                if (entries.isNotEmpty()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search journal") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            item(key = "journal-empty") {
                EmptyCard(Icons.Outlined.Book, "No entries yet", "Write a few lines about today - it only takes a minute.")
            }
        } else if (filtered.isEmpty()) {
            item(key = "journal-nomatch") {
                Text("No entries match \"${search.trim()}\".", color = KarColors.Slate, modifier = Modifier.padding(8.dp))
            }
        }

        items(filtered, key = { "journal-${it.id}" }) { e ->
            val isSel = e.date == selectedDate
            DepthCard(
                modifier = Modifier.fillMaxWidth().animateItem(),
                elevation = if (isSel) 8.dp else 3.dp,
                onClick = { goTo(e.date) },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Surface(
                        color = if (isSel) KarColors.SkyDeep else KarColors.SkyLight,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            Modifier.width(54.dp).padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val d = Ist.parse(e.date)
                            Text(
                                d?.dayOfMonth?.toString() ?: "--",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isSel) Color.White else KarColors.SkyDeep,
                            )
                            Text(
                                (d?.month?.name?.take(3) ?: "") + " " + (d?.year?.toString()?.takeLast(2) ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) Color.White else KarColors.SkyDeep,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            moodEmoji(e.mood)?.let { Text("$it ", style = MaterialTheme.typography.titleMedium) }
                            Text(
                                e.title ?: Ist.friendly(e.date),
                                style = MaterialTheme.typography.titleSmall,
                                color = KarColors.Navy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (e.content.isNotBlank()) {
                            Text(
                                e.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = KarColors.Slate,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            Ist.parse(e.date)?.dayOfWeek?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = KarColors.SlateLight,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Ist.dateToPickerMillis(selected))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { goTo(Ist.pickerMillisToDate(it).toString()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    confirmDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color.White,
            title = { Text("Delete journal entry?") },
            text = { Text("The entry for ${Ist.formatFull(e.date)} will be permanently deleted.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(e); confirmDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = KarColors.Red),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    LaunchedEffect(today) {
        // Keep the editor on "today" when the day rolls over and nothing was being edited.
        if (!dirty && selectedDate < today && existing == null) selectedDate = today
    }
}
