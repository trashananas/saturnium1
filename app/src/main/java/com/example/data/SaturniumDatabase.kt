package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShiftCycle::class, CycleDayConfig::class, CyclicReminder::class, DateExclusion::class], version = 2, exportSchema = false)
abstract class SaturniumDatabase : RoomDatabase() {

    abstract fun saturniumDao(): SaturniumDao

    companion object {
        @Volatile
        private var INSTANCE: SaturniumDatabase? = null

        fun getDatabase(context: Context): SaturniumDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaturniumDatabase::class.java,
                    "saturnium_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
