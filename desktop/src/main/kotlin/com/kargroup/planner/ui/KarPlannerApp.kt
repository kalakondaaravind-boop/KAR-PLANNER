package com.kargroup.planner.ui

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
import com.kargroup.planner.ui.components.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kargroup.planner.data.ContextTagEntity
import com.kargroup.planner.data.MeetingMinuteEntity
import com.kargroup.planner.data.ProjectEntity
import com.kargroup.planner.data.TaskEntity
import com.kargroup.planner.data.TaskStatus
import com.kargroup.planner.data.PersonEntity
import com.kargroup.planner.reminders.Reminders
import com.kargroup.planner.ui.components.KarEmblem
import com.kargroup.planner.ui.components.KarLogo
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.parseHexColor
import com.kargroup.planner.util.Ist
import com.kargroup.planner.data.LockMode
import com.kargroup.planner.data.TaskHistoryEntity
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import com.kargroup.planner.desktop.DesktopApp
import com.kargroup.planner.desktop.EmailCenterScreen
import com.kargroup.planner.desktop.FileDialogs
import com.kargroup.planner.desktop.OutlookMailer
import kotlinx.coroutines.launch

/** Wrapper so the meeting editor can be opened for "new" (null meeting) too. */
private data class MeetingEditorRequest(val meeting: MeetingMinuteEntity?)


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun KarPlannerApp(vm: MainViewModel, app: DesktopApp, openRemindersSignal: Int = 0) {
    val tasks by vm.tasks.collectAsState()
    val projects by vm.projects.collectAsState()
    val contexts by vm.contexts.collectAsState()
    val meetings by vm.meetings.collectAsState()
    val journal by vm.journal.collectAsState()
    val persons by vm.persons.collectAsState()
    var personDialog by remember { mutableStateOf<PersonEntity?>(null) }
    var showPersonDialog by remember { mutableStateOf(false) }
    var deletePersonRequest by remember { mutableStateOf<PersonEntity?>(null) }
    val screen by vm.screen.collectAsState()
    val drillRootId by vm.drillRootId.collectAsState()
    val showCompleted by vm.showCompleted.collectAsState()
    val scheduleManual by vm.scheduleManual.collectAsState()
    val today by vm.today.collectAsState()
    val reminderVersion by vm.reminderVersion.collectAsState()
    val settings by vm.settings.collectAsState()
    val appUnlocked by vm.appUnlocked.collectAsState()
    val journalUnlocked by vm.journalUnlocked.collectAsState()
    val journalDate by vm.journalDate.collectAsState()
    val lockActive = settings.pinHash != null && settings.lockMode != LockMode.OFF
    val appLocked = lockActive && settings.lockMode == LockMode.APP && !appUnlocked
    val journalLocked = lockActive && settings.lockMode == LockMode.JOURNAL && !journalUnlocked

    val emailQueue by app.emails.store.queue.collectAsState()
    val emailSettings by app.emails.store.settings.collectAsState()
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
    val assigneeNames = remember(tasks, meetings) {
        (tasks.mapNotNull { it.assignee } + meetings.flatMap { m -> m.attendees + m.actionItems.map { it.assignee } })
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }
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
    var completionRequest by remember { mutableStateOf<CompletionRequest?>(null) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    val askCompletion: (TaskEntity) -> Unit = { t ->
        completionRequest = CompletionRequest(t, if (t.isRecurring) 0 else index.openDescendantCount(t.id))
    }
    var deleteTaskRequest by remember { mutableStateOf<TaskEntity?>(null) }
    var deleteProjectRequest by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteContextRequest by remember { mutableStateOf<ContextTagEntity?>(null) }
    var rescheduleRequest by remember { mutableStateOf<TaskEntity?>(null) }
    var showPalette by remember { mutableStateOf(false) }
    var showShortcuts by remember { mutableStateOf(false) }
    val tagDefs by app.tags.tags.collectAsState()
    val tagMap by app.tags.assigned.collectAsState()
    val activityTemplates by app.templates.activities.collectAsState()
    val meetingTemplates by app.templates.meetings.collectAsState()
    val tagsOf: (String) -> Set<String> = { id -> tagMap[id] ?: emptySet() }
    val tagColor: (String) -> String = { n -> tagDefs.firstOrNull { it.name.equals(n, true) }?.color ?: "#64748B" }
    LaunchedEffect(tasks) { if (tasks.isNotEmpty()) app.tags.prune(tasks.map { it.id }.toSet()) }

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

    // ------------------------------------------------------------------ files
    val startExport = {
        FileDialogs.save("Export backup", "KAR-Planner-backup-${Ist.todayStr()}.json")?.let { vm.exportBackup(it) }
        Unit
    }
    val startImport = {
        FileDialogs.open("Import a KAR Planner backup (from the tablet or this PC)", "json")?.let { vm.importBackup(it) }
        Unit
    }

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
                is Screen.Person -> TaskDialogRequest(defaultAssignee = s.name)
                else -> TaskDialogRequest()
            }
        }
    }

    val taskActions = TaskActions(
        onToggle = { task, checked ->
            if (checked) askCompletion(task) else vm.setDone(task, false, true)
        },
        onToggleCollapse = { vm.toggleCollapsed(it) },
        onOpen = { taskDialog = TaskDialogRequest(existing = it) },
        onDrill = { vm.drillInto(it) },
        onAddSubtask = { taskDialog = TaskDialogRequest(parent = it) },
        onDelete = { deleteTaskRequest = it },
        onStatus = { task, status ->
            if (status == TaskStatus.COMPLETED) askCompletion(task) else vm.setStatus(task, status)
        },
        onReschedule = { rescheduleRequest = it },
    )

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
            emailCount = emailQueue.size,
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
                    onSearch = { vm.navigate(Screen.Search) },
                    onCommand = { showPalette = true },
                    onToggleDark = { vm.command(DesktopCommand.ToggleDark) },
                )
            },
            floatingActionButton = {
                if (!wide && screen != Screen.Meetings && screen != Screen.Projects && screen != Screen.Journal && screen != Screen.People &&
                    screen != Screen.Reports && screen != Screen.Search && screen != Screen.Settings && screen != Screen.Emails &&
                    screen != Screen.Dashboard && screen != Screen.Board && screen != Screen.Table && screen != Screen.Templates && screen != Screen.Tracker
                ) {
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
                    Screen.Schedule -> {
                        // Re-plan the time slots every minute so they always start from "now".
                        val plan = remember(index, scheduleManual, minuteTick, settings, meetings) {
                            buildSchedule(index, scheduleManual, settings, meetings)
                        }
                        val autoIds = remember(index, settings, meetings) {
                            buildSchedule(index, manual = false, settings = settings, meetings = meetings).items.map { it.task.id }.distinct()
                        }
                        SchedulerScreen(
                            items = plan.items,
                            blocks = plan.blocks,
                            settings = settings,
                            manual = scheduleManual,
                            projects = projectMap,
                            today = today,
                            wide = wide,
                            header = header,
                            quickAdd = { QuickAddBar(persons = persons, projects = projects, onAdd = { vm.addQuick(it) }) },
                            onManualChange = { vm.setScheduleManual(it) },
                            onMove = { from, to -> vm.moveInSchedule(plan.items.map { it.task.id }, from, to) },
                            onResetManual = { vm.resetManualToAuto(autoIds) },
                            onToggle = { task, checked -> taskActions.onToggle(task, checked) },
                            onStatus = { task, status -> taskActions.onStatus(task, status) },
                            onOpen = { taskDialog = TaskDialogRequest(existing = it) },
                            onAddActivity = openAddActivity,
                            onPin = { task, start -> vm.setPlannedStart(task, start) },
                            onOpenMeeting = { id -> meetings.firstOrNull { it.id == id }?.let { meetingEditor = MeetingEditorRequest(it) } },
                            onReschedule = { rescheduleRequest = it },
                            onToggleHoliday = { vm.toggleHoliday(it) },
                        )
                    }
                    Screen.Reports -> ReportsScreen(
                        tasks = tasks,
                        persons = persons,
                        projects = projects,
                        wide = wide,
                        onExportPdf = { p -> FileDialogs.save("Save report (opens in your browser to print or save as PDF)", "KAR-Planner-report-${Ist.todayStr()}.html")?.let { vm.exportReportHtml(it, p) } },
                        onExportCsv = { p -> FileDialogs.save("Save report for Excel", "KAR-Planner-report-${Ist.todayStr()}.csv")?.let { vm.exportReportCsv(it, p) } },
                        onOpenTask = { taskDialog = TaskDialogRequest(existing = it) },
                    )
                    Screen.Search -> SearchScreen(
                        tasks = tasks,
                        meetings = meetings,
                        journal = journal,
                        projects = projectMap,
                        journalLocked = journalLocked,
                        wide = wide,
                        onOpenTask = { taskDialog = TaskDialogRequest(existing = it) },
                        onOpenMeeting = { meetingEditor = MeetingEditorRequest(it) },
                        onOpenJournal = { vm.openJournalOn(it) },
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        wide = wide,
                        onUpdate = { vm.updateSettings(it) },
                        onBackupNow = { vm.runBackupNow() },
                        onSetPin = { vm.setPin(it) },
                        email = emailSettings,
                        onEmailUpdate = { app.emails.store.updateSettings(it) },
                        onTestEmail = {
                            app.emails.prepareTest()
                            vm.navigate(Screen.Emails)
                            vm.toast("Test e-mail ready · press Send")
                        },
                        onOpenDataFolder = { vm.openDataFolder() },
                        onExport = startExport,
                        onImport = startImport,
                        textSize = com.kargroup.planner.desktop.DisplayPrefs.textSize,
                        onTextSize = { com.kargroup.planner.desktop.DisplayPrefs.set(app, it) },
                        onToggleDark = { vm.command(DesktopCommand.ToggleDark) },
                        onStartWithWindows = { on ->
                            val ok = OutlookMailer.setStartWithWindows(on)
                            if (ok || !on) app.emails.store.updateSettings { it.copy(startWithWindows = on) }
                            vm.toast(if (ok) (if (on) "KAR Planner will start with Windows" else "Removed from Windows start-up") else "Could not change the Windows start-up setting")
                        },
                    )
                    Screen.Dashboard -> DashboardScreen(
                        tasks = tasks,
                        meetings = meetings,
                        persons = persons,
                        projects = projects,
                        settings = settings,
                        manual = scheduleManual,
                        myName = emailSettings.myName,
                        emailsWaiting = emailQueue.size,
                        today = today,
                        onNewActivity = openAddActivity,
                        onNewMeeting = { meetingEditor = MeetingEditorRequest(null) },
                        onOpenEmails = { vm.navigate(Screen.Emails) },
                        onOpenTask = { taskDialog = TaskDialogRequest(existing = it) },
                        onOpenMeeting = { meetingEditor = MeetingEditorRequest(it) },
                        onNavigate = { vm.navigate(it) },
                        onReschedule = { rescheduleRequest = it },
                    )
                    Screen.Board -> BoardScreen(
                        tasks = tasks,
                        persons = persons,
                        projects = projects,
                        tags = tagDefs,
                        tagsOf = tagsOf,
                        tagColor = tagColor,
                        today = today,
                        onOpen = { taskDialog = TaskDialogRequest(existing = it) },
                        onMove = { task, status ->
                            if (status == TaskStatus.COMPLETED) askCompletion(task) else vm.setStatus(task, status)
                        },
                        onAdd = openAddActivity,
                    )
                    Screen.Table -> TableScreen(
                        tasks = tasks,
                        persons = persons,
                        projects = projects,
                        tags = tagDefs,
                        tagsOf = tagsOf,
                        tagColor = tagColor,
                        today = today,
                        onOpen = { taskDialog = TaskDialogRequest(existing = it) },
                        bulk = BulkActions(
                            assign = { ids, p -> vm.bulkAssign(ids, p) },
                            status = { ids, st -> vm.bulkStatus(ids, st) },
                            priority = { ids, p -> vm.bulkPriority(ids, p) },
                            currentStatus = { ids, t -> vm.bulkCurrentStatus(ids, t) },
                            reschedule = { ids, d, r -> vm.bulkReschedule(ids, d, r) },
                            complete = { ids, r -> vm.bulkComplete(ids, r) },
                            delete = { ids -> vm.bulkDelete(ids) },
                            addTag = { ids, t -> app.tags.add(ids, t); vm.toast("Tag $t added to ${ids.size}") },
                            removeTag = { ids, t -> app.tags.remove(ids, t); vm.toast("Tag $t removed") },
                            exportCsv = { list ->
                                FileDialogs.save("Export this view for Excel", "KAR-Planner-activities-${Ist.todayStr()}.csv")?.let { f ->
                                    runCatching { f.writeText(com.kargroup.planner.desktop.TableCsv.csv(list, projects, tagsOf), Charsets.UTF_8) }
                                        .onSuccess { vm.toast("Exported ${list.size} activities") }
                                        .onFailure { vm.toast("Export failed: ${it.message}") }
                                }
                            },
                        ),
                    )
                    Screen.Tracker -> SpecialProjectsScreen(
                        store = app.plans, persons = persons, settings = settings, onMessage = { vm.toast(it) },
                    )
                    Screen.Templates -> TemplatesScreen(
                        activities = activityTemplates,
                        meetings = meetingTemplates,
                        tags = tagDefs,
                        tagUsage = { n -> tagMap.values.count { set -> set.any { it.equals(n, true) } } },
                        persons = persons,
                        onUseActivity = { vm.command(DesktopCommand.UseTemplate(it)) },
                        onUseMeeting = { vm.command(DesktopCommand.UseMeetingTemplate(it)) },
                        onSaveActivity = { app.templates.saveActivity(it); vm.toast("Template saved") },
                        onDeleteActivity = { app.templates.deleteActivity(it.id); vm.toast("Template deleted") },
                        onSaveMeeting = { app.templates.saveMeeting(it); vm.toast("Template saved") },
                        onDeleteMeeting = { app.templates.deleteMeeting(it.id); vm.toast("Template deleted") },
                        onSaveTag = { t, old -> app.tags.saveTag(t, old) },
                        onDeleteTag = { app.tags.deleteTag(it.name) },
                    )
                    Screen.Emails -> EmailCenterScreen(
                        center = app.emails,
                        meetings = meetings,
                        wide = wide,
                        onMessage = { vm.toast(it) },
                        onOpenSettings = { vm.navigate(Screen.Settings) },
                    )
                    Screen.People -> PeopleScreen(
                        tasks = tasks,
                        persons = persons,
                        onAdd = { personDialog = null; showPersonDialog = true },
                        onEdit = { personDialog = it; showPersonDialog = true },
                        onDelete = { deletePersonRequest = it },
                        today = today,
                        wide = wide,
                        header = header,
                        onOpen = { vm.navigate(Screen.Person(it)) },
                    )
                    Screen.Journal -> if (journalLocked) {
                        LockScreen(settings, "My Journal") { vm.unlockJournal() }
                    } else {
                        JournalScreen(
                            entries = journal,
                            today = today,
                            wide = wide,
                            onSave = { date, mood, title, content, quiet -> vm.saveJournal(date, mood, title, content, quiet) },
                            onDelete = { vm.deleteJournal(it) },
                            initialDate = journalDate,
                        )
                    }
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
                            contexts = emptyMap(),
                            today = today,
                            wide = wide,
                            actions = taskActions,
                            onShowCompletedChange = { vm.setShowCompleted(it) },
                            onAddActivity = openAddActivity,
                            header = header,
                            sheetExtras = {
                                if (s is Screen.Person) {
                                    val person = persons.firstOrNull { sameAssignee(it.name, s.name) }
                                    androidx.compose.foundation.layout.FlowRow(
                                        Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { vm.navigate(Screen.People) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                        ) { Text("← All people") }
                                        if (s.name != null) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = {
                                                    val n = app.emails.preparePersonUpdate(s.name)
                                                    if (n > 0) {
                                                        vm.navigate(Screen.Emails)
                                                        vm.toast("Update e-mail for ${s.name} ready to review")
                                                    } else vm.toast("No open activities or no e-mail address for ${s.name}")
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                            ) {
                                                Icon(Icons.Outlined.Email, null, Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("E-mail update via Outlook")
                                            }
                                        }
                                    }
                                }
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
                emailCount = emailQueue.size,
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
        val historyFlow = remember(req.existing?.id) {
            req.existing?.let { vm.historyFor(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
        }
        val history by historyFlow.collectAsState(initial = emptyList<TaskHistoryEntity>())
        AddTaskDialog(
            request = req,
            projects = projects,
            people = persons,
            onAddPerson = { vm.savePerson(it, null) },
            existingSubtasks = req.existing?.let { index.children(it.id).sortedWith(childOrder) } ?: emptyList(),
            history = history,
            onDismiss = { taskDialog = null },
            onSave = { task, completed, subs, removed, reason ->
                // Completing from the pop-up also asks for remarks first.
                val needsRemarks = completed && !task.done
                vm.saveTask(
                    task,
                    isNew = req.existing == null,
                    wantCompleted = if (needsRemarks) false else completed,
                    subtasks = subs,
                    removedSubtaskIds = removed,
                    rescheduleReason = reason,
                )
                if (needsRemarks) completionRequest = CompletionRequest(task, if (task.isRecurring) 0 else maxOf(index.openDescendantCount(task.id), subs.count { !it.done }))
                taskDialog = null
            },
        )
    }

    if (showPersonDialog) {
        val editing = personDialog
        PersonDialog(
            existing = editing,
            takenNames = persons.map { it.name },
            onDismiss = { showPersonDialog = false },
            onSave = { vm.savePerson(it, editing?.name); showPersonDialog = false },
        )
    }

    deletePersonRequest?.let { p ->
        val count = tasks.count { !it.done && sameAssignee(it.assignee, p.name) }
        ConfirmDialog(
            title = "Remove ${p.name}?",
            message = if (count > 0) "${p.name} has $count open activit${if (count == 1) "y" else "ies"}. They will become unassigned." else "${p.name} will be removed from the people list.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.deletePerson(p); deletePersonRequest = null },
            onDismiss = { deletePersonRequest = null },
        )
    }

    if (showProjectDialog) {
        ProjectDialog(
            existing = projectDialog,
            onDismiss = { showProjectDialog = false },
            onSave = { vm.saveProject(it); showProjectDialog = false },
        )
    }


    meetingEditor?.let { req ->
        // createdAt == -1 marks a "new meeting with a preset project" template.
        val isTemplate = req.meeting?.createdAt == -1L
        MeetingEditorDialog(
            existing = if (isTemplate) null else req.meeting,
            projects = projects,
            people = persons,
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
            onReschedule = { rescheduleRequest = it },
        )
    }

    // ------------------------------------------------- shortcuts & command bar
    LaunchedEffect(Unit) {
        vm.commands.collect { c ->
            when (c) {
                DesktopCommand.NewActivity -> openAddActivity()
                DesktopCommand.NewMeeting -> meetingEditor = MeetingEditorRequest(null)
                DesktopCommand.OpenPalette -> showPalette = true
                DesktopCommand.Shortcuts -> showShortcuts = true
                DesktopCommand.Reminders -> showReminders = true
                DesktopCommand.Export -> startExport()
                DesktopCommand.Import -> startImport()
                DesktopCommand.ToggleDark -> {
                    com.kargroup.planner.ui.theme.KarPalette.dark = !com.kargroup.planner.ui.theme.KarPalette.dark
                    app.uiPrefs.edit().putBoolean("dark", com.kargroup.planner.ui.theme.KarPalette.dark).apply()
                }
                DesktopCommand.PrepareEmails -> {
                    val n = app.emails.prepareDigest() + app.emails.prepareReminders()
                    vm.navigate(Screen.Emails)
                    vm.toast("$n e-mail(s) ready to review")
                }
                DesktopCommand.TextBigger -> { com.kargroup.planner.desktop.DisplayPrefs.bigger(app); vm.toast("Text size ${Math.round(com.kargroup.planner.desktop.DisplayPrefs.textSize * 100)}%") }
                DesktopCommand.TextSmaller -> { com.kargroup.planner.desktop.DisplayPrefs.smaller(app); vm.toast("Text size ${Math.round(com.kargroup.planner.desktop.DisplayPrefs.textSize * 100)}%") }
                DesktopCommand.TextReset -> { com.kargroup.planner.desktop.DisplayPrefs.reset(app); vm.toast("Text size reset") }
                is DesktopCommand.Go -> vm.navigate(c.screen)
                is DesktopCommand.OpenTask -> taskDialog = TaskDialogRequest(existing = c.task)
                is DesktopCommand.OpenMeeting -> meetingEditor = MeetingEditorRequest(c.meeting)
                is DesktopCommand.UseTemplate -> {
                    val created = vm.createFromTemplate(c.template)
                    taskDialog = TaskDialogRequest(existing = created)
                }
                is DesktopCommand.UseMeetingTemplate -> meetingEditor = MeetingEditorRequest(vm.meetingFromTemplate(c.template))
            }
        }
    }

    if (showPalette) {
        CommandBar(
            tasks = tasks,
            meetings = meetings,
            persons = persons,
            templates = activityTemplates,
            meetingTemplates = meetingTemplates,
            onCommand = { vm.command(it) },
            onDismiss = { showPalette = false },
        )
    }
    if (showShortcuts) ShortcutsDialog { showShortcuts = false }

    if (showDeleteAll) {
        DeleteAllDialog(
            loadCounts = { vm.counts() },
            onBackupFirst = startExport,
            onConfirm = { vm.deleteAllData(); showDeleteAll = false },
            onDismiss = { showDeleteAll = false },
        )
    }

    completionRequest?.let { req ->
        CompletionRemarksDialog(
            request = req,
            onDismiss = { completionRequest = null },
            onConfirm = { remarks, includeSubtasks ->
                vm.completeWithRemarks(req.task, remarks, includeSubtasks)
                completionRequest = null
                celebration = newCelebration(req.task, remarks)
            },
        )
    }

    celebration?.let { c ->
        CelebrationOverlay(c) { celebration = null }
    }

    rescheduleRequest?.let { t ->
        RescheduleDialog(
            task = t,
            onDismiss = { rescheduleRequest = null },
            onConfirm = { date, time, reason ->
                vm.reschedule(t, date, time, reason)
                rescheduleRequest = null
            },
        )
    }

    // Whole-app privacy lock sits above everything else.
    if (appLocked) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            LockScreen(settings, "KAR Planner") { vm.unlockApp() }
        }
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

}

// =============================================================================
// Titles
// =============================================================================

fun screenTitle(screen: Screen, projects: Map<String, ProjectEntity>, contexts: Map<String, ContextTagEntity>): String = when (screen) {
    Screen.Schedule -> "Scheduler"
    Screen.Today -> "Today"
    Screen.Inbox -> "Inbox"
    Screen.Overdue -> "Overdue"
    Screen.Upcoming -> "Upcoming 7 Days"
    Screen.Recurring -> "Recurring"
    Screen.AllTasks -> "All Activities"
    Screen.Completed -> "Completed"
    Screen.Projects -> "Projects"
    Screen.Meetings -> "Minutes of Meeting"
    Screen.Journal -> "My Journal"
    is Screen.Project -> projects[screen.id]?.name ?: "Project"
    Screen.People -> "By Person"
    Screen.Reports -> "Reports"
    Screen.Search -> "Search"
    Screen.Settings -> "Settings"
    Screen.Emails -> "Outlook E-mails"
    Screen.Dashboard -> "Dashboard"
    Screen.Board -> "Board"
    Screen.Table -> "Table"
    Screen.Templates -> "Templates & Tags"
    Screen.Tracker -> "Special Projects"
    is Screen.Person -> screen.name ?: "Unassigned"
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
        Screen.Inbox -> "$countText · not yet assigned to a project" to KarColors.Navy2
        Screen.Overdue -> "$countText · past their due date" to KarColors.Red
        Screen.Upcoming -> "$countText · due in the next 7 days" to KarColors.Navy2
        Screen.Recurring -> "$countText · repeating on a cadence" to KarColors.Navy2
        Screen.AllTasks -> countText to KarColors.Navy
        Screen.Completed -> "$countText · most recent first" to KarColors.Green
        is Screen.Person -> "$countText · assigned ${if (screen.name == null) "to nobody yet" else "to ${screen.name}"}" to KarColors.SkyDeep
        is Screen.Project -> {
            val p = projects[screen.id]
            ((p?.description?.takeIf { it.isNotBlank() }?.let { "$it · " } ?: "") + countText) to parseHexColor(p?.color)
        }
        else -> null to null
    }
}

