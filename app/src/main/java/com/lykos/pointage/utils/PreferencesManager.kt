package com.lykos.pointage.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lykos.pointage.data.model.data.GeofencePreferences

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
        private const val KEY_IS_OUTSIDE = "is_outside_safe_zone"
        private const val KEY_LAST_ENTER_TIMESTAMP = "last_enter_timestamp"
        private const val KEY_LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
        private const val KEY_ACCUMULATED_TIME_INSIDE = "accumulated_time_inside"
        private const val KEY_CURRENT_USER_ID = "current_user_id"

        private const val KEY_LAST_GEOFENCE_ID = "last_geofence_id"
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    private fun saveLastSelectedGeofence(geofenceId: String) {
        sharedPrefs.edit { putString(KEY_LAST_GEOFENCE_ID, geofenceId) }
    }

    private fun getLastSelectedGeofence(): String? {
        return sharedPrefs.getString(KEY_LAST_GEOFENCE_ID, null)
    }

    fun saveCurrentUserId(userId: String) {
        sharedPrefs.edit { putString(KEY_CURRENT_USER_ID, userId) }
    }

    fun getCurrentUserId(): String? {
        return sharedPrefs.getString(KEY_CURRENT_USER_ID, null)
    }

    fun setState(isOutside: Boolean) {
        sharedPrefs.edit { putBoolean(KEY_IS_OUTSIDE, isOutside) }
    }

    fun getState(): Boolean {
        return sharedPrefs.getBoolean(KEY_IS_OUTSIDE, false)
    }

    fun saveLastEnterTimestamp(timestamp: Long) {
        sharedPrefs.edit { putLong(KEY_LAST_ENTER_TIMESTAMP, timestamp) }
    }

    fun getLastEnterTimestamp(): Long {
        return sharedPrefs.getLong(KEY_LAST_ENTER_TIMESTAMP, 0L)
    }

    // For the start of the current continuous session outside the zone
    fun saveLastExitTimestamp(timestamp: Long) {
        sharedPrefs.edit { putLong(KEY_LAST_EXIT_TIMESTAMP, timestamp) }
    }

    fun getLastExitTimestamp(): Long {
        return sharedPrefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
    }

    fun addAccumulatedTimeInside(timeToAdd: Long) {
        val currentAccumulated = getAccumulatedTimeInside()
        sharedPrefs.edit { putLong(KEY_ACCUMULATED_TIME_INSIDE, currentAccumulated + timeToAdd) }
    }

    fun getAccumulatedTimeInside(): Long {
        return sharedPrefs.getLong(KEY_ACCUMULATED_TIME_INSIDE, 0L)
    }

    fun resetAccumulatedTimeInside() {
        sharedPrefs.edit { putLong(KEY_ACCUMULATED_TIME_INSIDE, 0L) }
    }

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
