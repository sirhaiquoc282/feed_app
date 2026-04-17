package com.danghung.nhungapp

import android.app.Application
import androidx.room.Room
import com.danghung.nhungapp.data.local.db.AppDatabase

class App : Application() {
    lateinit var storage: Storage
    // Added: Room database singleton for schedule persistence.
    lateinit var appDatabase: AppDatabase

    companion object{
        lateinit var instance: App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        initRoomDatabase()
        storage = Storage()
    }

    // Added: initialize Room once when app starts.
    private fun initRoomDatabase() {
        appDatabase = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "nhungapp.db"
        )
            // Added: recreate local DB if schema version changes during development.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
}