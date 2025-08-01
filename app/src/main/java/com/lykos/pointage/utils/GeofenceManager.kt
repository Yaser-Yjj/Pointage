package com.lykos.pointage.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ApiException
import com.lykos.pointage.receiver.GeofenceBroadcastReceiver


/**
 * Manages geofence operations using Google Play Services Location API
 * Handles geofence registration, removal, and error handling
 */
class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_ID = "safe_zone_geofence"
        private const val GEOFENCE_EXPIRATION_TIME = Geofence.NEVER_EXPIRE
        private const val GEOFENCE_LOITERING_DELAY = 1000 // 1 second
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    // PendingIntent for geofence transitions
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Creates and registers a geofence at specified location
     * @param latitude Center latitude of geofence
     * @param longitude Center longitude of geofence
     * @param radius Radius in meters
     * @param onSuccess Callback for successful registration
     * @param onFailure Callback for registration failure
     */
    fun createGeofence(
        latitude: Double,
        longitude: Double,
        radius: Float,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d(TAG, "Creating geofence at: $latitude, $longitude with radius: ${radius}m")

        // Check location permissions
        if (!hasLocationPermissions()) {
            onFailure("Location permissions not granted")
            return
        }

        // Build geofence object
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(GEOFENCE_EXPIRATION_TIME)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT or
                        Geofence.GEOFENCE_TRANSITION_DWELL
            )
            .setLoiteringDelay(2000)
            .build()

        // Build geofencing request
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT
            )
            .addGeofence(geofence)
            .build()


        // Register geofence with Google Play Services
        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Geofence registered successfully")
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "❌ Failed to register geofence", exception)
                    Log.e(TAG, "❌ Error type: ${exception.javaClass.simpleName}")
                    val errorMessage = handleGeofenceError(exception)
                    onFailure(errorMessage)
                }
        } catch (securityException: SecurityException) {
            Log.e(TAG, "❌ Security exception when registering geofence", securityException)
            onFailure("Location permission denied")
        }
    }

    /**
     * Removes all registered geofences
     * @param onComplete Callback when removal is complete (success or failure)
     */
    fun removeGeofences(onComplete: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "Removing all geofences")

        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Geofences removed successfully")
                onComplete?.invoke(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "❌ Failed to remove geofences", exception)
                onComplete?.invoke(false)
            }
    }

    /**
     * Checks if app has required location permissions
     */
    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Fine Location: $fineLocation, Coarse Location: $coarseLocation")
        return fineLocation && coarseLocation
    }

    /**
     * Handles geofence API errors and returns user-friendly messages
     */
    private fun handleGeofenceError(exception: Exception): String {
        return when (exception) {
            is ApiException -> {
                when (exception.statusCode) {
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> {
                        "Geofence service not available. Check location services."
                    }

                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> {
                        "Too many geofences registered."
                    }

                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> {
                        "Too many pending intents for geofences."
                    }

                    1000 -> {
                        "Location services unavailable. Enable high accuracy GPS."
                    }

                    else -> {
                        "Geofence error: ${exception.statusCode}"
                    }
                }
            }

            else -> {
                "Unknown geofence error: ${exception.message}"
            }
        }
    }
}

