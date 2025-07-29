package com.lykos.pointage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.service.BackgroundLocationService
import com.lykos.pointage.utils.GeofenceManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var locationManager: LocationManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted && coarseLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocationPermission()
            } else {
                checkLocationSettings()
            }
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkLocationSettings()
        } else {
            showBackgroundLocationDialog()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Continue regardless of notification permission
        checkLocationPermissions()
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if location is now enabled
        if (isLocationEnabled()) {
            onAllPermissionsGranted()
        } else {
            showLocationDisabledDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geofenceManager = GeofenceManager(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        binding.btnStartTracking.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnStopTracking.setOnClickListener {
            stopLocationTracking()
        }

        binding.btnOpenSettings.setOnClickListener {
            openAppSettings()
        }

        updateUI()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkLocationPermissions()
    }

    private fun checkLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted && coarseLocationGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val backgroundLocationGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (backgroundLocationGranted) {
                    checkLocationSettings()
                } else {
                    requestBackgroundLocationPermission()
                }
            } else {
                checkLocationSettings()
            }
        } else {
            requestLocationPermissions()
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

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showBackgroundLocationDialog {
                backgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        }
    }

    private fun showLocationPermissionDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location access to track when you enter or exit the designated area. This helps monitor your presence in the zone.")
            .setPositiveButton("Grant Permission") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showBackgroundLocationDialog(onPositive: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle("Background Location Required")
            .setMessage("To track your location even when the app is closed, we need background location access. Please select 'Allow all the time' in the next screen.")
            .setPositiveButton("Continue") { _, _ ->
                onPositive?.invoke() ?: openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Services Disabled")
            .setMessage("Please enable location services (GPS) for geofencing to work properly. High accuracy mode is recommended.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                locationSettingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun showLocationDisabledDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Still Disabled")
            .setMessage("Location services are still disabled. The app may not work properly without location access.")
            .setPositiveButton("Try Again") { _, _ -> checkLocationSettings() }
            .setNegativeButton("Continue Anyway") { _, _ -> onAllPermissionsGranted() }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Location permission is required for this app to work. Please grant the permission in app settings.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun onAllPermissionsGranted() {
        Toast.makeText(this, "Starting location tracking...", Toast.LENGTH_SHORT).show()
        startLocationTracking()
        updateUI()
    }

    private fun startLocationTracking() {
        // Start the background service
        val serviceIntent = Intent(this, BackgroundLocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // Setup geofences (will be handled by the service)
        // geofenceManager.setupGeofences() - This is now called from the service
    }

    private fun stopLocationTracking() {
        // Stop the background service
        val serviceIntent = Intent(this, BackgroundLocationService::class.java)
        stopService(serviceIntent)

        // Remove geofences
        geofenceManager.removeGeofences()

        Toast.makeText(this, "Location tracking stopped", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun updateUI() {
        val isServiceRunning = BackgroundLocationService.isServiceRunning
        binding.btnStartTracking.isEnabled = !isServiceRunning
        binding.btnStopTracking.isEnabled = isServiceRunning
        binding.tvStatus.text = if (isServiceRunning) {
            "Location tracking is active"
        } else {
            "Location tracking is stopped"
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
