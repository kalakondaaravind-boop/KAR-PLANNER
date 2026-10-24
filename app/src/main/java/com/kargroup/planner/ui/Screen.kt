package com.kargroup.planner.ui

/** Every page of the app (shared by the tablet and the desktop app). */
sealed interface Screen {
    data object Schedule : Screen
    data object Today : Screen
    data object Inbox : Screen
    data object Overdue : Screen
    data object Upcoming : Screen
    data object Recurring : Screen
    data object AllTasks : Screen
    data object Completed : Screen
    data object Projects : Screen
    data object Meetings : Screen
    data object Journal : Screen
    data object People : Screen
    data object Reports : Screen
    data object Search : Screen
    data object Settings : Screen
    /** Desktop only: Outlook e-mails waiting for review. */
    data object Emails : Screen
    /** Desktop only: home dashboard, Kanban board, table view and templates. */
    data object Dashboard : Screen
    data object Board : Screen
    data object Table : Screen
    data object Templates : Screen
    /** Desktop only: Special Projects planner (portfolio, Gantt, task sheet, workload). */
    data object Tracker : Screen
    /** Desktop only: commercials tracker (table with custom columns). */
    data object Commercials : Screen
    /** Review page for one person; [name] is null for "Unassigned". */
    data class Person(val name: String?) : Screen
    data class Project(val id: String) : Screen
}
