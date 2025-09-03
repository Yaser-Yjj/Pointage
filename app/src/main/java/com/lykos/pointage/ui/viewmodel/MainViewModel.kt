package com.lykos.pointage.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.data.database.entity.LocationEvent
import com.lykos.pointage.data.model.data.GeofencePreferences
import com.lykos.pointage.data.model.data.SafeZoneData
import com.lykos.pointage.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Manages UI state and business logic for geofence configuration
 * Handles database operations and preferences management
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as GeofenceMapApplication).database
    private val preferencesManager = PreferencesManager(application)

    // 🔷 StateFlows for internal mutable state
    private val _safeZones = MutableStateFlow<List<SafeZoneData>>(emptyList())
    private val _selectedZoneIndex = MutableStateFlow(0)
    private val _isTracking = MutableLiveData<Boolean>()
    private val _locationEvents = MutableLiveData<List<LocationEvent>>()
    private val _totalTimeInside = MutableLiveData<Long>()
    private val _isInsideGeofence = MutableLiveData<Boolean>()

    // 🔶 Exposed as LiveData (or StateFlow) for UI
    val safeZones: LiveData<List<SafeZoneData>> = _safeZones.asLiveData()
    val selectedZoneIndex: LiveData<Int> = _selectedZoneIndex.asLiveData()
    val isTracking: LiveData<Boolean> = _isTracking
    val locationEvents: LiveData<List<LocationEvent>> = _locationEvents
    val totalTimeInside: LiveData<Long> = _totalTimeInside
    val isInsideGeofence: LiveData<Boolean> = _isInsideGeofence

    // 🌟 Derived: Current selected safe zone (modern way)
    val currentGeofenceData = combine(_safeZones, _selectedZoneIndex) { zones, index ->
        zones.getOrNull(index)
    }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    val currentGeofenceDataAsLiveData: LiveData<SafeZoneData?> = currentGeofenceData.asLiveData()

    init {
        loadInitialState()
        loadLocationEvents()
        loadStatistics()
    }

    private fun loadInitialState() {
        val preferences = preferencesManager.getGeofencePreferences()
        val zones = if (preferences.latitude != 0.0 && preferences.longitude != 0.0) {
            listOf(
                SafeZoneData(
                    id = 0,
                    latitude = preferences.latitude,
                    longitude = preferences.longitude,
                    radius = preferences.radius
                )
            )
        } else {
            emptyList()
        }

        _safeZones.value = zones
        _selectedZoneIndex.value = zones.firstOrNull()?.let { 0 }!!
        _isTracking.value = preferencesManager.isTracking()
        _isInsideGeofence.value = !preferencesManager.getState()
    }

    fun setSafeZones(zones: List<SafeZoneData>) {
        val newZones = zones.map {
            SafeZoneData(
                id = it.id,
                latitude = it.latitude,
                longitude = it.longitude,
                radius = it.radius
            )
        }

        _safeZones.value = newZones
        if (newZones.isNotEmpty()) {
            _selectedZoneIndex.value = 0
        }
    }

    fun selectZone(index: Int) {
        val zones = _safeZones.value
        if (index in zones.indices) {
            _selectedZoneIndex.value = index
        }
    }

    fun updateInsideGeofenceState() {
        _isInsideGeofence.value = !preferencesManager.getState()
    }

    fun saveGeofenceData(latitude: Double, longitude: Double, radius: Float) {
        val zone = SafeZoneData(
            id = 0, latitude = latitude, longitude = longitude, radius = radius
        )
        setSafeZones(listOf(zone)) // For now — later you might append
        preferencesManager.saveGeofencePreferences(
            GeofencePreferences(latitude, longitude, radius, isGeofenceActive = true)
        )
    }

    fun updateTrackingState(isTracking: Boolean) {
        _isTracking.value = isTracking
        preferencesManager.setTrackingState(isTracking)
    }

    private fun loadLocationEvents() {
        viewModelScope.launch {
            runCatching {
                database.locationEventDao().getRecentEvents()
            }.onSuccess { events ->
                _locationEvents.value = events
            }.onFailure {
                _locationEvents.value = emptyList()
            }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            runCatching {
                database.locationEventDao().getTotalTimeInside() ?: 0L
            }.onSuccess { totalTime ->
                _totalTimeInside.value = totalTime
            }.onFailure { e ->
                // Log if needed
            }
        }
    }

    fun refreshData() {
        loadLocationEvents()
        loadStatistics()
        _isTracking.value = preferencesManager.isTracking()
    }

    fun clearAllData() {
        viewModelScope.launch {
            runCatching {
                database.locationEventDao().deleteAllLocationEvents()
                preferencesManager.clearGeofencePreferences()

                _locationEvents.value = emptyList()
                _totalTimeInside.value = 0L
                _isTracking.value = false
                _safeZones.value = emptyList()
                _selectedZoneIndex.value = 0
            }
        }
    }
}