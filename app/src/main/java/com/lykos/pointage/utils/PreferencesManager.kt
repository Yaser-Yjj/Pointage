package com.lykos.pointage.utils

import android.content.Context
import android.content.SharedPreferences
import com.lykos.pointage.model.GeofencePreferences
import androidx.core.content.edit

/**
 * Manages SharedPreferences for geofence configuration
 * Stores geofence location, radius, and active state
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "geofence_prefs"
        private const val KEY_LATITUDE = "geofence_latitude"
        private const val KEY_LONGITUDE = "geofence_longitude"
        private const val KEY_RADIUS = "geofence_radius"
        private const val KEY_IS_ACTIVE = "geofence_is_active"
        private const val KEY_IS_TRACKING = "is_tracking"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves geofence configuration to SharedPreferences
     */
    fun saveGeofencePreferences(preferences: GeofencePreferences) {
        sharedPrefs.edit().apply {
            putFloat(KEY_LATITUDE, preferences.latitude.toFloat())
            putFloat(KEY_LONGITUDE, preferences.longitude.toFloat())
            putFloat(KEY_RADIUS, preferences.radius)
            putBoolean(KEY_IS_ACTIVE, preferences.isGeofenceActive)
            apply()
        }
    }

    /**
     * Loads geofence configuration from SharedPreferences
     */
    fun getGeofencePreferences(): GeofencePreferences {
        return GeofencePreferences(
            latitude = sharedPrefs.getFloat(KEY_LATITUDE, 0f).toDouble(),
            longitude = sharedPrefs.getFloat(KEY_LONGITUDE, 0f).toDouble(),
            radius = sharedPrefs.getFloat(KEY_RADIUS, 100f),
            isGeofenceActive = sharedPrefs.getBoolean(KEY_IS_ACTIVE, false)
        )
    }

    /**
     * Checks if geofence is configured (has valid coordinates)
     */
    fun isGeofenceConfigured(): Boolean {
        val prefs = getGeofencePreferences()
        return prefs.latitude != 0.0 && prefs.longitude != 0.0
    }

    /**
     * Sets tracking state (whether user is currently being tracked)
     */
    fun setTrackingState(isTracking: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_IS_TRACKING, isTracking) }
    }

    /**
     * Gets current tracking state
     */
    fun isTracking(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_TRACKING, false)
    }

    /**
     * Clears all geofence preferences
     */
    fun clearGeofencePreferences() {
        sharedPrefs.edit { clear() }
    }
}
