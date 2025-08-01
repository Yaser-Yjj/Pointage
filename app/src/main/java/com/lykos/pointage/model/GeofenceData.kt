package com.lykos.pointage.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing geofence configuration
 * Parcelable for passing between activities/fragments
 */
@Parcelize
data class GeofenceData(
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val isActive: Boolean = false
) : Parcelable

/**
 * Data class for geofence preferences storage
 */
data class GeofencePreferences(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 100f,
    val isGeofenceActive: Boolean = false
)
