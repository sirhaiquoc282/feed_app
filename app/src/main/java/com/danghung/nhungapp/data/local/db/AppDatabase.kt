package com.danghung.nhungapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.danghung.nhungapp.data.local.dao.HistoryDao
import com.danghung.nhungapp.data.local.dao.ScheduleDao
import com.danghung.nhungapp.data.local.entity.HistoryEntity
import com.danghung.nhungapp.data.local.entity.ScheduleEntity

// Added: central Room database for the app.
@Database(
    entities = [ScheduleEntity::class, HistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun historyDao(): HistoryDao
}
