import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
}

group = "com.kargroup.planner"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

/*
 * The desktop app reuses the tablet app's planner logic and screens directly from ../app,
 * so both stay identical. Only Android-specific pieces (storage, alarms, sharing) are
 * replaced by desktop versions in src/main/kotlin.
 */
val sharedSources = listOf(
    "com/kargroup/planner/data/BackupCodec.kt",
    "com/kargroup/planner/data/Daos.kt",
    "com/kargroup/planner/data/Entities.kt",
    "com/kargroup/planner/data/KarRepository.kt",
    "com/kargroup/planner/data/Settings.kt",
    "com/kargroup/planner/util/IstTime.kt",
    "com/kargroup/planner/util/Recurrence.kt",
    "com/kargroup/planner/reminders/Reminders.kt",
    "com/kargroup/planner/ui/AddTaskDialog.kt",
    "com/kargroup/planner/ui/Celebration.kt",
    "com/kargroup/planner/ui/Journal.kt",
    "com/kargroup/planner/ui/MeetingScreens.kt",
    "com/kargroup/planner/ui/OrganizeScreens.kt",
    "com/kargroup/planner/ui/People.kt",
    "com/kargroup/planner/ui/Persons.kt",
    "com/kargroup/planner/ui/QuickAdd.kt",
    "com/kargroup/planner/ui/Reports.kt",
    "com/kargroup/planner/ui/Reschedule.kt",
    "com/kargroup/planner/ui/Scheduler.kt",
    "com/kargroup/planner/ui/Screen.kt",
    "com/kargroup/planner/ui/Search.kt",
    "com/kargroup/planner/ui/Shell.kt",
    "com/kargroup/planner/ui/TaskListScreen.kt",
    "com/kargroup/planner/ui/TaskTree.kt",
    "com/kargroup/planner/ui/UtilityDialogs.kt",
    "com/kargroup/planner/ui/components/Common.kt",
    "com/kargroup/planner/ui/components/Depth.kt",
    "com/kargroup/planner/ui/components/UpperCase.kt",
    "com/kargroup/planner/ui/theme/Theme.kt",
)

val syncShared by tasks.registering(Sync::class) {
    from("../app/src/main/java") { include(sharedSources) }
    into(layout.buildDirectory.dir("shared/kotlin"))
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir(syncShared)
}

compose.desktop {
    application {
        mainClass = "com.kargroup.planner.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "KAR Planner"
            packageVersion = "1.0.${(System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt() % 1000}"
            description = "KAR Group Planner - activities, MoM and Outlook reminders"
            vendor = "KAR Group"
            copyright = "KAR Group"
            includeAllModules = true
            windows {
                iconFile.set(project.file("kar_icon.ico"))
                menuGroup = "KAR Group"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1e2a4c-3b7d-4c55-9a8e-2d6b0c9f7e41"
            }
        }
    }
}
