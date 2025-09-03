package com.lykos.pointage.database

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room type converters for Date objects
 * Converts Date to Long (timestamp) for storage
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}