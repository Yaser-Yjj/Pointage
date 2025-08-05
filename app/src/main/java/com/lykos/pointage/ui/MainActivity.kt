package com.lykos.pointage.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import java.util.Date
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
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.R
import com.lykos.pointage.database.GeofenceDatabase
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.service.LocationTrackingService
import com.lykos.pointage.service.RetrofitClient
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.viewmodel.MainViewModel
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_ZOOM = 15f
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var locationManager: LocationManager
    private lateinit var userID: String
    private val viewModel: MainViewModel by viewModels()
    private var geofenceCircle: Circle? = null
    private var geofenceRadius: Float = 100f
    private lateinit var tvStatus: TextView
    private lateinit var preferencesManager: PreferencesManager
    private var timerHandler: android.os.Handler? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false

    private lateinit var database: GeofenceDatabase
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestLocationPermissions()
        } else {
            Toast.makeText(this, "Notification permission recommended", Toast.LENGTH_LONG).show()
            requestLocationPermissions()
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
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

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkLocationSettings()
        } else {
            showBackgroundLocationDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize core components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofenceManager = GeofenceManager(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        database = (application as GeofenceMapApplication).database

        userID = "01987620-49fa-7398-8ce6-17b887e206dd"

        viewModel.updateTrackingState(false)
        viewModel.updateInsideGeofenceState()

        setupToolbar()
        setupMap()
        setupButtons()
        observeViewModel()
        setupButtonSheet()
        navigateToPage()
        initViews()
        initPreferences()

        requestRequiredPermissionsAndProceed()
    }

    /**
     * Request all required permissions in correct order
     */
    private fun requestRequiredPermissionsAndProceed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted || !coarseGranted) {
            requestLocationPermissions()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) {
                showBackgroundLocationDialog {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                return
            }
        }

        if (!isLocationEnabled()) {
            showLocationSettingsDialog()
            return
        }

        onAllPermissionsAndLocationAvailable()
    }

    /**
     * This is the SAFE ZONE — all permissions + location enabled
     */
    private fun onAllPermissionsAndLocationAvailable() {
        enableMyLocationIfPermitted()

        fetchSafeZone(userId = userID)
        updateTimerDisplay()

        Toast.makeText(this, "✅ All permissions granted! Ready to track.", Toast.LENGTH_SHORT)
            .show()
    }

    // MARK: - UI Setup

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun initPreferences() {
        preferencesManager = PreferencesManager(this)
    }

    private fun updateTimerDisplay() {
        val isOutsideZone = preferencesManager.getState() // true = outside, false = inside
        Log.d(TAG, "updateTimerDisplay called. isOutsideZone: $isOutsideZone")

        if (!isOutsideZone) {
            startTimer()
        } else {
            stopTimer()
        }
    }

    private fun startTimer() {
        if (isTimerRunning) return
        isTimerRunning = true

        tvStatus.setBackgroundResource(R.drawable.bg_status_banner_active)
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white))

        timerHandler = android.os.Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            @SuppressLint("SetTextI18n")
            override fun run() {
                if (isTimerRunning) {
                    val accumulatedTime = preferencesManager.getAccumulatedTimeInside()
                    val sessionStartTime = preferencesManager.getLastEnterTimestamp()
                    val currentSessionDuration = System.currentTimeMillis() - sessionStartTime
                    val totalDisplayTime = accumulatedTime + currentSessionDuration
                    val formattedTime = formatTime(totalDisplayTime)
                    tvStatus.text = "⏱️ Timer $formattedTime"
                    timerHandler?.postDelayed(this, 1000)
                }
            }
        }
        timerHandler?.post(timerRunnable!!)
        Log.d(TAG, "Timer started.")
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

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    // MARK: - Lifecycle

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.lykos.pointage.GEOFENCE_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geofenceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") registerReceiver(geofenceStateReceiver, filter)
        }
        viewModel.updateInsideGeofenceState()
        updateTimerDisplay()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(geofenceStateReceiver)
    }

    // MARK: - UI Components

    private fun navigateToPage() {
        binding.btnReportImages.setOnClickListener {
            startActivity(Intent(this, DailyReportActivity::class.java))
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

                    viewModel.isTracking.value?.let { it1 ->
                        if (!it1) {
                            startGeofenceTracking()
                        }
                    }
                }
            }
        }

        viewModel.isInsideGeofence.observe(this) { isInside ->
            if (::googleMap.isInitialized && geofenceCircle != null) {
                updateGeofenceCircleColor(isInside)
            }
            updateTimerDisplay()
        }
    }

    private val geofenceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateInsideGeofenceState()
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

        // Disable gestures
        googleMap.setOnMapClickListener(null)
        googleMap.setOnMapLongClickListener(null)
        googleMap.uiSettings.isScrollGesturesEnabled = false
        googleMap.uiSettings.isZoomGesturesEnabled = false
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isTiltGesturesEnabled = false

        enableMyLocationIfPermitted()

        if (viewModel.geofenceData.value == null) {
            fetchSafeZone(userId = userID)
        } else {
            val data = viewModel.geofenceData.value!!
            val location = LatLng(data.latitude, data.longitude)
            val isInside = viewModel.isInsideGeofence.value ?: true
            showGeofenceOnMap(location, data.radius, isInside)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
        }
    }

    // MARK: - Geofence & Location

    fun fetchSafeZone(userId: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getSafeZone(userId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val zone = response.body()?.data
                    if (zone != null && zone.latitude != 0.0 && zone.longitude != 0.0) {
                        viewModel.saveGeofenceData(zone.latitude, zone.longitude, zone.radius)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity, "Invalid safe zone data.", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity, "Failed to load safe zone.", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Network error", e)
                    Toast.makeText(this@MainActivity, "Network error.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showGeofenceOnMap(location: LatLng, radius: Float, isInside: Boolean) {
        googleMap.clear()
        val strokeColor = if (isInside) ContextCompat.getColor(this, R.color.green_stroke)
        else ContextCompat.getColor(this, R.color.red_stroke)
        val fillColor = if (isInside) ContextCompat.getColor(this, R.color.green_fill)
        else ContextCompat.getColor(this, R.color.red_fill)

        geofenceCircle = googleMap.addCircle(
            CircleOptions().center(location).radius(radius.toDouble()).strokeColor(strokeColor)
                .fillColor(fillColor).strokeWidth(3f)
        )
    }

    private fun startGeofenceTracking() {
        val geofenceData = viewModel.geofenceData.value
        if (geofenceData == null || geofenceData.latitude == 0.0) {
            Toast.makeText(this, "Safe zone not set.", Toast.LENGTH_LONG).show()
            return
        }

        if (viewModel.isTracking.value == true) {
            Log.d(TAG, "Tracking already active.")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission missing.", Toast.LENGTH_SHORT).show()
            return
        }

        geofenceManager.createGeofence(
            latitude = geofenceData.latitude,
            longitude = geofenceData.longitude,
            radius = geofenceData.radius,
            onSuccess = {
                viewModel.updateTrackingState(true)
                val serviceIntent = Intent(this, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START_TRACKING
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                Toast.makeText(this, "Tracking started!", Toast.LENGTH_SHORT).show()

                // Optional: Check initial location
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val checkIntent = Intent(this, LocationTrackingService::class.java).apply {
                            action = LocationTrackingService.ACTION_CHECK_INITIAL_LOCATION
                            putExtra(LocationTrackingService.EXTRA_LATITUDE, it.latitude)
                            putExtra(LocationTrackingService.EXTRA_LONGITUDE, it.longitude)
                        }
                        ContextCompat.startForegroundService(this, checkIntent)
                    }
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Geofence failed: $error")
                Toast.makeText(this, "Start failed: $error", Toast.LENGTH_LONG).show()
                viewModel.updateTrackingState(false)
            })
    }

    private fun moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(
                            it.latitude, it.longitude
                        ), DEFAULT_ZOOM
                    )
                )
            } ?: Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    // MARK: - Permission Helpers

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

    private fun checkLocationSettings() {
        if (isLocationEnabled()) {
            onAllPermissionsAndLocationAvailable()
        } else {
            showLocationSettingsDialog()
        }
    }

    private fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun enableMyLocationIfPermitted() {
        if (::googleMap.isInitialized && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }

    // MARK: - Dialogs

    private fun showLocationPermissionDialog(onPositive: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Location Access Required")
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
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }

    // MARK: - Menu

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_clear_data)?.isVisible = false
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
        lifecycleScope.launch {
            try {
                val dailyRecords = database.dailyInsideTimeDao().getAll().take(7)
                val events = database.locationEventDao().getRecentEvents().take(5)

                val history = StringBuilder()

                if (dailyRecords.isNotEmpty()) {
                    history.append("⏱️ Daily Time Inside Safe Zone:\n\n")
                    dailyRecords.forEach { record ->
                        val formatted = formatTime(record.totalTimeInside)
                        history.append("${record.date}: $formatted\n")
                    }
                    history.append("\n")
                }

                if (events.isNotEmpty()) {
                    history.append("🟢 Recent Sessions:\n\n")
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    events.forEach { event ->
                        val exit = event.exitTime?.let { sdf.format(it) } ?: "Unknown"
                        val enter = event.enterTime?.let { sdf.format(it) } ?: "Still away"
                        val duration = formatTime(event.totalTimeInside)
                        history.append("Exit: $exit\nEnter: $enter\nDuration: $duration\n\n")
                    }
                }

                if (history.isEmpty()) {
                    history.append("No data yet.")
                }

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity).setTitle("History")
                        .setMessage(history.toString()).setPositiveButton("OK", null).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()

        if (::googleMap.isInitialized) {
            val geofenceData = viewModel.geofenceData.value
            if (geofenceData != null) {
                val location = LatLng(geofenceData.latitude, geofenceData.longitude)
                val isInside = viewModel.isInsideGeofence.value ?: true
                showGeofenceOnMap(location, geofenceData.radius, isInside)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
