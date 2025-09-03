package com.lykos.pointage.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.data.database.entity.DailyInsideTime
import com.lykos.pointage.data.database.entity.LocationEvent
import com.lykos.pointage.ui.view.MainActivity
import com.lykos.pointage.utils.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service that tracks time spent INSIDE the geofence
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
        const val ACTION_CHECK_INITIAL_LOCATION = "check_initial_location"

        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"

        // Service state
        var isServiceRunning = false
            private set
        var isUserAway = false
            private set
        private var exitTimestamp: Long? = null // Last exit time
    }

    private lateinit var notificationManager: NotificationManager
    private lateinit var preferencesManager: PreferencesManager
    private val database by lazy { (application as GeofenceMapApplication).database }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        preferencesManager = PreferencesManager(this)
        isServiceRunning = true
        isUserAway = false
        exitTimestamp = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action

        when (action) {
            ACTION_START_TRACKING -> startForegroundTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_GEOFENCE_EXIT -> handleGeofenceExit()
            ACTION_GEOFENCE_ENTER -> handleGeofenceEnter()
            ACTION_CHECK_INITIAL_LOCATION -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                if (lat != 0.0 && lng != 0.0) {
                    val location = Location("").apply {
                        latitude = lat
                        longitude = lng
                    }
                    handleInitialLocationCheck(location)
                }
            }
            else -> startForegroundTracking()
        }
        return START_STICKY
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

    private fun startForegroundTracking() {
        val notification = createServiceNotification("Monitoring safe zone", false)
        startForeground(NOTIFICATION_ID, notification)
        preferencesManager.setTrackingState(true)
    }

    private fun stopTracking() {
        if (isUserAway) {
            handleGeofenceEnter() // Close ongoing session
        }
        preferencesManager.setTrackingState(false)
        stopForeground(true)
        stopSelf()
    }

    /**
     * When user exits: calculate & save time spent INSIDE
     */
    private fun handleGeofenceExit() {
        Log.d(TAG, "🔴 User EXITED safe zone - Recording time spent INSIDE")

        preferencesManager.setState(true)
        val exitTime = System.currentTimeMillis()
        preferencesManager.saveLastExitTimestamp(exitTime)

        // Calculate time INSIDE during this session
        val lastEnterTime = preferencesManager.getLastEnterTimestamp()
        val timeInside = if (lastEnterTime != 0L) exitTime - lastEnterTime else 0L

        preferencesManager.saveLastEnterTimestamp(0L) // Clear
        broadcastGeofenceStateChange()

        if (!isUserAway) {
            isUserAway = true
            exitTimestamp = exitTime

            lifecycleScope.launch {
                try {
                    // Save to daily total
                    saveInsideTimeToDailyDatabase(timeInside)

                    // Save to events
                    val prefs = preferencesManager.getGeofencePreferences()
                    val event = LocationEvent(
                        exitTime = Date(exitTime),
                        enterTime = null,
                        totalTimeInside = timeInside,
                        geofenceLat = prefs.latitude,
                        geofenceLng = prefs.longitude,
                        geofenceRadius = prefs.radius
                    )
                    database.locationEventDao().insertLocationEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving exit event", e)
                }
            }
        }

        updateServiceNotification("🔴 Outside safe zone", true)
        showAwayNotification()
    }

    /**
     * When user enters: just update state & close last event
     */
    private fun handleGeofenceEnter() {
        Log.d(TAG, "🟢 User ENTERED safe zone")

        preferencesManager.setState(false)
        val enterTime = System.currentTimeMillis()
        preferencesManager.saveLastEnterTimestamp(enterTime)
        preferencesManager.saveLastExitTimestamp(0L)
        broadcastGeofenceStateChange()

        if (isUserAway) {
            val exitTime = exitTimestamp
            if (exitTime != null) {
                lifecycleScope.launch {
                    try {
                        val latestEvent = database.locationEventDao().getLatestLocationEvent()
                        latestEvent?.let { event ->
                            if (event.enterTime == null) {
                                val updatedEvent = event.copy(enterTime = Date(enterTime))
                                database.locationEventDao().updateLocationEvent(updatedEvent)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating enter event", e)
                    }
                }
            }
            isUserAway = false
            exitTimestamp = null
        }

        updateServiceNotification("🟢 Inside safe zone", false)
        cancelAwayNotification()
    }

    /**
     * On service start: check initial location
     */
    private fun handleInitialLocationCheck(currentLocation: Location) {
        val prefs = preferencesManager.getGeofencePreferences()
        if (prefs.latitude == 0.0 || prefs.longitude == 0.0) return

        val center = Location("").apply {
            latitude = prefs.latitude
            longitude = prefs.longitude
        }

        val distance = currentLocation.distanceTo(center)
        val radius = prefs.radius

        if (distance > radius) {
            // Outside
            isUserAway = true
            exitTimestamp = System.currentTimeMillis()
            preferencesManager.setState(true)
            preferencesManager.saveLastExitTimestamp(exitTimestamp!!)
            preferencesManager.saveLastEnterTimestamp(0L)

            lifecycleScope.launch {
                try {
                    val latestEvent = database.locationEventDao().getLatestLocationEvent()
                    if (latestEvent == null || latestEvent.enterTime != null) {
                        val event = LocationEvent(
                            exitTime = Date(exitTimestamp!!),
                            enterTime = null,
                            totalTimeInside = 0,
                            geofenceLat = prefs.latitude,
                            geofenceLng = prefs.longitude,
                            geofenceRadius = prefs.radius
                        )
                        database.locationEventDao().insertLocationEvent(event)
                    } else {
                        exitTimestamp = latestEvent.exitTime?.time ?: System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving initial exit event", e)
                }
            }

            updateServiceNotification("🔴 Outside safe zone", true)
            showAwayNotification()
        } else {
            // Inside
            isUserAway = false
            exitTimestamp = null
            preferencesManager.setState(false)
            val enterTime = System.currentTimeMillis()
            preferencesManager.saveLastEnterTimestamp(enterTime)
            preferencesManager.saveLastExitTimestamp(0L)

            updateServiceNotification("🟢 Inside safe zone", false)
            cancelAwayNotification()
        }
    }

    /**
     * Save time inside to daily database
     */
    private suspend fun saveInsideTimeToDailyDatabase(timeInside: Long) {
        if (timeInside <= 0) return
        val dao = database.dailyInsideTimeDao()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
        val existing = dao.getByDate(today)
        if (existing != null) {
            dao.update(existing.copy(totalTimeInside = existing.totalTimeInside + timeInside))
        } else {
            dao.insert(DailyInsideTime(date = today, totalTimeInside = timeInside))
        }
    }

    private fun broadcastGeofenceStateChange() {
        sendBroadcast(Intent("com.lykos.pointage.GEOFENCE_STATE_CHANGED"))
    }

    // --- Notifications ---

    private fun createServiceNotification(text: String, isAway: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = if (isAway) android.R.drawable.ic_dialog_alert else android.R.drawable.ic_menu_mylocation

        return NotificationCompat.Builder(this, GeofenceMapApplication.FOREGROUND_SERVICE_CHANNEL_ID)
            .setContentTitle("Safe Zone Tracking")
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateServiceNotification(text: String, isAway: Boolean) {
        notificationManager.notify(NOTIFICATION_ID, createServiceNotification(text, isAway))
    }

    private fun showAwayNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, GeofenceMapApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("⚠️ Outside Safe Zone")
            .setContentText("Return to safe zone")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(false)
            .build()
        notificationManager.notify(AWAY_NOTIFICATION_ID, notification)
    }

    private fun cancelAwayNotification() {
        notificationManager.cancel(AWAY_NOTIFICATION_ID)
    }
}