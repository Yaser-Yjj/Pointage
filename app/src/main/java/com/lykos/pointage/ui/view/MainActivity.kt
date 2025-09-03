package com.lykos.pointage.ui.view

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.R
import com.lykos.pointage.data.database.GeofenceDatabase
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.ui.controller.GeofenceMapController
import com.lykos.pointage.ui.controller.GeofenceTrackingController
import com.lykos.pointage.ui.controller.HistoryDialogController
import com.lykos.pointage.ui.controller.SafeZoneApiController
import com.lykos.pointage.ui.controller.UISetupController
import com.lykos.pointage.ui.controllers.*
import com.lykos.pointage.utils.GeofenceManager
import com.lykos.pointage.utils.PreferencesManager
import com.lykos.pointage.ui.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var userID: String
    private val viewModel: MainViewModel by viewModels()

    // Modular Controllers
    private lateinit var permissionManager: PermissionManager
    private lateinit var mapController: GeofenceMapController
    private lateinit var timerController: TimerController
    private lateinit var trackingController: GeofenceTrackingController
    private lateinit var apiController: SafeZoneApiController
    private lateinit var historyController: HistoryDialogController
    private lateinit var uiController: UISetupController

    // Core Dependencies
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: GeofenceDatabase
    private lateinit var geofenceManager: GeofenceManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val geofenceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateInsideGeofenceState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()
        initializeControllers()
        setupInitialState()

        permissionManager.requestAllPermissions()
    }

    private fun initializeDependencies() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofenceManager = GeofenceManager(this)
        preferencesManager = PreferencesManager(this)
        database = (application as GeofenceMapApplication).database
        userID = preferencesManager.getCurrentUserId().toString()
    }

    private fun initializeControllers() {
        permissionManager = PermissionManager(this) { onAllPermissionsGranted() }
        mapController = GeofenceMapController(this, geofenceManager, fusedLocationClient)
        timerController = TimerController(findViewById(R.id.tvStatus), preferencesManager)
        trackingController =
            GeofenceTrackingController(this,this  ,geofenceManager, viewModel, fusedLocationClient)
        apiController = SafeZoneApiController(this, viewModel)
        historyController = HistoryDialogController(this, database)
        uiController = UISetupController(this, binding, viewModel)
    }

    private fun setupInitialState() {
        viewModel.updateTrackingState(false)
        viewModel.updateInsideGeofenceState()

        uiController.initializeAllViews()
        setupMap()
        setupCurrentLocationButton()
        observeViewModel()
    }

    private fun onAllPermissionsGranted() {
        mapController.enableLocationIfPermitted()
        apiController.fetchSafeZones(userID)
        timerController.updateTimerDisplay()
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(mapController)
    }

    private fun setupCurrentLocationButton() {
        binding.btnCurrentLocation.setOnClickListener {
            mapController.moveToCurrentLocation()
        }
    }

    private fun observeViewModel() {
        viewModel.safeZones.observe(this) { zones ->
            if (zones.isNotEmpty()) {
                viewModel.isTracking.value?.let { isTracking ->
                    if (!isTracking) {
                        trackingController.startGeofenceTracking()
                    }
                }
            }
        }

        viewModel.currentGeofenceDataAsLiveData.observe(this) { safeZone ->
            safeZone?.let { zone ->
                val isInside = !preferencesManager.getState()
                mapController.updateMapWithZone(zone, isInside)

                // Restart tracking if zone changed
                if (viewModel.isTracking.value == true) {
                    trackingController.stopGeofenceTracking()
                    trackingController.startGeofenceTracking()
                }
            }
        }

        viewModel.isInsideGeofence.observe(this) { isInside ->
            mapController.updateGeofenceCircleColor(isInside)
            timerController.updateTimerDisplay()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.lykos.pointage.GEOFENCE_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(geofenceStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(geofenceStateReceiver, filter)
        }
        viewModel.updateInsideGeofenceState()
        timerController.updateTimerDisplay()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(geofenceStateReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_clear_data)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_view_history -> {
                historyController.showHistoryDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerController.cleanup()
    }
}