package com.lykos.pointage.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "location_events")
data class LocationEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exitTimestamp: Date?,
    val entryTimestamp: Date?,
    val totalTimeAway: Long // in milliseconds
)
