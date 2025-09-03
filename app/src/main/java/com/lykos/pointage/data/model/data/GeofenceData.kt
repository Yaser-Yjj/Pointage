package com.lykos.pointage.data.model.data

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

