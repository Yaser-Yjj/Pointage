package com.lykos.pointage.ui.controller

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.lykos.pointage.data.model.data.SafeZoneData
import com.lykos.pointage.service.LocationTrackingService
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceTrackingController(
    private val context: Context,
    private val activity: AppCompatActivity,
    private val geofenceManager: GeofenceManager,
    private val viewModel: MainViewModel,
    private val fusedLocationClient: FusedLocationProviderClient
) {

    companion object {
        private const val TAG = "GeofenceTrackingController"
    }

    fun startGeofenceTracking() {
        val geofenceData = viewModel.currentGeofenceData.value

        val validatedData = validateGeofenceData(geofenceData) ?: return
        if (isAlreadyTracking()) return
        if (!hasLocationPermission()) return

        createGeofenceWithCallbacks(validatedData)
    }

    fun stopGeofenceTracking() {
        if (!isCurrentlyTracking()) {
            Log.d(TAG, "No active tracking to stop.")
            return
        }

        geofenceManager.removeGeofences { success ->
            handleTrackingStopResult(success)
        }
    }

    private fun validateGeofenceData(data: SafeZoneData?): SafeZoneData? {
        if (data == null || data.latitude == 0.0) {
            Toast.makeText(activity, "Safe zone not set.", Toast.LENGTH_LONG).show()
            return null
        }
        return data
    }

    private fun isAlreadyTracking(): Boolean {
        val isTracking = viewModel.isTracking.value == true
        if (isTracking) {
            Log.d(TAG, "Tracking already active.")
        }
        return isTracking
    }

    private fun isCurrentlyTracking(): Boolean {
        return viewModel.isTracking.value == true
    }

    private fun hasLocationPermission(): Boolean {
        val hasPermission = ActivityCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Toast.makeText(activity, "Location permission missing.", Toast.LENGTH_SHORT).show()
        }

        return hasPermission
    }

    fun createGeofenceWithCallbacks(data: SafeZoneData) {
        geofenceManager.createGeofence(
            latitude = data.latitude,
            longitude = data.longitude,
            radius = data.radius,
            onSuccess = {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@createGeofence
                }
                handleTrackingStartSuccess()
            },
            onFailure = { error -> handleTrackingStartFailure(error) }
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun handleTrackingStartSuccess() {
        viewModel.updateTrackingState(true)
        startLocationTrackingService()
        checkInitialLocation()
        Toast.makeText(activity, "Tracking started!", Toast.LENGTH_SHORT).show()
    }

    private fun handleTrackingStartFailure(error: String) {
        Log.e(TAG, "Geofence failed: $error")
        Toast.makeText(activity, "Start failed: $error", Toast.LENGTH_LONG).show()
        viewModel.updateTrackingState(false)
    }

    private fun startLocationTrackingService() {
        val serviceIntent = Intent(activity, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(activity, serviceIntent)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun checkInitialLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val checkIntent = Intent(activity, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_CHECK_INITIAL_LOCATION
                    putExtra(LocationTrackingService.EXTRA_LATITUDE, it.latitude)
                    putExtra(LocationTrackingService.EXTRA_LONGITUDE, it.longitude)
                }
                ContextCompat.startForegroundService(activity, checkIntent)
            }
        }
    }

    private fun handleTrackingStopResult(success: Boolean) {
        activity.lifecycleScope.launch(Dispatchers.Main) {
            if (success) {
                viewModel.updateTrackingState(false)
                stopLocationTrackingService()
                Toast.makeText(activity, "Tracking stopped.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Geofences removed successfully.")
            } else {
                Toast.makeText(activity, "Failed to stop tracking.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Failed to remove geofences.")
            }
        }
    }

    private fun stopLocationTrackingService() {
        val serviceIntent = Intent(activity, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        activity.stopService(serviceIntent)
    }
}