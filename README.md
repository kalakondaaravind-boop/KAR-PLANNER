# KAR Planner — KAR Group

Native Android planner for tablets and phones. Built with Kotlin, Jetpack Compose and Material 3. Everything is stored on the device in a Room (SQLite) database, so there's no login, no server and no internet requirement.

## Install on your tablet (no computer setup needed)

1. Create a free account at **github.com** and click **New repository**. Name it `kar-planner`, set it to **Private** if you like, and click **Create**.
2. On the new repository page, click **uploading an existing file** and drag in **everything inside this folder**. That includes the hidden `.github` folder: on a Mac press `Cmd + Shift + .` to show hidden files. Then click **Commit changes**.
3. Open the **Actions** tab. A build named *Build KAR Planner APK* starts automatically and takes about 5–8 minutes.
4. When it shows a green tick, open the repository's **Releases** section on your tablet and tap **KAR-Planner.apk**.
5. Tap the downloaded file. If Android asks, allow **Install unknown apps** for your browser, then tap **Install**.

To update the app later, upload the changed files again. A new release with a new APK is built automatically, and installing it keeps your data.

## Alternative: Android Studio

Open this folder in Android Studio (Koala or newer). Then either connect the tablet over USB with *Developer options → USB debugging* turned on and press **Run ▶**, or use *Build → Build APK(s)*.

## Features

| Area | What it does |
|---|---|
| Adaptive layout | Tablets (≥600dp) get a permanent sidebar and a dual-pane layout. Phones get a hamburger drawer and an *Add Activity* button. |
| IST clock | Live `hh:mm:ss a` IST clock in the header. Every due, overdue and "next 7 days" calculation uses Asia/Kolkata time. |
| Task tree | Unlimited subtasks, expand/collapse, drilldown with breadcrumbs, and an optional "complete subtasks too" prompt. |
| Recurring | Daily, weekly (pick weekdays), biweekly and monthly, with a repeat interval. Checking one off archives it and creates the next one, subtasks included. |
| Add Activity pop-up | Title, notes, project, GTD context, priority pills, Today/Tomorrow/None/date picker, and recurrence options. |
| Minutes of Meeting | Attendees, agenda, notes, decisions and action items. Each action item becomes a **high-priority** planner task, and completion syncs both ways. |
| Reminders | Checks hourly in the background and sends a notification with *Snooze 1 Hour* / *Dismiss*. Inside the app there's a floating banner and a reminder pop-up. |
| Backup | Exports and imports a formatted JSON file through the Android file picker. Imports are checked before anything is written. |
| Delete all data | A Danger Zone dialog showing exact record counts, a *Download Backup First* option, and you must type `DELETE` to confirm. |

## Project layout

```
app/src/main/java/com/kargroup/planner/
  data/        Room entities, DAOs, database, repository, JSON backup codec
  reminders/   WorkManager worker, notification actions, snooze/dismiss store
  util/        IST time helpers, recurrence engine
  ui/          Compose screens, dialogs, adaptive shell, theme, logo, 3D components
```
