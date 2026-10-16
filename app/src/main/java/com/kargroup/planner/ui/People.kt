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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.MetaPill
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.theme.KarColors

/** Workload summary for one assignee (name == null means unassigned). */
data class PersonSummary(
    val name: String?,
    val notStarted: Int,
    val initiated: Int,
    val overdue: Int,
    val completed: Int,
) {
    val open: Int get() = notStarted + initiated
    val total: Int get() = open + completed
}

fun personSummaries(tasks: List<TaskEntity>, today: String): List<PersonSummary> {
    val groups = tasks.groupBy { it.assignee?.trim()?.ifBlank { null }?.lowercase() }
    return groups.map { (_, list) ->
        // Show the most common spelling of the name.
        val display = list.mapNotNull { it.assignee?.trim()?.ifBlank { null } }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val open = list.filter { !it.done }
        PersonSummary(
            name = display,
            notStarted = open.count { it.status != TaskStatus.INITIATED },
            initiated = open.count { it.status == TaskStatus.INITIATED },
            overdue = open.count { it.dueDate != null && it.dueDate < today },
            completed = list.count { it.done },
        )
    }.sortedWith(
        compareBy<PersonSummary>({ it.name == null }, { -it.overdue }, { -it.open }, { it.name?.lowercase() ?: "" })
    )
}

private val HeaderShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleScreen(
    tasks: List<TaskEntity>,
    today: String,
    wide: Boolean,
    header: @Composable () -> Unit,
    onOpen: (String?) -> Unit,
) {
    val hPad = if (wide) 24.dp else 12.dp
    val people = remember(tasks, today) { personSummaries(tasks, today) }
    val named = people.count { it.name != null }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = hPad, end = hPad, top = 12.dp, bottom = 110.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .depthShadow(10.dp, HeaderShape)
                    .background(KarBrush.SkyHeader, HeaderShape)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Text("By Person", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "$named ${if (named == 1) "person" else "people"} · tap a card to review their activities",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (people.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyCard(Icons.Outlined.People, "No activities yet", "Assign activities to people and they will appear here.")
            }
        }
        items(people, key = { it.name?.lowercase() ?: "__unassigned" }) { p ->
            PersonCard(p) { onOpen(p.name) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(p: PersonSummary, onClick: () -> Unit) {
    val initials = p.name?.split(" ")?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.take(1).uppercase() } ?: "?"
    val avatar = if (p.name == null) KarColors.SlateLight else KarColors.SkyDeep
    DepthCard(modifier = Modifier.fillMaxWidth(), elevation = 5.dp, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(avatar.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, color = avatar, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.name ?: "Unassigned",
                        style = MaterialTheme.typography.titleMedium,
                        color = KarColors.Navy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${p.open} open · ${p.completed} done", style = MaterialTheme.typography.labelSmall, color = KarColors.Slate)
                }
            }
            Spacer(Modifier.height(12.dp))
            val fraction = if (p.total == 0) 0f else p.completed.toFloat() / p.total
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = KarColors.Green,
                trackColor = KarColors.Border,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (p.overdue > 0) MetaPill("${p.overdue} overdue", KarColors.Red, dotColor = KarColors.Red)
                MetaPill("${p.initiated} initiated", KarColors.SkyDeep, dotColor = KarColors.SkyDeep)
                MetaPill("${p.notStarted} not started", KarColors.Slate, dotColor = KarColors.Slate)
                MetaPill("${p.completed} completed", KarColors.Green, dotColor = KarColors.Green)
            }
        }
    }
}
