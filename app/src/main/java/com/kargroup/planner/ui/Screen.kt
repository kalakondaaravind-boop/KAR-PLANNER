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
    /** Review page for one person; [name] is null for "Unassigned". */
    data class Person(val name: String?) : Screen
    data class Project(val id: String) : Screen
}
