package com.lykos.pointage.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.lykos.pointage.GeofenceApplication
import com.lykos.pointage.MainActivity
import com.lykos.pointage.database.LocationEvent
import com.lykos.pointage.utils.GeofenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

class BackgroundLocationService : LifecycleService() {

    companion object {
        private const val TAG = "BackgroundLocationService"
        private const val NOTIFICATION_ID = 1001
        var isServiceRunning = false
            private set

        private var exitTimestamp: Long? = null
    }

    private lateinit var geofenceManager: GeofenceManager
    private val database by lazy { (application as GeofenceApplication).database }
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        geofenceManager = GeofenceManager(this)
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service onStartCommand")

        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            // Setup geofences with a delay to ensure service is fully started
            lifecycleScope.launch {
                delay(1000) // Wait 1 second
                geofenceManager.setupGeofences()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            stopSelf()
        }

        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        isServiceRunning = false
        geofenceManager.removeGeofences()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GeofenceApplication.FOREGROUND_SERVICE_CHANNEL_ID,
                GeofenceApplication.FOREGROUND_SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background location service notifications"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, GeofenceApplication.FOREGROUND_SERVICE_CHANNEL_ID)
            .setContentTitle("Location Tracking Active")
            .setContentText("Monitoring geofence area")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun handleGeofenceExit() {
        Log.d(TAG, "Handling geofence exit")
        exitTimestamp = System.currentTimeMillis()

        lifecycleScope.launch {
            try {
                val locationEvent = LocationEvent(
                    exitTimestamp = Date(exitTimestamp!!),
                    entryTimestamp = null,
                    totalTimeAway = 0
                )
                database.locationEventDao().insertLocationEvent(locationEvent)
                Log.d(TAG, "Exit event saved to database")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving exit event", e)
            }
        }

        // Show notification that user is away
        geofenceManager.showAwayNotification()
    }

    fun handleGeofenceEntry() {
        Log.d(TAG, "Handling geofence entry")
        val entryTime = System.currentTimeMillis()
        val exitTime = exitTimestamp

        if (exitTime != null) {
            val timeAway = entryTime - exitTime

            lifecycleScope.launch {
                try {
                    // Update the latest record with entry time and duration
                    val latestEvent = database.locationEventDao().getLatestLocationEvent()
                    latestEvent?.let { event ->
                        val updatedEvent = event.copy(
                            entryTimestamp = Date(entryTime),
                            totalTimeAway = timeAway
                        )
                        database.locationEventDao().updateLocationEvent(updatedEvent)
                        Log.d(TAG, "Entry event updated in database. Time away: ${timeAway}ms")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating entry event", e)
                }
            }

            exitTimestamp = null
        }

        // Cancel the away notification
        geofenceManager.cancelAwayNotification()
    }
}