package com.lykos.pointage.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

/**
 * Room database for storing geofence events
 * Uses singleton pattern for single database instance
 */
@Database(
    entities = [LocationEvent::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GeofenceDatabase : RoomDatabase() {

    abstract fun locationEventDao(): LocationEventDao

    companion object {
        @Volatile
        private var INSTANCE: GeofenceDatabase? = null

        fun getDatabase(context: Context): GeofenceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GeofenceDatabase::class.java,
                    "geofence_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
