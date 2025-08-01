package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.lykos.pointage.R
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.service.LocationTrackingService
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.viewmodel.MainViewModel

/**
 * Main activity with Google Maps integration
 * Allows users to select safe zone location and configure geofence
 * Handles permissions, map interactions, and geofence management
 */
class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 15f
        private const val MIN_RADIUS = 1f
        private const val MAX_RADIUS = 500f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var locationManager: LocationManager
    private val viewModel: MainViewModel by viewModels()

    // Current geofence configuration
    private var selectedLocation: LatLng? = null
    private var geofenceRadius: Float = 100f
    private var geofenceCircle: Circle? = null

    // Permission launchers
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

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>


    /**
     * Requests background location permission (Android 10+)
     */
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showBackgroundLocationDialog {
                backgroundLocationPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkLocationPermissions()
        } else {
            Toast.makeText(
                this, "Notification permission recommended for alerts", Toast.LENGTH_LONG
            ).show()
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

        // Setup UI
        setupToolbar()
        setupMap()
        setupRadiusSeekBar()
        setupButtons()
        observeViewModel()

        // Request notification permission first (Android 13+)
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

    private fun navigateToReportPage(){
        binding.btnAddReport.setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setMapGesturesEnabled(enabled: Boolean) {
        googleMap.uiSettings.isScrollGesturesEnabled = enabled
        googleMap.uiSettings.isZoomGesturesEnabled = enabled
        googleMap.uiSettings.isRotateGesturesEnabled = enabled
        googleMap.uiSettings.isTiltGesturesEnabled = enabled
    }

    private fun setupButtonSheet() {
        val bottomSheet = findViewById<FrameLayout>(R.id.bottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.peekHeight = 250
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (!::googleMap.isInitialized) return

                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        setMapGesturesEnabled(false)
                        binding.tvStatus.text = "Adjust your settings or start tracking"
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        setMapGesturesEnabled(true)
                        updateButtonStates()
                    }

                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        setMapGesturesEnabled(false)
                        binding.tvStatus.text = "Half expanded - Adjust radius or settings"
                    }

                    BottomSheetBehavior.STATE_HIDDEN -> {
                        setMapGesturesEnabled(true)
                        binding.tvStatus.text = "Tap on map to select safe zone center"
                    }

                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val dimAmount = 0.5f * slideOffset.coerceIn(0f, 1f)
                binding.map.alpha = 1f - dimAmount
            }
        })
    }


    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Safe Zone Setup"
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    @SuppressLint("SetTextI18n")
    private fun setupRadiusSeekBar() {
        binding.seekBarRadius.max = (MAX_RADIUS - MIN_RADIUS).toInt()
        binding.seekBarRadius.progress = (geofenceRadius - MIN_RADIUS).toInt()
        binding.tvRadiusValue.text = "${geofenceRadius.toInt()}m"

        binding.seekBarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    geofenceRadius = MIN_RADIUS + progress
                    binding.tvRadiusValue.text = "${geofenceRadius.toInt()}m"
                    updateGeofenceCircle()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        binding.btnConfirmLocation.setOnClickListener { confirmGeofenceLocation() }
        binding.btnStartTracking.setOnClickListener { startGeofenceTracking() }
        binding.btnStopTracking.setOnClickListener { stopGeofenceTracking() }
        binding.btnCurrentLocation.setOnClickListener { moveToCurrentLocation() }
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.geofenceData.observe(this) { geofenceData ->
            geofenceData?.let {
                selectedLocation = LatLng(it.latitude, it.longitude)
                geofenceRadius = it.radius
                binding.seekBarRadius.progress = (geofenceRadius - MIN_RADIUS).toInt()
                binding.tvRadiusValue.text = "${geofenceRadius.toInt()}m"
                if (::googleMap.isInitialized) {
                    showGeofenceOnMap(selectedLocation!!, geofenceRadius)
                }
                updateButtonStates()
            }
        }

        viewModel.isTracking.observe(this) { isTracking ->
            updateButtonStates()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configure map UI
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        googleMap.setOnMapClickListener { latLng ->
            if (viewModel.isTracking.value == true) {
                Toast.makeText(this, "Stop tracking to change location", Toast.LENGTH_SHORT).show()
            } else {
                selectLocation(latLng)
            }
        }

        enableMyLocationIfPermitted()

        viewModel.geofenceData.value?.let { geofenceData ->
            val location = LatLng(geofenceData.latitude, geofenceData.longitude)
            showGeofenceOnMap(location, geofenceData.radius)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
        } ?: run {
            moveToCurrentLocation()
        }
    }

    private fun selectLocation(latLng: LatLng) {
        selectedLocation = latLng
        showGeofenceOnMap(latLng, geofenceRadius)
        updateButtonStates()
        Toast.makeText(this, "Location selected! Adjust radius and confirm.", Toast.LENGTH_SHORT)
            .show()

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun showGeofenceOnMap(location: LatLng, radius: Float) {
        geofenceCircle?.remove()
        googleMap.clear()

        googleMap.addMarker(
            MarkerOptions().position(location).title("Safe Zone Center")
        )

        geofenceCircle = googleMap.addCircle(
            CircleOptions().center(location).radius(radius.toDouble())
                .strokeColor(ContextCompat.getColor(this, R.color.geofence_stroke))
                .fillColor(ContextCompat.getColor(this, R.color.geofence_fill)).strokeWidth(3f)
        )

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
    }

    private fun updateGeofenceCircle() {
        selectedLocation?.let { location ->
            geofenceCircle?.radius = geofenceRadius.toDouble()
        }
    }

    private fun confirmGeofenceLocation() {
        val location = selectedLocation
        if (location == null) {
            Toast.makeText(this, "Please select a location on the map first", Toast.LENGTH_SHORT)
                .show()
            return
        }
        viewModel.saveGeofenceData(location.latitude, location.longitude, geofenceRadius)
        Toast.makeText(this, "Safe zone location confirmed!", Toast.LENGTH_SHORT).show()
        binding.btnConfirmLocation.isEnabled = false
        setOutlinedButtonColors(
            binding.btnConfirmLocation,
            R.color.geofence_stroke,
            R.color.geofence_stroke,
            R.color.geofence_stroke
        )
        binding.btnConfirmLocation.text = "Confirmed"
    }

    private fun startGeofenceTracking() {
        val geofenceData = viewModel.geofenceData.value
        if (geofenceData == null) {
            Toast.makeText(this, "Please confirm a location first", Toast.LENGTH_SHORT).show()
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

                Toast.makeText(this, "✅ Safe zone tracking started!", Toast.LENGTH_SHORT).show()
                updateButtonStates()
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to start tracking: $error", Toast.LENGTH_LONG).show()
            })
    }

    private fun stopGeofenceTracking() {
        geofenceManager.removeGeofences { success ->
            if (!success) {
                Toast.makeText(this, "Failed to stop geofence", Toast.LENGTH_SHORT).show()
            }
        }

        val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_STOP_TRACKING
        }
        startService(serviceIntent)

        viewModel.updateGeofenceActiveState(false)
        viewModel.updateTrackingState(false)
        Toast.makeText(this, "Safe zone tracking stopped", Toast.LENGTH_SHORT).show()
        updateButtonStates()
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM))
                Toast.makeText(this, "Tap on map to select safe zone center", Toast.LENGTH_LONG)
                    .show()
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get current location", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ✅ Safe: Only called after map is ready
     */
    private fun enableMyLocationIfPermitted() {
        if (::googleMap.isInitialized && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }

    private fun updateButtonStates() {
        val hasSelectedLocation = selectedLocation != null
        val hasConfirmedLocation = viewModel.geofenceData.value != null
        val isTracking = viewModel.isTracking.value == true

        // --- Confirm Location Button ---
        when {
            isTracking -> {
                binding.btnConfirmLocation.isEnabled = false
                setOutlinedButtonColors(
                    binding.btnConfirmLocation,
                    R.color.gris,
                    R.color.gris,
                    R.color.gris
                )
                binding.btnConfirmLocation.text = "Location Locked"
            }

            hasSelectedLocation -> {
                binding.btnConfirmLocation.isEnabled = true
                setOutlinedButtonColors(
                    binding.btnConfirmLocation,
                    R.color.geofence_stroke,
                    R.color.geofence_stroke,
                    R.color.geofence_stroke
                )
                binding.btnConfirmLocation.text = "Confirm Location"
            }

            hasConfirmedLocation -> {
                binding.btnConfirmLocation.isEnabled = true
                setOutlinedButtonColors(
                    binding.btnConfirmLocation,
                    R.color.geofence_stroke,
                    R.color.geofence_stroke,
                    R.color.geofence_stroke
                )
                binding.btnConfirmLocation.text = "Confirmed"
            }

            else -> {
                binding.btnConfirmLocation.isEnabled = false
                setOutlinedButtonColors(
                    binding.btnConfirmLocation,
                    R.color.gris,
                    R.color.gris,
                    R.color.gris
                )
                binding.btnConfirmLocation.text = "Select Location First"
            }
        }


        // --- Start Tracking Button ---
        if (isTracking) {
            binding.btnStartTracking.isEnabled = false
            binding.btnStartTracking.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.rouge)
            binding.btnStartTracking.strokeColor =
                ContextCompat.getColorStateList(this, R.color.rouge)
            binding.btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.blanc))
            binding.btnStartTracking.text = "Tracking..."
        } else if (hasConfirmedLocation) {
            binding.btnStartTracking.isEnabled = true
            binding.btnStartTracking.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.geofence_stroke)
            binding.btnStartTracking.strokeColor =
                ContextCompat.getColorStateList(this, R.color.geofence_stroke)
            binding.btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.blanc))
            binding.btnStartTracking.text = "Start Tracking"
        } else {
            binding.btnStartTracking.isEnabled = false
            binding.btnStartTracking.backgroundTintList =
                ContextCompat.getColorStateList(this, R.color.gris)
            binding.btnStartTracking.strokeColor =
                ContextCompat.getColorStateList(this, R.color.gris)
            binding.btnStartTracking.setTextColor(ContextCompat.getColor(this, R.color.blanc))
            binding.btnStartTracking.text = "Confirm First"
        }

        // --- Stop Tracking Button ---
        binding.btnStopTracking.isEnabled = isTracking
        if (isTracking) {
            setOutlinedButtonColors(
                binding.btnStopTracking,
                R.color.rouge,
                R.color.rouge,
                R.color.rouge
            )
            binding.btnStopTracking.text = "Stop Tracking"
        } else {
            setOutlinedButtonColors(
                binding.btnStopTracking,
                R.color.gris,
                R.color.gris,
                R.color.gris
            )
            binding.btnStopTracking.text = "Not Tracking"
        }

        // --- Map Gestures ---
        if (::googleMap.isInitialized) {
            googleMap.uiSettings.isScrollGesturesEnabled = !isTracking
            googleMap.uiSettings.isZoomGesturesEnabled = !isTracking
            googleMap.uiSettings.isRotateGesturesEnabled = !isTracking
            googleMap.uiSettings.isTiltGesturesEnabled = !isTracking
        }

        // --- Status Text ---
        binding.tvStatus.text = when {
            isTracking -> "Safe zone tracking is ACTIVE"
            hasConfirmedLocation -> "Safe zone configured - Ready to start tracking"
            hasSelectedLocation -> "Location selected - Confirm to save"
            else -> "Tap on map to select safe zone center"
        }
    }

    private fun setOutlinedButtonColors(
        button: MaterialButton,
        @ColorRes strokeColor: Int,
        @ColorRes textColor: Int,
        @ColorRes rippleColor: Int
    ) {
        button.strokeColor = ContextCompat.getColorStateList(this, strokeColor)
        button.setTextColor(ContextCompat.getColorStateList(this, textColor))
        button.rippleColor = ContextCompat.getColorStateList(this, rippleColor)
    }

    // --- Permission Handling ---

    private fun checkLocationPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted && coarseLocationGranted) {
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
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (backgroundGranted) {
                checkLocationSettings()
            } else {
                requestBackgroundLocationPermission()
            }
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
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted && coarseLocationGranted) {
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
        // This is safe now because map is already ready
        enableMyLocationIfPermitted()
        Toast.makeText(
            this, "✅ All permissions granted! You can now set up geofencing.", Toast.LENGTH_LONG
        ).show()
    }

    // --- Dialogs ---

    private fun showLocationPermissionDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Location Permission Required")
            .setMessage("This app needs location access to create and monitor your safe zone. Location data is only used for geofencing and is not shared.")
            .setPositiveButton("Grant Permission") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showBackgroundLocationDialog(onPositive: (() -> Unit)? = null) {
        AlertDialog.Builder(this).setTitle("Background Location Required")
            .setMessage("To track when you leave and return to your safe zone, this app needs background location access. Please select 'Allow all the time' in the next screen.")
            .setPositiveButton("Continue") { _, _ ->
                onPositive?.invoke() ?: openAppSettings()
            }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showLocationSettingsDialog() {
        AlertDialog.Builder(this).setTitle("Location Services Disabled")
            .setMessage("Please enable location services (GPS) for accurate geofencing. High accuracy mode is recommended.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this).setTitle("Permission Required")
            .setMessage("Location permission is required for this app to work. Please grant the permission in app settings.")
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_history -> {
                showHistoryDialog()
                true
            }

            R.id.action_clear_data -> {
                showClearDataDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showHistoryDialog() {
        viewModel.locationEvents.value?.let { events ->
            if (events.isEmpty()) {
                Toast.makeText(this, "No location events recorded yet", Toast.LENGTH_SHORT).show()
                return
            }
            val historyText = StringBuilder()
            historyText.append("Recent Location Events:\n")
            events.take(5).forEach { event ->
                val exitTime = event.exitTime?.toString() ?: "Unknown"
                val enterTime = event.enterTime?.toString() ?: "Still away"
                val duration = if (event.totalTimeAway > 0) {
                    "${event.totalTimeAway / (1000 * 60)} minutes"
                } else {
                    "In progress"
                }
                historyText.append("Exit: $exitTime\n")
                historyText.append("Enter: $enterTime\n")
                historyText.append("Duration: $duration\n\n")
            }
            AlertDialog.Builder(this).setTitle("Location History")
                .setMessage(historyText.toString())
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }.show()
        }
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this).setTitle("Clear All Data")
            .setMessage("This will delete all location events and reset the app. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearAllData()
                googleMap.clear()
                geofenceCircle = null
                selectedLocation = null
                updateButtonStates()
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
        updateButtonStates()
    }
}