package com.lykos.pointage.utils

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.api.ApiException
import com.lykos.pointage.GeofenceApplication
import com.lykos.pointage.MainActivity
import com.lykos.pointage.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_ID = "main_geofence"
        private const val AWAY_NOTIFICATION_ID = 2001

        // Define your geofence area here
        private const val GEOFENCE_LATITUDE = 37.7749 // San Francisco example
        private const val GEOFENCE_LONGITUDE = -122.4194
        private const val GEOFENCE_RADIUS_METERS = 100f // 100 meters radius
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun setupGeofences() {
        Log.d(TAG, "Setting up geofences...")

        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "Location services are disabled")
            return
        }

        // Remove existing geofences first
        removeGeofences {
            // Add new geofences after removal
            addGeofences()
        }
    }

    private fun addGeofences() {
        val geofence = createGeofence()
        val geofencingRequest = createGeofencingRequest(geofence)

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Fine location permission not granted")
                return
            }

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added successfully")
                }
                .addOnFailureListener { exception ->
                    handleGeofenceError(exception, "add")
                }
        } catch (securityException: SecurityException) {
            Log.e(TAG, "Security exception when adding geofence", securityException)
        }
    }

    fun removeGeofences(onComplete: (() -> Unit)? = null) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "Geofences removed successfully")
                onComplete?.invoke()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove geofences", exception)
                onComplete?.invoke() // Still call onComplete even if removal fails
            }
    }

    private fun handleGeofenceError(exception: Exception, operation: String) {
        when (exception) {
            is ApiException -> {
                val errorMessage = when (exception.statusCode) {
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                        "Geofence service is not available. Check if location services are enabled and Google Play Services is updated."
                    }
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                        "Too many geofences registered. Maximum is 100 per app."
                    }
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                        "Too many pending intents registered for geofences."
                    }
                    1000 -> {
                        "Location services unavailable. Please enable high accuracy location mode and ensure Google Play Services is updated."
                    }
                    else -> {
                        "Geofence error: ${exception.statusCode} - ${exception.message}"
                    }
                }
                Log.e(TAG, "Failed to $operation geofence: $errorMessage")
            }
            else -> {
                Log.e(TAG, "Failed to $operation geofence", exception)
            }
        }
    }

    private fun createGeofence(): Geofence {
        return Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(GEOFENCE_LATITUDE, GEOFENCE_LONGITUDE, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(5000) // 5 seconds delay to avoid false triggers
            .build()
    }

    private fun createGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(0) // Don't trigger immediately, wait for actual transitions
            .addGeofence(geofence)
            .build()
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Location permissions - Fine: $fineLocation, Coarse: $coarseLocation")
        return fineLocation && coarseLocation
    }

    private fun isLocationEnabled(): Boolean {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        Log.d(TAG, "Location providers - GPS: $isGpsEnabled, Network: $isNetworkEnabled")
        return isGpsEnabled || isNetworkEnabled
    }

    fun showAwayNotification() {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GeofenceApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("You are away from the zone")
            .setContentText("Timer started - tracking time away from designated area")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        notificationManager.notify(AWAY_NOTIFICATION_ID, notification)
        Log.d(TAG, "Away notification shown")
    }

    fun cancelAwayNotification() {
        notificationManager.cancel(AWAY_NOTIFICATION_ID)
        Log.d(TAG, "Away notification cancelled")
    }
}
