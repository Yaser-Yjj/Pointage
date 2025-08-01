package com.lykos.pointage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lykos.pointage.service.LocationTrackingService
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.utils.PreferencesManager


/**
 * BroadcastReceiver that handles device boot events
 * Restores geofence and tracking service after device reboot
 * Ensures continuous tracking across device restarts
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Device booted or app updated - restoring geofence")
                restoreGeofenceAfterBoot(context)
            }
        }
    }

    /**
     * Restores geofence and tracking service after device boot
     * Only restores if geofence was previously configured and active
     */
    private fun restoreGeofenceAfterBoot(context: Context) {
        try {
            val preferencesManager = PreferencesManager(context)
            val geofencePrefs = preferencesManager.getGeofencePreferences()

            // Check if geofence was previously active
            if (geofencePrefs.isGeofenceActive &&
                geofencePrefs.latitude != 0.0 &&
                geofencePrefs.longitude != 0.0) {

                // Re-register geofence
                val geofenceManager = GeofenceManager(context)
                geofenceManager.createGeofence(
                    latitude = geofencePrefs.latitude,
                    longitude = geofencePrefs.longitude,
                    radius = geofencePrefs.radius,
                    onSuccess = {
                        if (preferencesManager.isTracking()) {
                            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                                action = LocationTrackingService.ACTION_START_TRACKING
                            }
                            ContextCompat.startForegroundService(context, serviceIntent)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "❌ Failed to restore geofence after boot: $error")
                    }
                )
            } else {
                Log.d(TAG, "No active geofence to restore")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error restoring geofence after boot", e)
        }
    }
}

