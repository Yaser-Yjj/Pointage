package com.lykos.pointage.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.lykos.pointage.GeofenceMapApplication
import com.lykos.pointage.database.entity.LocationEvent
import com.lykos.pointage.model.GeofenceData
import com.lykos.pointage.model.GeofencePreferences
import com.lykos.pointage.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Manages UI state and business logic for geofence configuration
 * Handles database operations and preferences management
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as GeofenceMapApplication).database
    private val preferencesManager = PreferencesManager(application)

    // LiveData for UI state
    private val _geofenceData = MutableLiveData<GeofenceData?>()
    val geofenceData: LiveData<GeofenceData?> = _geofenceData

    private val _isTracking = MutableLiveData<Boolean>()
    val isTracking: LiveData<Boolean> = _isTracking

    private val _locationEvents = MutableLiveData<List<LocationEvent>>()
    val locationEvents: LiveData<List<LocationEvent>> = _locationEvents

    private val _totalTimeAway = MutableLiveData<Long>()
    val totalTimeAway: LiveData<Long> = _totalTimeAway

    private val _totalTrips = MutableLiveData<Int>()
    val totalTrips: LiveData<Int> = _totalTrips

    private val _isInsideGeofence = MutableLiveData<Boolean>()
    val isInsideGeofence: LiveData<Boolean> = _isInsideGeofence

    init {
        loadGeofenceData()
        loadTrackingState()
        loadLocationEvents()
        loadStatistics()
    }

    fun updateInsideGeofenceState() {
        val isOutside = preferencesManager.getState()
        _isInsideGeofence.value = !isOutside
    }

    /**
     * Loads saved geofence configuration from preferences
     */
    private fun loadGeofenceData() {
        val preferences = preferencesManager.getGeofencePreferences()
        if (preferences.latitude != 0.0 && preferences.longitude != 0.0) {
            _geofenceData.value = GeofenceData(
                latitude = preferences.latitude,
                longitude = preferences.longitude,
                radius = preferences.radius,
                isActive = preferences.isGeofenceActive
            )
        }
    }

    /**
     * Loads current tracking state
     */
    private fun loadTrackingState() {
        _isTracking.value = preferencesManager.isTracking()
    }

    /**
     * Loads location events from database
     */
    private fun loadLocationEvents() {
        viewModelScope.launch {
            try {
                val events = database.locationEventDao().getRecentEvents()
                _locationEvents.value = events
            } catch (_: Exception) {
                // Handle error
                _locationEvents.value = emptyList()
            }
        }
    }

    /**
     * Loads statistics from database
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                val totalTime = database.locationEventDao().getTotalTimeAway() ?: 0L
                val totalTrips = database.locationEventDao().getTotalTrips()

                _totalTimeAway.value = totalTime
                _totalTrips.value = totalTrips
            } catch (_: Exception) {
                _totalTimeAway.value = 0L
                _totalTrips.value = 0
            }
        }
    }

    /**
     * Saves geofence configuration
     */
    fun saveGeofenceData(latitude: Double, longitude: Double, radius: Float) {
        val geofenceData = GeofenceData(latitude, longitude, radius, true)
        _geofenceData.value = geofenceData

        val preferences = GeofencePreferences(
            latitude = latitude,
            longitude = longitude,
            radius = radius,
            isGeofenceActive = false
        )
        preferencesManager.saveGeofencePreferences(preferences)
    }

    /**
     * Updates geofence active state
     */
    fun updateGeofenceActiveState(isActive: Boolean) {
        val currentData = _geofenceData.value
        if (currentData != null) {
            val updatedData = currentData.copy(isActive = isActive)
            _geofenceData.value = updatedData

            val preferences = GeofencePreferences(
                latitude = currentData.latitude,
                longitude = currentData.longitude,
                radius = currentData.radius,
                isGeofenceActive = isActive
            )
            preferencesManager.saveGeofencePreferences(preferences)
        }
    }

    /**
     * Updates tracking state
     */
    fun updateTrackingState(isTracking: Boolean) {
        _isTracking.value = isTracking
        preferencesManager.setTrackingState(isTracking)
    }

    /**
     * Refreshes data from database
     */
    fun refreshData() {
        loadLocationEvents()
        loadStatistics()
        loadTrackingState()
    }

    /**
     * Clears all data
     */
    fun clearAllData() {
        viewModelScope.launch {
            try {
                database.locationEventDao().deleteAllLocationEvents()
                preferencesManager.clearGeofencePreferences()

                _geofenceData.value = null
                _isTracking.value = false
                _locationEvents.value = emptyList()
                _totalTimeAway.value = 0L
                _totalTrips.value = 0
            } catch (_: Exception) {
                // Handle error
            }
        }
    }
}
