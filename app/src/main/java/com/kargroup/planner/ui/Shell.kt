package com.kargroup.planner.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.kargroup.planner.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.KarEmblem
import com.kargroup.planner.ui.components.KarLogo
import com.kargroup.planner.ui.components.SoftIconTile
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.components.pressDepth
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.delay

// =============================================================================
// App bar (white, corporate) with live IST clock
// =============================================================================

@Composable
internal fun TopBar(title: String, wide: Boolean, onMenu: (() -> Unit)?, onAdd: () -> Unit, onSearch: () -> Unit = {}) {
    Column(Modifier.depthShadow(4.dp, RectangleShape).background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (onMenu != null) 4.dp else 24.dp, end = if (wide) 24.dp else 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onMenu != null) {
                IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "Menu", tint = KarColors.Navy) }
                KarEmblem(22.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = KarColors.Navy,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "KAR GROUP · PLANNER",
                    color = KarColors.Slate,
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Search", tint = KarColors.Navy) }
            Spacer(Modifier.width(4.dp))
            IstClockBadge(showDate = true, compact = !wide)
            if (wide) {
                Spacer(Modifier.width(14.dp))
                GoldButton("Add Activity", onAdd, icon = Icons.Outlined.Add, height = 44.dp)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(KarColors.SkyMid, KarColors.Sky.copy(alpha = 0.25f), Color.Transparent)))
        )
    }
}

@Composable
fun IstClockBadge(showDate: Boolean, compact: Boolean) {
    var now by remember { mutableStateOf(Ist.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Ist.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KarColors.SkyWash,
        border = BorderStroke(1.dp, KarColors.Border),
    ) {
        Column(
            Modifier.padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(KarColors.Green))
                Spacer(Modifier.width(5.dp))
                Text("IST ", color = KarColors.SkyDeep, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    now.format(Ist.clockFormat),
                    color = KarColors.Navy,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 12.sp else 15.sp,
                    maxLines = 1,
                )
            }
            if (showDate) {
                Text(
                    now.format(Ist.headerDateFormat),
                    color = KarColors.Slate,
                    fontSize = if (compact) 9.sp else 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

// =============================================================================
// KPI stat cards - responsive grid that always fits the available width
// =============================================================================

private data class StatSpec(val label: String, val value: Int, val color: Color, val icon: ImageVector, val target: Screen)

@Composable
internal fun StatsRow(tasks: List<TaskEntity>, today: String, onNavigate: (Screen) -> Unit) {
    val open = tasks.count { !it.done }
    val dueToday = tasks.count { !it.done && it.dueDate == today }
    val recurring = tasks.count { !it.done && it.isRecurring }
    val completed = tasks.count { it.done }
    val cards = listOf(
        StatSpec("Open", open, KarColors.SkyDeep, Icons.Outlined.ViewList, Screen.AllTasks),
        StatSpec("Due Today", dueToday, KarColors.Amber, Icons.Outlined.Today, Screen.Today),
        StatSpec("Recurring", recurring, KarColors.Blue, Icons.Outlined.Repeat, Screen.Recurring),
        StatSpec("Completed", completed, KarColors.Green, Icons.Outlined.TaskAlt, Screen.Completed),
    )
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 4 across when there is room, otherwise a 2 x 2 grid - never scrolls sideways.
        val perRow = if (maxWidth >= 760.dp) 4 else 2
        val gap = if (perRow == 4) 14.dp else 10.dp
        val compactCards = perRow == 2 && maxWidth < 420.dp
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            cards.chunked(perRow).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowCards.forEach { StatCard(it, Modifier.weight(1f), compactCards, onNavigate) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(spec: StatSpec, modifier: Modifier, compact: Boolean, onNavigate: (Screen) -> Unit) {
    val animatedValue by animateIntAsState(targetValue = spec.value, animationSpec = tween(700), label = "stat")
    DepthCard(modifier = modifier, shape = RoundedCornerShape(14.dp), elevation = 4.dp, onClick = { onNavigate(spec.target) }) {
        Row(
            Modifier.padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    spec.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                    color = KarColors.Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    animatedValue.toString(),
                    fontSize = if (compact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = KarColors.Navy,
                )
            }
            SoftIconTile(spec.icon, spec.color, if (compact) 36.dp else 42.dp)
        }
        // Accent underline
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Brush.horizontalGradient(listOf(spec.color, spec.color.copy(alpha = 0.0f))))
        )
    }
}

// =============================================================================
// Full sidebar (landscape tablets and the phone drawer) - navy corporate panel
// =============================================================================

@Composable
internal fun Sidebar(
    screen: Screen,
    tasks: List<TaskEntity>,
    today: String,
    alertCount: Int,
    onNavigate: (Screen) -> Unit,
    onNewActivity: () -> Unit,
    onReminders: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val counts = remember(tasks, today) { navCounts(tasks, today) }

    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        KarLogo(Modifier.padding(start = 8.dp), height = 40.dp)
        Row(Modifier.padding(start = 10.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 16.dp, height = 2.dp).background(KarColors.SkyMid))
            Spacer(Modifier.width(8.dp))
            Text("PLANNER", color = KarColors.SkyDeep, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontSize = 10.sp)
        }
        Spacer(Modifier.height(20.dp))
        GoldButton("New Activity", onNewActivity, Modifier.fillMaxWidth(), icon = Icons.Outlined.Add, height = 46.dp)

        NavLabel("Views")
        NavItem("Scheduler", Icons.Outlined.Schedule, screen == Screen.Schedule, counts.open) { onNavigate(Screen.Schedule) }
        NavItem("Today", Icons.Outlined.Today, screen == Screen.Today, counts.today) { onNavigate(Screen.Today) }
        NavItem("Inbox", Icons.Outlined.Inbox, screen == Screen.Inbox, counts.inbox) { onNavigate(Screen.Inbox) }
        NavItem("Overdue", Icons.Outlined.ReportProblem, screen == Screen.Overdue, counts.overdue, KarColors.Red) { onNavigate(Screen.Overdue) }
        NavItem("Upcoming 7 Days", Icons.Outlined.DateRange, screen == Screen.Upcoming, counts.upcoming) { onNavigate(Screen.Upcoming) }
        NavItem("Recurring", Icons.Outlined.Repeat, screen == Screen.Recurring, counts.recurring) { onNavigate(Screen.Recurring) }
        NavItem("All Activities", Icons.Outlined.ViewList, screen == Screen.AllTasks, null) { onNavigate(Screen.AllTasks) }
        NavItem("Completed", Icons.Outlined.TaskAlt, screen == Screen.Completed, null) { onNavigate(Screen.Completed) }

        NavLabel("Organize")
        NavItem("By Person", Icons.Outlined.People, screen == Screen.People || screen is Screen.Person, null) { onNavigate(Screen.People) }
        NavItem("Projects", Icons.Outlined.Folder, screen == Screen.Projects || screen is Screen.Project, null) { onNavigate(Screen.Projects) }
        NavItem("Minutes of Meeting", Icons.Outlined.Groups, screen == Screen.Meetings, null) { onNavigate(Screen.Meetings) }
        NavItem("Reports", Icons.Outlined.Insights, screen == Screen.Reports, null) { onNavigate(Screen.Reports) }

        NavLabel("Personal")
        NavItem("My Journal", Icons.Outlined.Book, screen == Screen.Journal, null) { onNavigate(Screen.Journal) }

        NavLabel("Utilities")
        NavItem("Search", Icons.Outlined.Search, screen == Screen.Search, null) { onNavigate(Screen.Search) }
        NavItem("Settings", Icons.Outlined.Settings, screen == Screen.Settings, null) { onNavigate(Screen.Settings) }
        NavItem("Reminders", Icons.Outlined.NotificationsActive, false, alertCount.takeIf { it > 0 }, KarColors.Amber, onClick = onReminders)
        NavItem("Export Backup", Icons.Outlined.Download, false, null, onClick = onExport)
        NavItem("Import Backup", Icons.Outlined.Upload, false, null, onClick = onImport)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = KarColors.Border)
        Spacer(Modifier.height(8.dp))
        NavItem("Delete All Data", Icons.Outlined.DeleteForever, false, null, danger = true, onClick = onDeleteAll)
        Spacer(Modifier.height(14.dp))
        Text(
            "100% offline · data stays on this device",
            style = MaterialTheme.typography.labelSmall,
            color = KarColors.PastelLabel,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private data class NavCounts(val open: Int, val today: Int, val inbox: Int, val overdue: Int, val upcoming: Int, val recurring: Int)

private fun navCounts(tasks: List<TaskEntity>, today: String): NavCounts {
    val open = tasks.filter { !it.done }
    val byId = tasks.associateBy { it.id }
    val weekEnd = Ist.parse(today)?.plusDays(7)?.toString() ?: today
    return NavCounts(
        open = open.size,
        today = open.count { it.dueDate != null && it.dueDate <= today },
        inbox = open.count { (it.parentId == null || byId[it.parentId] == null) && it.projectId == null },
        overdue = open.count { it.dueDate != null && it.dueDate < today },
        upcoming = open.count { val d = it.dueDate; d != null && d > today && d <= weekEnd },
        recurring = open.count { it.isRecurring },
    )
}

@Composable
private fun NavLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = KarColors.PastelLabel,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int?,
    badgeColor: Color = KarColors.SkyDeep,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val textColor = when {
        danger -> KarColors.Red
        selected -> Color.White
        else -> KarColors.SidebarText
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .pressDepth(interaction, 3f)
            .then(if (selected) Modifier.depthShadow(6.dp, shape) else Modifier)
            .clip(shape)
            .background(if (selected) KarBrush.SelectedNav else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 3.dp, height = 18.dp)
                .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(9.dp))
        Icon(
            icon, null,
            tint = when {
                danger -> KarColors.Red
                selected -> Color.White
                else -> KarColors.SkyDeep.copy(alpha = 0.75f)
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge != null && badge > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) Color.White else badgeColor.copy(alpha = 0.14f),
            ) {
                Text(
                    badge.toString(),
                    color = if (selected) KarColors.SkyDeep else badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// =============================================================================
// Navigation rail (tablet portrait / medium widths)
// =============================================================================

@Composable
internal fun NavRail(
    screen: Screen,
    alertCount: Int,
    onNavigate: (Screen) -> Unit,
    onNewActivity: () -> Unit,
    onReminders: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(92.dp)
            .fillMaxHeight()
            .depthShadow(10.dp, RectangleShape)
            .background(KarBrush.Sidebar)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KarEmblem(32.dp)
        Spacer(Modifier.height(16.dp))
        val addInteraction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressDepth(addInteraction, 8f)
                .size(52.dp)
                .depthShadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(KarBrush.Gold)
                .clickable(interactionSource = addInteraction, indication = LocalIndication.current, onClick = onNewActivity),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "New Activity", tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        RailItem("Scheduler", Icons.Outlined.Schedule, screen == Screen.Schedule) { onNavigate(Screen.Schedule) }
        RailItem("Today", Icons.Outlined.Today, screen == Screen.Today) { onNavigate(Screen.Today) }
        RailItem("Inbox", Icons.Outlined.Inbox, screen == Screen.Inbox) { onNavigate(Screen.Inbox) }
        RailItem("Overdue", Icons.Outlined.ReportProblem, screen == Screen.Overdue) { onNavigate(Screen.Overdue) }
        RailItem("7 Days", Icons.Outlined.DateRange, screen == Screen.Upcoming) { onNavigate(Screen.Upcoming) }
        RailItem("Recurring", Icons.Outlined.Repeat, screen == Screen.Recurring) { onNavigate(Screen.Recurring) }
        RailItem("All", Icons.Outlined.ViewList, screen == Screen.AllTasks) { onNavigate(Screen.AllTasks) }
        RailItem("Done", Icons.Outlined.TaskAlt, screen == Screen.Completed) { onNavigate(Screen.Completed) }
        RailDivider()
        RailItem("People", Icons.Outlined.People, screen == Screen.People || screen is Screen.Person) { onNavigate(Screen.People) }
        RailItem("Projects", Icons.Outlined.Folder, screen == Screen.Projects || screen is Screen.Project) { onNavigate(Screen.Projects) }
        RailItem("MoM", Icons.Outlined.Groups, screen == Screen.Meetings) { onNavigate(Screen.Meetings) }
        RailItem("Reports", Icons.Outlined.Insights, screen == Screen.Reports) { onNavigate(Screen.Reports) }
        RailItem("Journal", Icons.Outlined.Book, screen == Screen.Journal) { onNavigate(Screen.Journal) }
        RailDivider()
        RailItem("Settings", Icons.Outlined.Settings, screen == Screen.Settings) { onNavigate(Screen.Settings) }
        RailItem(if (alertCount > 0) "Alerts ($alertCount)" else "Alerts", Icons.Outlined.NotificationsActive, false, onClick = onReminders)
        RailItem("Export", Icons.Outlined.Download, false, onClick = onExport)
        RailItem("Import", Icons.Outlined.Upload, false, onClick = onImport)
        RailItem("Delete All", Icons.Outlined.DeleteForever, false, danger = true, onClick = onDeleteAll)
    }
}

@Composable
private fun RailDivider() {
    HorizontalDivider(
        color = KarColors.Border,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .padding(vertical = 2.dp)
            .width(80.dp)
            .pressDepth(interaction, 4f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) KarBrush.SelectedNav else androidx.compose.ui.graphics.SolidColor(Color.Transparent)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, label,
                tint = when {
                    danger -> KarColors.Red
                    selected -> Color.White
                    else -> KarColors.SkyDeep.copy(alpha = 0.75f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = when {
                danger -> KarColors.Red
                selected -> KarColors.SkyDeep
                else -> KarColors.SidebarText
            },
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
