package com.lykos.pointage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.lykos.pointage.database.GeofenceDatabase

class GeofenceApplication : Application() {

    companion object {
        private const val TAG = "GeofenceApplication"
        const val NOTIFICATION_CHANNEL_ID = "geofence_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Geofence Notifications"
        const val FOREGROUND_SERVICE_CHANNEL_ID = "location_service_channel"
        const val FOREGROUND_SERVICE_CHANNEL_NAME = "Location Service"
    }

    val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            GeofenceDatabase::class.java,
            "geofence_database"
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel for geofence notifications
            val geofenceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for geofence events"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }

            // Channel for foreground service
            val serviceChannel = NotificationChannel(
                FOREGROUND_SERVICE_CHANNEL_ID,
                FOREGROUND_SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location service notifications"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(geofenceChannel)
            notificationManager.createNotificationChannel(serviceChannel)

            Log.d(TAG, "Notification channels created successfully")
        }
    }
}
