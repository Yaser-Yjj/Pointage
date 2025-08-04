package com.lykos.pointage.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
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
        const val ACTION_CHECK_INITIAL_LOCATION = "check_initial_location" // NEW ACTION
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"

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

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        preferencesManager = PreferencesManager(this)
        isServiceRunning = true
        isUserAway = false // Reset state on service creation
        exitTimestamp = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action

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
            ACTION_CHECK_INITIAL_LOCATION -> { // NEW: Handle initial location check
                val currentLat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val currentLng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                if (currentLat != 0.0 || currentLng != 0.0) {
                    val currentLocation = Location("").apply {
                        latitude = currentLat
                        longitude = currentLng
                    }
                    handleInitialLocationCheck(currentLocation)
                } else {
                    Log.w(TAG, "Initial location data missing from intent.")
                }
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
        isServiceRunning = false
        isUserAway = false
        exitTimestamp = null
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
        val notification = createServiceNotification("Monitoring safe zone", false)
        startForeground(NOTIFICATION_ID, notification)
        preferencesManager.setTrackingState(true)
    }

    /**
     * Stops tracking and removes service
     */
    private fun stopTracking() {
        // If user was away, handle the return before stopping
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

        // Only start timer if not already away
        if (!isUserAway) {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving exit event", e)
                }
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

        // Only process if user was previously away
        if (isUserAway) {
            val enterTime = System.currentTimeMillis()
            val exitTime = exitTimestamp

            if (exitTime != null) {
                val timeAway = enterTime - exitTime
                val timeAwayMinutes = timeAway / (1000 * 60)

                Log.d(TAG, "Time away: $timeAwayMinutes minutes")

                // Update database with enter time and duration
                lifecycleScope.launch {
                    try {
                        val latestEvent = database.locationEventDao().getLatestLocationEvent()
                        latestEvent?.let { event ->
                            // Only update if this event corresponds to the last exit
                            if (event.enterTime == null && event.exitTime?.time == exitTime) {
                                val updatedEvent = event.copy(
                                    enterTime = Date(enterTime),
                                    totalTimeAway = timeAway
                                )
                                database.locationEventDao().updateLocationEvent(updatedEvent)
                            } else if (event.enterTime == null) {
                                // If there's an unclosed event but it's not the one we just exited,
                                // it means the geofence system might have missed an exit.
                                // For robustness, we can create a new entry for this enter.
                                // Or, more simply, just ensure the current state is correct.
                                Log.w(TAG, "Latest event not matching current exitTimestamp. Possible missed exit event.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating enter event", e)
                    }
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
     * NEW: Handles initial location check after service starts.
     * Determines if the user is inside or outside the geofence and triggers appropriate state/notification.
     */
    private fun handleInitialLocationCheck(currentLocation: Location) {
        val geofencePrefs = preferencesManager.getGeofencePreferences()
        if (geofencePrefs.latitude == 0.0 && geofencePrefs.longitude == 0.0) {
            Log.w(TAG, "Geofence preferences not set for initial location check.")
            return
        }

        val geofenceCenter = Location("").apply {
            latitude = geofencePrefs.latitude
            longitude = geofencePrefs.longitude
        }

        val distance = currentLocation.distanceTo(geofenceCenter)
        val radius = geofencePrefs.radius

        Log.d(TAG, "Initial check: Distance to center: $distance m, Radius: $radius m")

        if (distance > radius) {
            // User is outside the geofence
            Log.d(TAG, "Initial check: User is OUTSIDE safe zone. Setting state and showing notification.")
            isUserAway = true
            exitTimestamp = System.currentTimeMillis() // Assume they just exited for tracking purposes

            // Save initial exit event to database if no ongoing away event
            lifecycleScope.launch {
                try {
                    val latestEvent = database.locationEventDao().getLatestLocationEvent()
                    // If there's no latest event, or the latest event has an enterTime (meaning it's closed)
                    if (latestEvent == null || latestEvent.enterTime != null) {
                        val locationEvent = LocationEvent(
                            exitTime = Date(exitTimestamp!!),
                            enterTime = null,
                            totalTimeAway = 0,
                            geofenceLat = geofencePrefs.latitude,
                            geofenceLng = geofencePrefs.longitude,
                            geofenceRadius = geofencePrefs.radius
                        )
                        database.locationEventDao().insertLocationEvent(locationEvent)
                    } else {
                        // There's an unclosed event, assume it's the one we're continuing
                        Log.d(TAG, "Continuing existing unclosed exit event from initial check.")
                        exitTimestamp = latestEvent.exitTime?.time ?: System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving initial exit event", e)
                }
            }
            updateServiceNotification("🔴 Outside safe zone - Timer running", true)
            showAwayNotification()
        } else {
            // User is inside or at the edge of the geofence
            Log.d(TAG, "Initial check: User is INSIDE safe zone. Setting state and cancelling away notification.")
            isUserAway = false
            exitTimestamp = null // Ensure no pending exit timestamp

            // If there was an ongoing away event, mark it as entered now
            lifecycleScope.launch {
                try {
                    val latestEvent = database.locationEventDao().getLatestLocationEvent()
                    if (latestEvent != null && latestEvent.enterTime == null) { // Ongoing away event
                        val timeAway = System.currentTimeMillis() - (latestEvent.exitTime?.time ?: System.currentTimeMillis())
                        val updatedEvent = latestEvent.copy(
                            enterTime = Date(System.currentTimeMillis()),
                            totalTimeAway = timeAway
                        )
                        database.locationEventDao().updateLocationEvent(updatedEvent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating initial enter event", e)
                }
            }
            updateServiceNotification("🟢 Inside safe zone - Monitoring", false)
            cancelAwayNotification()
        }
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
