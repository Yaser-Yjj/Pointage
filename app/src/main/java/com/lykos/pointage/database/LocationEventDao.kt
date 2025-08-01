package com.lykos.pointage.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow


/**
 * Data Access Object for LocationEvent
 * Provides methods to interact with location_events table
 */
@Dao
interface LocationEventDao {

    @Query("SELECT * FROM location_events ORDER BY id DESC")
    fun getAllLocationEvents(): Flow<List<LocationEvent>>

    @Query("SELECT * FROM location_events ORDER BY id DESC LIMIT 1")
    suspend fun getLatestLocationEvent(): LocationEvent?

    @Insert
    suspend fun insertLocationEvent(locationEvent: LocationEvent): Long

    @Update
    suspend fun updateLocationEvent(locationEvent: LocationEvent)

    @Delete
    suspend fun deleteLocationEvent(locationEvent: LocationEvent)

    @Query("DELETE FROM location_events")
    suspend fun deleteAllLocationEvents()

    @Query("SELECT SUM(totalTimeAway) FROM location_events WHERE totalTimeAway > 0")
    suspend fun getTotalTimeAway(): Long?

    @Query("SELECT COUNT(*) FROM location_events WHERE totalTimeAway > 0")
    suspend fun getTotalTrips(): Int

    @Query("SELECT * FROM location_events WHERE totalTimeAway > 0 ORDER BY id DESC LIMIT 10")
    suspend fun getRecentEvents(): List<LocationEvent>
}