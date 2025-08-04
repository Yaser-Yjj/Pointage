package com.lykos.pointage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.lykos.pointage.service.LocationTrackingService

/**
 * BroadcastReceiver that handles geofence transition events
 * Receives ENTER and EXIT events from Google Play Services
 * Forwards events to LocationTrackingService for processing
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {

        // Parse geofencing event from intent
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "❌ Geofencing event is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "❌ Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        // Get transition type and triggering geofences
        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences

        if (triggeringGeofences.isNullOrEmpty()) {
            Log.e(TAG, "❌ No triggering geofences found")
            return
        }

        // Handle different transition types
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "🟢 ENTERED safe zone")
                handleGeofenceEnter(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "🔴 EXITED safe zone")
                handleGeofenceExit(context)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown geofence transition: $geofenceTransition")
            }
        }
    }

    /**
     * Handles geofence ENTER event
     * Starts LocationTrackingService with ENTER action
     */
    private fun handleGeofenceEnter(context: Context) {

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_GEOFENCE_ENTER
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service for ENTER event", e)
        }
    }

    /**
     * Handles geofence EXIT event
     * Starts LocationTrackingService with EXIT action
     */
    private fun handleGeofenceExit(context: Context) {

        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_GEOFENCE_EXIT
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service for EXIT event", e)
        }
    }
}
