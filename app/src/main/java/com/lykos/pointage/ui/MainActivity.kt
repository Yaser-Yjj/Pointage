package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.os.Looper
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
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
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.viewmodel.MainViewModel
import kotlinx.coroutines.*
import android.os.Handler

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        private const val TAG = "MainActivity" // Added TAG for consistent logging
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

    private lateinit var tvStatus: TextView
    private lateinit var preferencesManager: PreferencesManager
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var startTime: Long = 0
    private var isTimerRunning = false

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

        viewModel.updateTrackingState(false)

        viewModel.updateInsideGeofenceState()

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
        navigateToPage()

        initViews()
        initPreferences()
        updateTimerDisplay() // Initial call to set up timer display based on current state
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun initPreferences() {
        preferencesManager = PreferencesManager(this)
    }

    private fun updateTimerDisplay() {
        val isOutsideZone = preferencesManager.getState() // true = outside, false = inside
        Log.d(TAG, "updateTimerDisplay called. isOutsideZone: $isOutsideZone")

        if (!isOutsideZone) { // User is inside zone
            startTimer()
        } else { // User is outside zone
            stopTimer()
        }
    }

    private fun startTimer() {
        // Always update startTime from preferences when starting/resuming the timer display
        startTime = preferencesManager.getLastEnterTimestamp()
        Log.d(TAG, "startTimer called. Retrieved lastEnterTimestamp from preferences: $startTime")

        if (startTime == 0L) {
            // Fallback: if no entry timestamp is recorded, use current time.
            // This should ideally be set by the service's initial check.
            startTime = System.currentTimeMillis()
            preferencesManager.saveLastEnterTimestamp(startTime) // Persist this for consistency
            Log.w(TAG, "startTime was 0L, initialized to current time: $startTime. This might indicate a missed initial enter event or app restart without prior entry.")
        }

        // Only create and post the runnable if it's not already running
        if (!isTimerRunning) {
            isTimerRunning = true
            tvStatus.setBackgroundResource(R.drawable.bg_status_banner_active)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white))

            timerHandler = Handler(Looper.getMainLooper())
            timerRunnable = object : Runnable {
                override fun run() {
                    if (isTimerRunning) {
                        val elapsedTime = System.currentTimeMillis() - startTime
                        val formattedTime = formatTime(elapsedTime)
                        tvStatus.text = "⏱️ Timer $formattedTime"
                        timerHandler?.postDelayed(this, 1000)
                    }
                }
            }
            timerHandler?.post(timerRunnable!!)
            Log.d(TAG, "New timer runnable posted.")
        } else {
            Log.d(TAG, "Timer runnable already active.")
        }
    }

    private fun stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false
            timerHandler?.removeCallbacks(timerRunnable!!)
            timerHandler = null
            timerRunnable = null

            tvStatus.setBackgroundResource(R.drawable.bg_status_banner)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
            tvStatus.text = "🔴 Outside Zone"
            Log.d(TAG, "Timer stopped.")
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                geofenceStateReceiver,
                IntentFilter("com.lykos.pointage.GEOFENCE_STATE_CHANGED"),
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION") registerReceiver(
                geofenceStateReceiver, IntentFilter("com.lykos.pointage.GEOFENCE_STATE_CHANGED")
            )
        }

        viewModel.updateInsideGeofenceState()
        updateTimerDisplay() // Ensure timer state is correct on activity start/resume
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(geofenceStateReceiver)
    }

    private fun navigateToPage() {
        // Consolidated redundant listeners for btnRapport
        binding.btnRapport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        // If btnPV, btnIntegrate, btnImages have other functionalities,
        // their listeners should be defined separately.
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
        binding.btnRapport.setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.geofenceData.observe(this) { geofenceData ->
            geofenceData?.let {
                geofenceRadius = it.radius
                if (::googleMap.isInitialized) {
                    val location = LatLng(it.latitude, it.longitude)

                    val isInside = viewModel.isInsideGeofence.value ?: true

                    showGeofenceOnMap(location, geofenceRadius, isInside)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))

                    Log.d(TAG, "viewModel.isTracking = ${viewModel.isTracking.value}")

                    if (viewModel.isTracking.value != true) {
                        Log.d(TAG, "Tracking is NOT active, starting geofence tracking")
                        startGeofenceTracking()
                    } else {
                        Log.d(TAG, "Tracking already active, will NOT start again")
                    }
                }
            }
        }

        viewModel.isInsideGeofence.observe(this) { isInside ->
            if (::googleMap.isInitialized && geofenceCircle != null) {
                updateGeofenceCircleColor(isInside)
            }
            updateTimerDisplay() // Trigger timer update when geofence state changes
        }
    }

    private val geofenceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Geofence state change broadcast received.")
            viewModel.updateInsideGeofenceState()
            // updateTimerDisplay() is now called by the viewModel.isInsideGeofence.observe
            // to avoid redundant calls and ensure state consistency.
        }
    }

    private fun updateGeofenceCircleColor(isInside: Boolean) {
        geofenceCircle?.let { circle ->
            val strokeColor = if (isInside) {
                ContextCompat.getColor(this, R.color.green_stroke)
            } else {
                ContextCompat.getColor(this, R.color.red_stroke)
            }
            val fillColor = if (isInside) {
                ContextCompat.getColor(this, R.color.green_fill)
            } else {
                ContextCompat.getColor(this, R.color.red_fill)
            }

            circle.strokeColor = strokeColor
            circle.fillColor = fillColor
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        enableMyLocationIfPermitted()
        viewModel.updateInsideGeofenceState()

        // Disable map interaction as per user request
        googleMap.setOnMapClickListener(null)
        googleMap.setOnMapLongClickListener(null)
        googleMap.uiSettings.isScrollGesturesEnabled = false
        googleMap.uiSettings.isZoomGesturesEnabled = false
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isTiltGesturesEnabled = false

        fetchSafeZone(userId = "68213301-7130-11f0-a8f7-a4bf012d9bf2")
    }


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
                    } else {
                        withContext(Dispatchers.Main) {
                            Log.e(TAG, "Fetched safe zone data is invalid or null.")
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to load safe zone from server. Geofence will not be active.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e(
                            TAG,
                            "Server Error: ${response.code()} - ${response.body()?.message}"
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to load safe zone from server. Geofence will not be active.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Connection error", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Network error loading safe zone. Geofence will not be active.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showGeofenceOnMap(location: LatLng, radius: Float, isInside: Boolean) {
        googleMap.clear()
        val strokeColor = if (isInside) {
            ContextCompat.getColor(this, R.color.green_stroke)
        } else {
            ContextCompat.getColor(this, R.color.red_stroke)
        }
        val fillColor = if (isInside) {
            ContextCompat.getColor(this, R.color.green_fill)
        } else {
            ContextCompat.getColor(this, R.color.red_fill)
        }

        geofenceCircle = googleMap.addCircle(
            CircleOptions().center(location).radius(radius.toDouble()).strokeColor(strokeColor)
                .fillColor(fillColor).strokeWidth(3f)
        )
    }


    private fun startGeofenceTracking() {
        Log.d(TAG, "startGeofenceTracking() called")
        Log.d(TAG, "viewModel.isTracking = ${viewModel.isTracking.value}")

        val geofenceData = viewModel.geofenceData.value
        if (geofenceData == null || (geofenceData.latitude == 0.0 && geofenceData.longitude == 0.0)) {
            Log.w(TAG, "Geofence data is invalid or not set. Cannot start tracking.")
            Toast.makeText(
                this, "Safe zone location not set. Cannot start tracking.", Toast.LENGTH_LONG
            ).show()
            return
        }

        if (viewModel.isTracking.value == true) {
            Log.i(TAG, "Tracking is already active. Aborting start.")
            Toast.makeText(this, "Tracking is already active.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(
            TAG,
            "Creating geofence with lat=${geofenceData.latitude}, lng=${geofenceData.longitude}, radius=${geofenceData.radius}"
        )
        geofenceManager.createGeofence(
            latitude = geofenceData.latitude,
            longitude = geofenceData.longitude,
            radius = geofenceData.radius,
            onSuccess = {
                Log.i(TAG, "Geofence created successfully.")
                viewModel.updateGeofenceActiveState(true)
                viewModel.updateTrackingState(true)

                val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START_TRACKING
                }
                Log.d(TAG, "Starting LocationTrackingService with ACTION_START_TRACKING")
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Tracking started successfully!", Toast.LENGTH_SHORT).show()

                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Permission granted, fetching last location for initial check.")
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            Log.d(
                                TAG,
                                "Last location found: lat=${it.latitude}, lng=${it.longitude}"
                            )
                            val checkIntent =
                                Intent(this, LocationTrackingService::class.java).apply {
                                    action = LocationTrackingService.ACTION_CHECK_INITIAL_LOCATION
                                    putExtra(LocationTrackingService.EXTRA_LATITUDE, it.latitude)
                                    putExtra(LocationTrackingService.EXTRA_LONGITUDE, it.longitude)
                                }
                            ContextCompat.startForegroundService(this, checkIntent)
                        } ?: run {
                            Log.w(TAG, "Last location is null for initial check.")
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get last location for initial check: ${e.message}")
                    }
                } else {
                    Log.w(
                        TAG,
                        "Fine location permission not granted, skipping initial location check."
                    )
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Geofence creation failed: $error")
                Toast.makeText(this, "Failed to start tracking: $error", Toast.LENGTH_LONG).show()
                viewModel.updateGeofenceActiveState(false)
                viewModel.updateTrackingState(false)
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

    override fun onDestroy() {
        super.onDestroy()
    }
}
