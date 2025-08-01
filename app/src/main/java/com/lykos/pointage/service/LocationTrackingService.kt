package com.lykos.pointage.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.database.LocationEvent
import com.lykos.pointage.ui.MainActivity
import com.lykos.pointage.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.util.Date


/**
 * Foreground service that tracks time spent outside the geofence
 * Runs in background even when app is closed
 * Shows persistent notification while user is away
 */
class LocationTrackingService : LifecycleService() {

    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_ID = 1001
        private const val AWAY_NOTIFICATION_ID = 1002

        // Service actions
        const val ACTION_START_TRACKING = "start_tracking"
        const val ACTION_STOP_TRACKING = "stop_tracking"
        const val ACTION_GEOFENCE_EXIT = "geofence_exit"
        const val ACTION_GEOFENCE_ENTER = "geofence_enter"

        // Service state
        var isServiceRunning = false
            private set
        var isUserAway = false
            private set
        private var exitTimestamp: Long? = null
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var preferencesManager: PreferencesManager
    private val database by lazy { (application as GeofenceMapApplication).database }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        preferencesManager = PreferencesManager(this)
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
        Log.d(TAG, "Service onStartCommand with action: $action")

        when (action) {
            ACTION_START_TRACKING -> {
                startForegroundTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
            }
            ACTION_GEOFENCE_EXIT -> {
                handleGeofenceExit()
            }
            ACTION_GEOFENCE_ENTER -> {
                handleGeofenceEnter()
            }
            else -> {
                // Default action - start tracking
                startForegroundTracking()
            }
        }

        return START_STICKY // Restart if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        isServiceRunning = false
        isUserAway = false
        preferencesManager.setTrackingState(false)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    /**
     * Starts foreground service with persistent notification
     */
    private fun startForegroundTracking() {
        Log.d(TAG, "Starting foreground tracking")

        val notification = createServiceNotification("Monitoring safe zone", false)
        startForeground(NOTIFICATION_ID, notification)

        preferencesManager.setTrackingState(true)
    }

    /**
     * Stops tracking and removes service
     */
    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking service")

        // If user was away, handle the return
        if (isUserAway) {
            handleGeofenceEnter()
        }

        preferencesManager.setTrackingState(false)
        stopForeground(true)
        stopSelf()
    }

    /**
     * Handles geofence exit event - user left safe zone
     * Starts timer and shows away notification
     */
    private fun handleGeofenceExit() {
        Log.d(TAG, "🔴 User EXITED safe zone - Starting timer")

        isUserAway = true
        exitTimestamp = System.currentTimeMillis()

        // Save exit event to database
        lifecycleScope.launch {
            try {
                val geofencePrefs = preferencesManager.getGeofencePreferences()
                val locationEvent = LocationEvent(
                    exitTime = Date(exitTimestamp!!),
                    enterTime = null,
                    totalTimeAway = 0,
                    geofenceLat = geofencePrefs.latitude,
                    geofenceLng = geofencePrefs.longitude,
                    geofenceRadius = geofencePrefs.radius
                )
                database.locationEventDao().insertLocationEvent(locationEvent)
                Log.d(TAG, "Exit event saved to database")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving exit event", e)
            }
        }

        // Update notifications
        updateServiceNotification("🔴 Outside safe zone - Timer running", true)
        showAwayNotification()
    }

    /**
     * Handles geofence enter event - user returned to safe zone
     * Stops timer and calculates time away
     */
    private fun handleGeofenceEnter() {
        Log.d(TAG, "🟢 User ENTERED safe zone - Stopping timer")

        val enterTime = System.currentTimeMillis()
        val exitTime = exitTimestamp

        if (exitTime != null && isUserAway) {
            val timeAway = enterTime - exitTime
            val timeAwayMinutes = timeAway / (1000 * 60)

            Log.d(TAG, "Time away: ${timeAwayMinutes} minutes")

            // Update database with enter time and duration
            lifecycleScope.launch {
                try {
                    val latestEvent = database.locationEventDao().getLatestLocationEvent()
                    latestEvent?.let { event ->
                        val updatedEvent = event.copy(
                            enterTime = Date(enterTime),
                            totalTimeAway = timeAway
                        )
                        database.locationEventDao().updateLocationEvent(updatedEvent)
                        Log.d(TAG, "Enter event updated in database")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating enter event", e)
                }
            }
        }

        // Reset state
        isUserAway = false
        exitTimestamp = null

        // Update notifications
        updateServiceNotification("🟢 Inside safe zone - Monitoring", false)
        cancelAwayNotification()
    }

    /**
     * Creates the main service notification
     */
    private fun createServiceNotification(text: String, isAway: Boolean): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = if (isAway) android.R.drawable.ic_dialog_alert
        else android.R.drawable.ic_menu_mylocation

        return NotificationCompat.Builder(this, GeofenceMapApplication.FOREGROUND_SERVICE_CHANNEL_ID)
            .setContentTitle("Safe Zone Tracking")
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Updates the service notification with new text
     */
    private fun updateServiceNotification(text: String, isAway: Boolean) {
        val notification = createServiceNotification(text, isAway)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows notification when user is away from safe zone
     */
    private fun showAwayNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, GeofenceMapApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚠️ Outside Safe Zone")
            .setContentText("Timer is running - Return to safe zone")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(false)
            .build()

        notificationManager.notify(AWAY_NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the away notification
     */
    private fun cancelAwayNotification() {
        notificationManager.cancel(AWAY_NOTIFICATION_ID)
    }
}

