package com.lykos.pointage.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity representing a location tracking event
 * Stores when user exits and enters the geofence area
 */
@Entity(tableName = "location_events")
data class LocationEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exitTime: Date?,
    val enterTime: Date?,
    val totalTimeInside: Long,
    val geofenceLat: Double = 0.0,
    val geofenceLng: Double = 0.0,
    val geofenceRadius: Float = 0f
)
