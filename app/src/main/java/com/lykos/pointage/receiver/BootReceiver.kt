package com.lykos.pointage.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lykos.pointage.service.BackgroundLocationService
import com.lykos.pointage.utils.GeofenceManager

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot receiver triggered: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d(TAG, "Device booted or app updated, restarting location service")
                restartLocationService(context)
            }
        }
    }
    
    private fun restartLocationService(context: Context) {
        try {
            // Start the background service
            val serviceIntent = Intent(context, BackgroundLocationService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            
            // Re-setup geofences
            val geofenceManager = GeofenceManager(context)
            geofenceManager.setupGeofences()
            
            Log.d(TAG, "Location service restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart location service", e)
        }
    }
}
