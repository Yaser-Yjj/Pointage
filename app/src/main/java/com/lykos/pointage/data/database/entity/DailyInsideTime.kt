package com.lykos.pointage.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_inside_time")
data class DailyInsideTime(
    @PrimaryKey val date: String,
    val totalTimeInside: Long
)