package com.lykos.pointage.model

/**
 * Data class for geofence preferences storage
 */
data class GeofencePreferences(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 100f,
    val isGeofenceActive: Boolean = false
)
