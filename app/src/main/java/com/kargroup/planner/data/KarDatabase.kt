package com.kargroup.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        ContextTagEntity::class,
        MeetingMinuteEntity::class,
        JournalEntry::class,
        PersonEntity::class,
        TaskHistoryEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KarDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun contextDao(): ContextDao
    abstract fun meetingDao(): MeetingDao
    abstract fun journalDao(): JournalDao
    abstract fun personDao(): PersonDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun build(context: Context): KarDatabase =
            Room.databaseBuilder(context.applicationContext, KarDatabase::class.java, "kar_planner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()

        /** v2 adds target time, estimated effort and manual schedule order - existing data is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN due_time TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimated_minutes INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN manual_order INTEGER")
            }
        }

        /** v3 adds activity status and assigned person - existing data is kept. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN assignee TEXT")
            }
        }

        /** v4 adds the personal daily journal - existing data is kept. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` TEXT NOT NULL, `entry_date` TEXT NOT NULL, " +
                        "`mood` TEXT, `title` TEXT, `content` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_entry_date` ON `journal_entries` (`entry_date`)")
            }
        }

        /** v8 adds the "current status" update text for each activity - existing data is kept. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN status_note TEXT")
            }
        }

        /** v7 adds activity history and user-fixed start times - existing data is kept. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN planned_start INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `task_history` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `detail` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_history_task_id` ON `task_history` (`task_id`)")
                // Everything in the app is shown in CAPITAL LETTERS - store existing entries that way too.
                db.execSQL("UPDATE tasks SET text = UPPER(text), notes = UPPER(notes), assignee = UPPER(assignee), completion_remarks = UPPER(completion_remarks)")
                db.execSQL("UPDATE projects SET name = UPPER(name), description = UPPER(description)")
                db.execSQL("UPDATE persons SET name = UPPER(name), role = UPPER(role)")
                db.execSQL("UPDATE journal_entries SET title = UPPER(title), content = UPPER(content)")
                db.execSQL("UPDATE meeting_minutes SET title = UPPER(title), agenda = UPPER(agenda), discussion_notes = UPPER(discussion_notes)")
            }
        }

        /** v6 adds completion remarks - existing data is kept. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completion_remarks TEXT")
            }
        }

        /** v5 adds the people list; names already assigned to activities are copied in. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT, " +
                        "`email` TEXT, `phone` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO persons (id, name, created_at) " +
                        "SELECT lower(hex(randomblob(16))), MIN(TRIM(assignee)), 0 FROM tasks " +
                        "WHERE assignee IS NOT NULL AND TRIM(assignee) != '' GROUP BY LOWER(TRIM(assignee))"
                )
            }
        }
    }
}
