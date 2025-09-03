package com.lykos.pointage.ui.controllers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Toast

class PermissionManager(
    private val activity: AppCompatActivity,
    private val onAllPermissionsGranted: () -> Unit
) {

    private val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestLocationPermissions()
            } else {
                Toast.makeText(activity, "Notification permission recommended", Toast.LENGTH_LONG).show()
                requestLocationPermissions()
            }
        }

    private val locationPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            handleLocationPermissionResult(permissions)
        }

    private val backgroundLocationPermissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkLocationSettings()
            } else {
                showBackgroundLocationDialog()
            }
        }

    fun requestAllPermissions() {
        when {
            shouldRequestNotificationPermission() -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            !hasLocationPermissions() -> {
                requestLocationPermissions()
            }
            shouldRequestBackgroundLocation() -> {
                showBackgroundLocationDialog {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            !isLocationEnabled() -> {
                showLocationSettingsDialog()
            }
            else -> {
                onAllPermissionsGranted()
            }
        }
    }

    private fun shouldRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermissions(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted && coarseGranted
    }

    private fun shouldRequestBackgroundLocation(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
    }

    private fun handleLocationPermissionResult(permissions: Map<String, Boolean>) {
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fine && coarse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkBackgroundLocationPermission()
            } else {
                checkLocationSettings()
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                checkLocationSettings()
            } else {
                showBackgroundLocationDialog {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            checkLocationSettings()
        }
    }

    private fun checkLocationSettings() {
        if (isLocationEnabled()) {
            onAllPermissionsGranted()
        } else {
            showLocationSettingsDialog()
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun requestLocationPermissions() {
        showLocationPermissionDialog {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Dialog Methods
    private fun showLocationPermissionDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Location Access Required")
            .setMessage("This app needs location access to monitor your safe zone.")
            .setPositiveButton("Grant") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showBackgroundLocationDialog(onPositive: (() -> Unit)? = null) {
        AlertDialog.Builder(activity)
            .setTitle("Background Location Needed")
            .setMessage("Please allow 'Allow all the time' for accurate tracking.")
            .setPositiveButton("Continue") { _, _ -> onPositive?.invoke() ?: openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Location Services Disabled")
            .setMessage("Please enable GPS for accurate tracking.")
            .setPositiveButton("Open Settings") { _, _ ->
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage("Please grant location permission in settings.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            activity.startActivity(this)
        }
    }
}