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
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class KarDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun contextDao(): ContextDao
    abstract fun meetingDao(): MeetingDao

    companion object {
        fun build(context: Context): KarDatabase =
            Room.databaseBuilder(context.applicationContext, KarDatabase::class.java, "kar_planner.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
