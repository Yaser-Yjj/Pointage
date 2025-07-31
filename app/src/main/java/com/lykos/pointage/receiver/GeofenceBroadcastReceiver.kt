package com.lykos.pointage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lykos.pointage.service.BackgroundLocationService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
        const val ACTION_GEOFENCE_EXIT = "com.lykos.pointage.GEOFENCE_EXIT"
        const val ACTION_GEOFENCE_ENTER = "com.lykos.pointage.GEOFENCE_ENTER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "🚨🚨🚨 BROADCAST RECEIVED! 🚨🚨🚨")
        Log.d(TAG, "Intent action: ${intent.action}")
        Log.d(TAG, "Intent extras: ${intent.extras}")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "❌ Geofencing event is null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "❌ Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences

        Log.d(TAG, "🎯 Geofence transition detected: $geofenceTransition")
        Log.d(TAG, "📍 Number of triggering geofences: ${triggeringGeofences?.size ?: 0}")

        if (triggeringGeofences == null || triggeringGeofences.isEmpty()) {
            Log.e(TAG, "❌ No triggering geofences found")
            return
        }

        // Log each triggering geofence
        triggeringGeofences.forEach { geofence ->
            Log.d(TAG, "🎯 Triggered geofence ID: ${geofence.requestId}")
        }

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "🟢🟢🟢 ENTERED GEOFENCE - YOU ARE INSIDE! 🟢🟢🟢")
                handleGeofenceEntry(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "🔴🔴🔴 EXITED GEOFENCE - YOU ARE OUTSIDE! 🔴🔴🔴")
                handleGeofenceExit(context)
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown geofence transition: $geofenceTransition")
            }
        }
    }

    private fun handleGeofenceEntry(context: Context) {
        Log.d(TAG, "✅ Processing geofence ENTRY")

        // Send broadcast to service
        val serviceIntent = Intent(context, BackgroundLocationService::class.java).apply {
            action = ACTION_GEOFENCE_ENTER
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun handleGeofenceExit(context: Context) {
        Log.d(TAG, "✅ Processing geofence EXIT")

        // Send broadcast to service
        val serviceIntent = Intent(context, BackgroundLocationService::class.java).apply {
            action = ACTION_GEOFENCE_EXIT
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
