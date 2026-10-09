package com.kargroup.planner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import com.kargroup.planner.ui.components.DepthCard
import com.kargroup.planner.ui.components.GoldButton
import com.kargroup.planner.ui.components.IconTile
import com.kargroup.planner.ui.components.SoftIconTile
import com.kargroup.planner.ui.components.KarBrush
import com.kargroup.planner.ui.components.depthShadow
import com.kargroup.planner.ui.components.pressDepth
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.reminders.Reminders
import com.kargroup.planner.ui.components.KarEmblem
import com.kargroup.planner.ui.components.KarLogo
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Wrapper so the meeting editor can be opened for "new" (null meeting) too. */
private data class MeetingEditorRequest(val meeting: MeetingMinuteEntity?)

private data class ToggleRequest(val task: TaskEntity, val openSubtasks: Int)

@Composable
fun KarPlannerApp(openRemindersSignal: Int = 0, vm: MainViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val contexts by vm.contexts.collectAsStateWithLifecycle()
    val meetings by vm.meetings.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val drillRootId by vm.drillRootId.collectAsStateWithLifecycle()
    val showCompleted by vm.showCompleted.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val reminderVersion by vm.reminderVersion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    // Adaptive breakpoints, re-evaluated live on every rotation / window resize:
    //   compact  (< 600dp)  phone            -> drawer + floating Add button
    //   medium   (600-999)  tablet portrait  -> slim navigation rail
    //   expanded (>= 1000)  tablet landscape -> full sidebar
    val widthDp = LocalConfiguration.current.screenWidthDp
    val wide = widthDp >= 600
    val expanded = widthDp >= 1000
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Hourly tick so snoozes expire in the UI without user interaction.
    var minuteTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            minuteTick++
        }
    }

    val index = remember(tasks) { TaskIndex(tasks) }
    val projectMap = remember(projects) { projects.associateBy { it.id } }
    val contextMap = remember(contexts) { contexts.associateBy { it.id } }
    val alerts = remember(tasks, today, reminderVersion, minuteTick) {
        Reminders.alerts(tasks, vm.reminderPrefs.suppressedIds(), today)
    }

    // ---------------------------------------------------------------- dialog state
    var taskDialog by remember { mutableStateOf<TaskDialogRequest?>(null) }
    var projectDialog by remember { mutableStateOf<ProjectEntity?>(null) }
    var showProjectDialog by remember { mutableStateOf(false) }
    var contextDialog by remember { mutableStateOf<ContextTagEntity?>(null) }
    var showContextDialog by remember { mutableStateOf(false) }
    var meetingEditor by remember { mutableStateOf<MeetingEditorRequest?>(null) }
    var showReminders by remember { mutableStateOf(false) }
    var bannerHidden by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var toggleRequest by remember { mutableStateOf<ToggleRequest?>(null) }
    var deleteTaskRequest by remember { mutableStateOf<TaskEntity?>(null) }
    var deleteProjectRequest by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteContextRequest by remember { mutableStateOf<ContextTagEntity?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Auto-open the reminder popup once per app session when something is due.
    LaunchedEffect(alerts.isNotEmpty()) {
        if (alerts.isNotEmpty() && !vm.reminderPopupShown) {
            delay(600)
            vm.reminderPopupShown = true
            showReminders = true
        }
    }
    LaunchedEffect(openRemindersSignal) {
        if (openRemindersSignal > 0) {
            vm.reminderPopupShown = true
            showReminders = true
        }
    }

    // ------------------------------------------------------- permissions & files
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val startExport = { exportLauncher.launch("KAR-Planner-backup-${Ist.todayStr()}.json") }
    val startImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }

    // --------------------------------------------------------------- add activity
    val openAddActivity: () -> Unit = {
        val drillRoot = drillRootId?.let { index.byId[it] }
        taskDialog = when {
            drillRoot != null -> TaskDialogRequest(parent = drillRoot)
            else -> when (val s = screen) {
                Screen.Today, Screen.Overdue -> TaskDialogRequest(defaultDueDate = today)
                Screen.Upcoming -> TaskDialogRequest(defaultDueDate = Ist.today().plusDays(1).toString())
                Screen.Recurring -> TaskDialogRequest(defaultRecurring = true, defaultDueDate = today)
                is Screen.Project -> TaskDialogRequest(defaultProjectId = s.id)
                is Screen.Context -> TaskDialogRequest(defaultContextId = s.id)
                else -> TaskDialogRequest()
            }
        }
    }

    val taskActions = TaskActions(
        onToggle = { task, checked ->
            if (checked && !task.isRecurring) {
                val open = index.openDescendantCount(task.id)
                if (open > 0) toggleRequest = ToggleRequest(task, open) else vm.setDone(task, true, true)
            } else {
                vm.setDone(task, checked, true)
            }
        },
        onToggleCollapse = { vm.toggleCollapsed(it) },
        onOpen = { taskDialog = TaskDialogRequest(existing = it) },
        onDrill = { vm.drillInto(it) },
        onAddSubtask = { taskDialog = TaskDialogRequest(parent = it) },
        onDelete = { deleteTaskRequest = it },
    )

    BackHandler(enabled = drillRootId != null) {
        vm.drillInto(drillRootId?.let { index.byId[it]?.parentId })
    }

    // ------------------------------------------------------------------- layout
    val sidebar: @Composable (onAfterClick: () -> Unit) -> Unit = { after ->
        Sidebar(
            screen = screen,
            tasks = tasks,
            today = today,
            alertCount = alerts.size,
            onNavigate = { vm.navigate(it); after() },
            onNewActivity = { after(); openAddActivity() },
            onReminders = { after(); showReminders = true },
            onExport = { after(); startExport() },
            onImport = { after(); startImport() },
            onDeleteAll = { after(); showDeleteAll = true },
        )
    }

    val mainPane: @Composable (onMenu: (() -> Unit)?) -> Unit = { onMenu ->
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopBar(
                    title = screenTitle(screen, projectMap, contextMap),
                    wide = wide,
                    onMenu = onMenu,
                    onAdd = openAddActivity,
                )
            },
            floatingActionButton = {
                if (!wide && screen != Screen.Meetings && screen != Screen.Projects && screen != Screen.Contexts) {
                    ExtendedFloatingActionButton(
                        onClick = openAddActivity,
                        containerColor = KarColors.SkyDeep,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text("Add Activity", fontWeight = FontWeight.Bold) },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val header: @Composable () -> Unit = {
                    Column {
                        StatsRow(
                            tasks = tasks,
                            today = today,
                            onNavigate = { vm.navigate(it) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
                when (val s = screen) {
                    Screen.Projects -> ProjectsScreen(
                        projects = projects,
                        tasks = tasks,
                        wide = wide,
                        header = header,
                        onOpen = { vm.navigate(Screen.Project(it.id)) },
                        onAdd = { projectDialog = null; showProjectDialog = true },
                        onEdit = { projectDialog = it; showProjectDialog = true },
                        onDelete = { deleteProjectRequest = it },
                    )
                    Screen.Contexts -> ContextsScreen(
                        contexts = contexts,
                        tasks = tasks,
                        wide = wide,
                        header = header,
                        onOpen = { vm.navigate(Screen.Context(it.id)) },
                        onAdd = { contextDialog = null; showContextDialog = true },
                        onEdit = { contextDialog = it; showContextDialog = true },
                        onDelete = { deleteContextRequest = it },
                    )
                    Screen.Meetings -> MeetingsScreen(
                        meetings = meetings,
                        projects = projectMap,
                        wide = wide,
                        header = header,
                        onOpen = { meetingEditor = MeetingEditorRequest(it) },
                        onAdd = { meetingEditor = MeetingEditorRequest(null) },
                    )
                    else -> {
                        val sections = remember(index, s, today, showCompleted, drillRootId) {
                            buildSections(s, index, today, showCompleted, drillRootId)
                        }
                        val (subtitle, accent) = screenSubtitle(s, sections, projectMap, contextMap)
                        TaskListScreen(
                            title = screenTitle(s, projectMap, contextMap),
                            subtitle = subtitle,
                            accent = accent,
                            sections = sections,
                            index = index,
                            drillRootId = drillRootId,
                            showCompleted = showCompleted,
                            showCompletedToggle = s != Screen.Completed,
                            projects = projectMap,
                            contexts = contextMap,
                            today = today,
                            wide = wide,
                            actions = taskActions,
                            onShowCompletedChange = { vm.setShowCompleted(it) },
                            onAddActivity = openAddActivity,
                            header = header,
                            sheetExtras = {
                                if (s is Screen.Project) {
                                    val project = projectMap[s.id]
                                    if (project != null) {
                                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = { projectDialog = project; showProjectDialog = true },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                            ) {
                                                Text("Edit project")
                                            }
                                            androidx.compose.material3.OutlinedButton(
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                                onClick = {
                                                meetingEditor = MeetingEditorRequest(
                                                    MeetingMinuteEntity(title = "", meetingDate = today, meetingTime = "10:00", projectId = project.id)
                                                        .copy(createdAt = -1L)
                                                )
                                            }) { Text("New MoM for project") }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                if (!bannerHidden && alerts.isNotEmpty() && !showReminders) {
                    ReminderBanner(
                        alerts = alerts,
                        today = today,
                        onReview = { showReminders = true },
                        onSnooze = { vm.snooze(alerts.map { it.id }) },
                        onDismiss = { bannerHidden = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = if (wide) 16.dp else 84.dp)
                            .widthIn(max = 640.dp),
                    )
                }
            }
        }
    }

    if (expanded) {
        Row(Modifier.fillMaxSize().background(KarBrush.Backdrop)) {
            Box(
                Modifier
                    .width(276.dp)
                    .fillMaxHeight()
                    .zIndex(1f)
                    .depthShadow(12.dp, RectangleShape)
                    .background(KarBrush.Sidebar)
            ) { sidebar {} }
            Box(Modifier.weight(1f)) { mainPane(null) }
        }
    } else if (wide) {
        Row(Modifier.fillMaxSize().background(KarBrush.Backdrop)) {
            NavRail(
                screen = screen,
                alertCount = alerts.size,
                onNavigate = { vm.navigate(it) },
                onNewActivity = openAddActivity,
                onReminders = { showReminders = true },
                onExport = startExport,
                onImport = startImport,
                onDeleteAll = { showDeleteAll = true },
                modifier = Modifier.zIndex(1f),
            )
            Box(Modifier.weight(1f)) { mainPane(null) }
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = KarColors.SkyWash,
                    drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                    modifier = Modifier.width(300.dp),
                ) {
                    Box(Modifier.fillMaxHeight().background(KarBrush.Sidebar)) {
                        sidebar { scope.launch { drawerState.close() } }
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(KarBrush.Backdrop)) {
                mainPane { scope.launch { drawerState.open() } }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs
    taskDialog?.let { req ->
        AddTaskDialog(
            request = req,
            projects = projects,
            contexts = contexts,
            onDismiss = { taskDialog = null },
            onSave = { task ->
                vm.saveTask(task, isNew = req.existing == null)
                taskDialog = null
            },
        )
    }

    if (showProjectDialog) {
        ProjectDialog(
            existing = projectDialog,
            onDismiss = { showProjectDialog = false },
            onSave = { vm.saveProject(it); showProjectDialog = false },
        )
    }

    if (showContextDialog) {
        ContextDialog(
            existing = contextDialog,
            onDismiss = { showContextDialog = false },
            onSave = { vm.saveContext(it); showContextDialog = false },
        )
    }

    meetingEditor?.let { req ->
        // createdAt == -1 marks a "new meeting with a preset project" template.
        val isTemplate = req.meeting?.createdAt == -1L
        MeetingEditorDialog(
            existing = if (isTemplate) null else req.meeting,
            projects = projects,
            defaultProjectId = if (isTemplate) req.meeting?.projectId else null,
            onDismiss = { meetingEditor = null },
            onSave = { vm.saveMeeting(it); meetingEditor = null },
            onDelete = { vm.deleteMeeting(it); meetingEditor = null },
        )
    }

    if (showReminders) {
        RemindersDialog(
            alerts = alerts,
            today = today,
            onSnooze = { vm.snooze(it) },
            onDismissReminders = { vm.dismiss(it) },
            onOpenTask = { showReminders = false; taskDialog = TaskDialogRequest(existing = it) },
            onClose = { showReminders = false },
        )
    }

    if (showDeleteAll) {
        DeleteAllDialog(
            loadCounts = { vm.counts() },
            onBackupFirst = startExport,
            onConfirm = { vm.deleteAllData(); showDeleteAll = false },
            onDismiss = { showDeleteAll = false },
        )
    }

    toggleRequest?.let { req ->
        ConfirmDialog(
            title = "Complete subtasks too?",
            message = "\"${req.task.text}\" has ${req.openSubtasks} open subtask(s). Mark them done as well?",
            confirmLabel = "Complete all",
            destructive = false,
            onConfirm = { vm.setDone(req.task, true, true); toggleRequest = null },
            onDismiss = { toggleRequest = null },
            secondaryLabel = "Only this one",
            onSecondary = { vm.setDone(req.task, true, false); toggleRequest = null },
        )
    }

    deleteTaskRequest?.let { t ->
        val subCount = remember(t, tasks) { com.kargroup.planner.data.KarRepository.descendantsOf(tasks, t.id).size }
        ConfirmDialog(
            title = "Delete activity?",
            message = if (subCount > 0) "\"${t.text}\" and its $subCount subtask(s) will be deleted." else "\"${t.text}\" will be deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteTask(t); deleteTaskRequest = null },
            onDismiss = { deleteTaskRequest = null },
        )
    }

    deleteProjectRequest?.let { p ->
        ConfirmDialog(
            title = "Delete project?",
            message = "\"${p.name}\" will be removed. Its activities and meetings are kept but unassigned.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteProject(p); deleteProjectRequest = null },
            onDismiss = { deleteProjectRequest = null },
        )
    }

    deleteContextRequest?.let { c ->
        ConfirmDialog(
            title = "Delete context?",
            message = "\"${c.name}\" will be removed from all activities.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.deleteContext(c); deleteContextRequest = null },
            onDismiss = { deleteContextRequest = null },
        )
    }
}

// =============================================================================
// Titles
// =============================================================================

fun screenTitle(screen: Screen, projects: Map<String, ProjectEntity>, contexts: Map<String, ContextTagEntity>): String = when (screen) {
    Screen.Today -> "Today"
    Screen.Inbox -> "Inbox"
    Screen.Overdue -> "Overdue"
    Screen.Upcoming -> "Upcoming 7 Days"
    Screen.Recurring -> "Recurring"
    Screen.AllTasks -> "All Activities"
    Screen.Completed -> "Completed"
    Screen.Projects -> "Projects"
    Screen.Contexts -> "Contexts"
    Screen.Meetings -> "Minutes of Meeting"
    is Screen.Project -> projects[screen.id]?.name ?: "Project"
    is Screen.Context -> contexts[screen.id]?.name ?: "Context"
}

private fun screenSubtitle(
    screen: Screen,
    sections: List<TaskSection>,
    projects: Map<String, ProjectEntity>,
    contexts: Map<String, ContextTagEntity>,
): Pair<String?, Color?> {
    val topCount = sections.sumOf { s -> s.rows.count { it.depth == 0 } }
    val countText = "$topCount activit${if (topCount == 1) "y" else "ies"}"
    return when (screen) {
        Screen.Today -> "$countText · due today or overdue (IST)" to KarColors.Gold
        Screen.Inbox -> "$countText · not yet assigned to a project or context" to KarColors.Navy2
        Screen.Overdue -> "$countText · past their due date" to KarColors.Red
        Screen.Upcoming -> "$countText · due in the next 7 days" to KarColors.Navy2
        Screen.Recurring -> "$countText · repeating on a cadence" to KarColors.Navy2
        Screen.AllTasks -> countText to KarColors.Navy
        Screen.Completed -> "$countText · most recent first" to KarColors.Green
        is Screen.Project -> {
            val p = projects[screen.id]
            ((p?.description?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") + countText) to parseHexColor(p?.color)
        }
        is Screen.Context -> countText to parseHexColor(contexts[screen.id]?.color)
        else -> null to null
    }
}

