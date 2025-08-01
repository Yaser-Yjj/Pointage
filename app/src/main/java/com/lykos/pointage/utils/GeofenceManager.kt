package com.lykos.pointage.utils

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ApiException
import com.lykos.pointage.receiver.GeofenceBroadcastReceiver

class GeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeofenceManager"
        private const val GEOFENCE_ID = "safe_zone_geofence"
        private const val GEOFENCE_EXPIRATION_TIME = Geofence.NEVER_EXPIRE
        private const val GEOFENCE_LOITERING_DELAY = 1000
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun createGeofence(
        latitude: Double,
        longitude: Double,
        radius: Float,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!hasLocationPermissions()) {
            onFailure("Location permissions not granted")
            return
        }

        val geofence = Geofence.Builder().setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(GEOFENCE_EXPIRATION_TIME).setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_DWELL
            ).setLoiteringDelay(GEOFENCE_LOITERING_DELAY).build()

        val geofencingRequest = GeofencingRequest.Builder().setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT
            ).addGeofence(geofence).build()

        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                .addOnSuccessListener { onSuccess() }.addOnFailureListener { exception ->
                    val errorMessage = handleGeofenceError(exception)
                    onFailure(errorMessage)
                }
        } catch (_: SecurityException) {
            onFailure("Location permission denied")
        }
    }

    fun removeGeofences(onComplete: ((Boolean) -> Unit)? = null) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener { onComplete?.invoke(false) }
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation && coarseLocation
    }

    private fun handleGeofenceError(exception: Exception): String {
        return when (exception) {
            is ApiException -> {
                when (exception.statusCode) {
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> "Geofence service not available. Check location services."
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "Too many geofences registered."
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> "Too many pending intents for geofences."
                    1000 -> "Location services unavailable. Enable high accuracy GPS."
                    else -> "Geofence error: ${exception.statusCode}"
                }
            }

            else -> "Unknown geofence error: ${exception.message}"
        }
    }
}
