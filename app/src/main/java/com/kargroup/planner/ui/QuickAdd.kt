package com.kargroup.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.data.Priority
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.OutlinedTextField
import com.kargroup.planner.ui.components.Text
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import java.time.DayOfWeek
import java.time.LocalDate

/** Result of parsing one quick-add line. */
data class QuickParse(
    val title: String,
    val date: String? = null,
    val time: String? = null,
    val minutes: Int? = null,
    val assignee: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val priority: String? = null,
    val unknownPerson: String? = null,
    val unknownProject: String? = null,
)

/**
 * Turns "CALL VENDOR TOMORROW 3PM 15MIN @RAVI #OPS !!" into a ready activity.
 *  - date: today, tomorrow, mon..sun (next occurrence), 25/10, 25-10-2026
 *  - time: 3pm, 3:30pm, 15:30
 *  - estimate: 15min, 15m, 1h, 1.5h, 1h30m
 *  - @person / #project (prefix match), !!! very high, !! high, ! medium
 */
object QuickAddParser {
    private val weekdays = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
    )
    private val time12 = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?(am|pm)$")
    private val time24 = Regex("^(\\d{1,2})[:.](\\d{2})$")
    private val dur = Regex("^(?:(\\d+(?:\\.\\d+)?)h(?:rs?|ours?)?)?(?:(\\d+)m(?:in(?:s|utes?)?)?)?$")
    private val dmy = Regex("^(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?$")

    fun parse(input: String, persons: List<PersonEntity>, projects: List<ProjectEntity>, today: LocalDate = Ist.today()): QuickParse {
        var date: LocalDate? = null
        var time: String? = null
        var minutes: Int? = null
        var assignee: String? = null
        var unknownPerson: String? = null
        var project: ProjectEntity? = null
        var unknownProject: String? = null
        var priority: String? = null
        val words = ArrayList<String>()

        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        while (i < tokens.size) {
            val raw = tokens[i]
            val t = raw.lowercase()
            val next = tokens.getOrNull(i + 1)?.lowercase()
            when {
                t == "!!!" -> priority = Priority.VERY_HIGH
                t == "!!" -> priority = Priority.HIGH
                t == "!" -> priority = Priority.MEDIUM
                t.startsWith("@") && t.length > 1 -> {
                    val key = t.drop(1).replace("_", " ")
                    val match = persons.firstOrNull { it.name.lowercase() == key }
                        ?: persons.firstOrNull { it.name.lowercase().startsWith(key) }
                        ?: persons.firstOrNull { p -> p.name.lowercase().split(" ").any { it.startsWith(key) } }
                    if (match != null) assignee = match.name else unknownPerson = raw.drop(1)
                }
                t.startsWith("#") && t.length > 1 -> {
                    val key = t.drop(1).replace("_", " ")
                    val match = projects.firstOrNull { it.name.lowercase() == key }
                        ?: projects.firstOrNull { it.name.lowercase().startsWith(key) }
                        ?: projects.firstOrNull { p -> p.name.lowercase().split(" ").any { it.startsWith(key) } }
                    if (match != null) project = match else unknownProject = raw.drop(1)
                }
                t == "today" || t == "tod" -> date = today
                t == "tomorrow" || t == "tmrw" || t == "tmr" -> date = today.plusDays(1)
                t == "next" && next != null && weekdays.containsKey(next) -> {
                    date = nextWeekday(today, weekdays.getValue(next)).let { if (it == today) it.plusDays(7) else it }
                    i++
                }
                weekdays.containsKey(t) -> date = nextWeekday(today, weekdays.getValue(t))
                t == "at" && next != null && (time12.matches(next) || time24.matches(next)) -> { /* skip "at" */ }
                time12.matches(t) -> {
                    val m = time12.find(t)!!
                    var h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
                    if (h in 1..12 && min in 0..59) {
                        if (m.groupValues[3] == "pm" && h != 12) h += 12
                        if (m.groupValues[3] == "am" && h == 12) h = 0
                        time = "%02d:%02d".format(h, min)
                    } else words += raw
                }
                time24.matches(t) -> {
                    val m = time24.find(t)!!
                    val h = m.groupValues[1].toInt()
                    val min = m.groupValues[2].toInt()
                    if (h in 0..23 && min in 0..59) time = "%02d:%02d".format(h, min) else words += raw
                }
                dmy.matches(t) -> {
                    val m = dmy.find(t)!!
                    val d = m.groupValues[1].toInt()
                    val mo = m.groupValues[2].toInt()
                    val yRaw = m.groupValues[3]
                    val y = when {
                        yRaw.isEmpty() -> today.year
                        yRaw.length == 2 -> 2000 + yRaw.toInt()
                        else -> yRaw.toInt()
                    }
                    val parsed = runCatching { LocalDate.of(y, mo, d) }.getOrNull()
                    if (parsed != null) {
                        date = if (yRaw.isEmpty() && parsed.isBefore(today)) parsed.plusYears(1) else parsed
                    } else words += raw
                }
                t.any { it.isDigit() } && (t.endsWith("m") || t.endsWith("min") || t.endsWith("mins") || t.endsWith("h") ||
                    t.endsWith("hr") || t.endsWith("hrs") || t.endsWith("minutes")) && dur.matches(t) -> {
                    val m = dur.find(t)!!
                    val h = m.groupValues[1].toDoubleOrNull() ?: 0.0
                    val mm = m.groupValues[2].toIntOrNull() ?: 0
                    val total = (h * 60).toInt() + mm
                    if (total in 1..24 * 60) minutes = total else words += raw
                }
                else -> words += raw
            }
            i++
        }
        return QuickParse(
            title = words.joinToString(" ").trim(),
            date = date?.toString() ?: if (time != null) today.toString() else null,
            time = time,
            minutes = minutes,
            assignee = assignee,
            projectId = project?.id,
            projectName = project?.name,
            priority = priority,
            unknownPerson = unknownPerson,
            unknownProject = unknownProject,
        )
    }

    private fun nextWeekday(from: LocalDate, dow: DayOfWeek): LocalDate {
        var d = from
        while (d.dayOfWeek != dow) d = d.plusDays(1)
        return d
    }

    fun toTask(p: QuickParse): TaskEntity = TaskEntity(
        text = p.title.uppercase(),
        dueDate = p.date,
        dueTime = p.time,
        estimatedMinutes = p.minutes,
        assignee = p.assignee,
        projectId = p.projectId,
        priority = p.priority,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddBar(
    persons: List<PersonEntity>,
    projects: List<ProjectEntity>,
    onAdd: (TaskEntity) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, persons, projects) { QuickAddParser.parse(text, persons, projects) }
    val canAdd = parsed.title.isNotBlank()
    val submit = {
        if (canAdd) {
            onAdd(QuickAddParser.toTask(parsed))
            text = ""
        }
    }
    DepthCard(Modifier.fillMaxWidth(), elevation = 4.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, tint = KarColors.Amber, modifier = Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Quick add: call vendor tomorrow 3pm 15min @name #project !!", maxLines = 1) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                FilledIconButton(
                    onClick = submit,
                    enabled = canAdd,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = KarColors.SkyDeep, contentColor = Color.White),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Icon(Icons.Outlined.Add, "Add") }
            }
            if (text.isNotBlank()) {
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(if (parsed.title.isBlank()) "Type a title" else parsed.title, KarColors.Navy)
                    parsed.date?.let { Chip("📅 " + Ist.friendly(it), KarColors.SkyDeep) }
                    parsed.time?.let { Chip("⏰ " + formatTime12(it), KarColors.SkyDeep) }
                    parsed.minutes?.let { Chip("⏱ " + formatMinutes(it), KarColors.SkyDeep) }
                    parsed.assignee?.let { Chip("👤 $it", KarColors.Green) }
                    parsed.unknownPerson?.let { Chip("👤 $it not in people list", KarColors.Red) }
                    parsed.projectName?.let { Chip("📁 $it", KarColors.Green) }
                    parsed.unknownProject?.let { Chip("📁 $it not found", KarColors.Red) }
                    parsed.priority?.let { Chip("⚑ " + priorityLabel(it), KarColors.Amber) }
                }
            }
        }
    }
}

private fun priorityLabel(p: String): String = when (p) {
    Priority.VERY_HIGH -> "Very High"
    Priority.HIGH -> "High"
    else -> "Medium"
}

@Composable
private fun Chip(label: String, color: Color) {
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
