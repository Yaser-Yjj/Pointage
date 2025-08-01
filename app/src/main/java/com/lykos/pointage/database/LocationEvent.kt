package com.lykos.pointage.database

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
    val exitTime: Date?,           // When user left the safe zone
    val enterTime: Date?,          // When user returned to safe zone
    val totalTimeAway: Long,       // Duration away in milliseconds
    val geofenceLat: Double = 0.0, // Geofence center latitude
    val geofenceLng: Double = 0.0, // Geofence center longitude
    val geofenceRadius: Float = 0f // Geofence radius in meters
)

