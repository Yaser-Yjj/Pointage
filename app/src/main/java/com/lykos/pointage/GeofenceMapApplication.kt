package com.lykos.pointage

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.lykos.pointage.database.GeofenceDatabase

/**
 * Application class for initializing app-wide components
 * - Creates notification channels
 * - Initializes Room database
 */
class GeofenceMapApplication : Application() {

    companion object {
        private const val TAG = "GeofenceMapApplication"
        const val NOTIFICATION_CHANNEL_ID = "geofence_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Geofence Notifications"
        const val FOREGROUND_SERVICE_CHANNEL_ID = "location_service_channel"
        const val FOREGROUND_SERVICE_CHANNEL_NAME = "Location Tracking Service"
    }

    // Room database instance - lazy initialization
    val database by lazy {
        GeofenceDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Creates notification channels for Android O+
     * - Geofence events channel (high importance)
     * - Foreground service channel (low importance)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel for geofence event notifications
            val geofenceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for geofence enter/exit events"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }

            // Channel for foreground service (persistent notification)
            val serviceChannel = NotificationChannel(
                FOREGROUND_SERVICE_CHANNEL_ID,
                FOREGROUND_SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location tracking service"
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

