package com.lykos.pointage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lykos.pointage.service.BackgroundLocationService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null")
            return
        }
        
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }
        
        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        
        if (triggeringGeofences == null) {
            Log.e(TAG, "Triggering geofences is null")
            return
        }
        
        Log.d(TAG, "Geofence transition: $geofenceTransition")
        
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "Entered geofence")
                handleGeofenceEntry(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "Exited geofence")
                handleGeofenceExit(context)
            }
            else -> {
                Log.w(TAG, "Unknown geofence transition: $geofenceTransition")
            }
        }
    }

    private fun handleGeofenceEntry(context: Context) {
        // Notify the service about geofence entry
        val serviceIntent = Intent(context, BackgroundLocationService::class.java)
        context.startService(serviceIntent)

        // If service is running, handle the entry
        if (BackgroundLocationService.isServiceRunning) {
            val service = BackgroundLocationService()
            service.handleGeofenceEntry()
        }
    }

    private fun handleGeofenceExit(context: Context) {
        // Notify the service about geofence exit
        val serviceIntent = Intent(context, BackgroundLocationService::class.java)
        context.startService(serviceIntent)

        // If service is running, handle the exit
        if (BackgroundLocationService.isServiceRunning) {
            val service = BackgroundLocationService()
            service.handleGeofenceExit()
        }
    }
}
