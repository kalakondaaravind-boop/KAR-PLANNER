package com.kargroup.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        ContextTagEntity::class,
        MeetingMinuteEntity::class,
    ],
    version = 1,
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
                .build()
    }
}
