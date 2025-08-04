package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lykos.pointage.R
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.service.LocationTrackingService
import com.lykos.pointage.service.RetrofitClient
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.viewmodel.MainViewModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val DEFAULT_ZOOM = 15f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var locationManager: LocationManager
    private val viewModel: MainViewModel by viewModels()

    private var geofenceCircle: Circle? = null
    private var geofenceRadius: Float = 100f

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handleLocationPermissionResult(permissions)
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
        if (granted) {
            checkLocationPermissions()
        } else {
            Toast.makeText(this, "Notification permission recommended", Toast.LENGTH_LONG).show()
            checkLocationPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofenceManager = GeofenceManager(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        setupToolbar()
        setupMap()
        setupButtons()
        observeViewModel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkLocationPermissions()
            }
        } else {
            checkLocationPermissions()
        }

        setupButtonSheet()
        navigateToReportPage()
    }

    private fun navigateToReportPage() {
        binding.btnAddReport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    private fun setupButtonSheet() {
        val bottomSheet = findViewById<FrameLayout>(R.id.bottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 150
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Safe Zone Tracking"
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupButtons() {
        binding.btnCurrentLocation.setOnClickListener { moveToCurrentLocation() }

//        binding.btnStartTracking.setOnClickListener {
////            startGeofenceTracking()
////        }
////
////        binding.btnStopTracking.setOnClickListener {
////            geofenceManager.removeGeofences { success ->
////                if (success) {
////                    Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show()
////                    viewModel.updateGeofenceActiveState(false)
////                    viewModel.updateTrackingState(false)
////                    val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
////                        action = LocationTrackingService.ACTION_STOP_TRACKING
////                    }
////                    stopService(serviceIntent)
////                    updateButtonStates()
////                    googleMap.clear() // Clear the geofence circle
////                } else {
////                    Toast.makeText(this, "Failed to stop tracking", Toast.LENGTH_SHORT).show()
////                }
////            }
////        }
        updateButtonStates() // Initial update of button states
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.geofenceData.observe(this) { geofenceData ->
            geofenceData?.let {
                geofenceRadius = it.radius
                if (::googleMap.isInitialized) {
                    val location = LatLng(it.latitude, it.longitude)
                    showGeofenceOnMap(location, geofenceRadius)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))

                    // Automatically start tracking if geofence is configured and not already active
                    Log.i(
                        "logg",
                        "geofence.isActive = ${it.isActive}, isTracking = ${viewModel.isTracking.value}"
                    )
                    if (viewModel.isTracking.value != true) {
                        Log.i(
                            "logg",
                            "observeViewModel: Automatically start tracking if geofence is configured and not already active"
                        )
                        startGeofenceTracking()
                    }

                }
            }
            updateButtonStates()
        }

        viewModel.isTracking.observe(this) {
            updateButtonStates()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        enableMyLocationIfPermitted()

        // Disable map interaction as per user request
        googleMap.setOnMapClickListener(null)
        googleMap.setOnMapLongClickListener(null)
        googleMap.uiSettings.isScrollGesturesEnabled = false
        googleMap.uiSettings.isZoomGesturesEnabled = false
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isTiltGesturesEnabled = false

        fetchSafeZone(userId = "68213301-7130-11f0-a8f7-a4bf012d9bf2")
    }


    @SuppressLint("SetTextI18n")
    fun fetchSafeZone(userId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getSafeZone(userId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val zone = response.body()?.data
                    if (zone != null && zone.latitude != 0.0 && zone.longitude != 0.0) {
                        viewModel.saveGeofenceData(
                            latitude = zone.latitude,
                            longitude = zone.longitude,
                            radius = zone.radius
                        )
                        withContext(Dispatchers.Main) {
                            binding.tvStatus.text = "Safe zone loaded from server. Ready to track."
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Log.e("SafeZone", "Fetched safe zone data is invalid or null.")
                            Toast.makeText(this@MainActivity, "Failed to load safe zone from server. Geofence will not be active.", Toast.LENGTH_LONG).show()
                            binding.tvStatus.text = "❌ Safe zone not configured."
                            updateButtonStates()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("SafeZone", "Server Error: ${response.code()} - ${response.body()?.message}")
                        Toast.makeText(this@MainActivity, "Failed to load safe zone from server. Geofence will not be active.", Toast.LENGTH_LONG).show()
                        binding.tvStatus.text = "❌ Safe zone not configured."
                        updateButtonStates()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("SafeZone", "Connection error", e)
                    Toast.makeText(this@MainActivity, "Network error loading safe zone. Geofence will not be active.", Toast.LENGTH_LONG).show()
                    binding.tvStatus.text = "❌ Safe zone not configured."
                    updateButtonStates()
                }
            }
        }
    }

    private fun showGeofenceOnMap(location: LatLng, radius: Float) {
        googleMap.clear() // Clear existing circles/markers
        geofenceCircle = googleMap.addCircle(
            CircleOptions().center(location).radius(radius.toDouble())
                .strokeColor(ContextCompat.getColor(this, R.color.geofence_stroke))
                .fillColor(ContextCompat.getColor(this, R.color.geofence_fill)).strokeWidth(3f)
        )
    }

    private fun startGeofenceTracking() {
        val geofenceData = viewModel.geofenceData.value
        if (geofenceData == null || (geofenceData.latitude == 0.0 && geofenceData.longitude == 0.0)) {
            Toast.makeText(this, "Safe zone location not set. Cannot start tracking.", Toast.LENGTH_LONG).show()
            return
        }

        if (viewModel.isTracking.value == true) {
            Toast.makeText(this, "Tracking is already active.", Toast.LENGTH_SHORT).show()
            return
        }

        geofenceManager.createGeofence(
            latitude = geofenceData.latitude,
            longitude = geofenceData.longitude,
            radius = geofenceData.radius,
            onSuccess = {
                viewModel.updateGeofenceActiveState(true)
                viewModel.updateTrackingState(true)

                val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START_TRACKING
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                updateButtonStates()
                Toast.makeText(this, "Tracking started successfully!", Toast.LENGTH_SHORT).show()

                // --- NEW: Perform initial location check after geofence is set ---
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val checkIntent = Intent(this, LocationTrackingService::class.java).apply {
                                action = LocationTrackingService.ACTION_CHECK_INITIAL_LOCATION
                                putExtra("latitude", it.latitude)
                                putExtra("longitude", it.longitude)
                            }
                            ContextCompat.startForegroundService(this, checkIntent)
                        } ?: run {
                            Log.w("MainActivity", "Last location is null for initial check.")
                        }
                    }.addOnFailureListener { e ->
                        Log.e("MainActivity", "Failed to get last location for initial check: ${e.message}")
                    }
                }
                // --- END NEW ---

            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to start tracking: $error", Toast.LENGTH_LONG).show()
                Log.e("MainActivity", "Geofence creation failed: $error")
                viewModel.updateGeofenceActiveState(false) // Ensure state is correct on failure
                viewModel.updateTrackingState(false)
                updateButtonStates()
            })
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM))
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get current location", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateButtonStates() {
        val isTrackingActive = viewModel.isTracking.value == true
        val geofenceConfigured = viewModel.geofenceData.value != null &&
                (viewModel.geofenceData.value?.latitude != 0.0 || viewModel.geofenceData.value?.longitude != 0.0)

        binding.btnStartTracking.isEnabled = geofenceConfigured && !isTrackingActive
        binding.btnStopTracking.isEnabled = isTrackingActive

        if (isTrackingActive) {
            binding.btnStartTracking.text = "Tracking..."
            binding.tvStatus.text = "Safe zone tracking is ACTIVE"
        } else if (geofenceConfigured) {
            binding.btnStartTracking.text = "Start Tracking"
            binding.tvStatus.text = "Safe zone configured. Ready to track."
        } else {
            binding.btnStartTracking.text = "Start Tracking"
            binding.tvStatus.text = "❌ Safe zone not configured."
        }
    }

    // --- Permission Handling ---

    private fun checkLocationPermissions() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine && coarse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkBackgroundLocationPermission()
            } else {
                checkLocationSettings()
            }
        } else {
            requestLocationPermissions()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
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

    private fun checkLocationSettings() {
        if (isLocationEnabled()) {
            onAllPermissionsGranted()
        } else {
            showLocationSettingsDialog()
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun onAllPermissionsGranted() {
        enableMyLocationIfPermitted()
        Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show()
    }

    private fun enableMyLocationIfPermitted() {
        if (::googleMap.isInitialized && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }

    // --- Dialogs ---

    private fun showLocationPermissionDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Location Permission Required")
            .setMessage("This app needs location access to monitor your safe zone.")
            .setPositiveButton("Grant") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showBackgroundLocationDialog(onPositive: (() -> Unit)? = null) {
        AlertDialog.Builder(this).setTitle("Background Location Needed")
            .setMessage("Please allow 'Allow all the time' for accurate tracking.")
            .setPositiveButton("Continue") { _, _ -> onPositive?.invoke() ?: openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this).setTitle("Location Services Disabled")
            .setMessage("Please enable GPS for accurate tracking.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this).setTitle("Permission Required")
            .setMessage("Please grant location permission in settings.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // --- Menu ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_clear_data)?.isVisible = false // إخفاء Clear Data
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_history -> {
                showHistoryDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHistoryDialog() {
        val events = viewModel.locationEvents.value?.take(5)
        if (events.isNullOrEmpty()) {
            Toast.makeText(this, "No events yet", Toast.LENGTH_SHORT).show()
            return
        }

        val history = StringBuilder("Last 5 Events:\n\n")
        events.forEach { event ->
            val exit = event.exitTime?.toString() ?: "Unknown"
            val enter = event.enterTime?.toString() ?: "Still away"
            val duration = if (event.totalTimeAway > 0) "${event.totalTimeAway / 60000} min"
            else "In progress"
            history.append("Exit: $exit\nEnter: $enter\nDuration: $duration\n\n")
        }

        AlertDialog.Builder(this).setTitle("History").setMessage(history.toString())
            .setPositiveButton("OK", null).show()
    }


    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }
}