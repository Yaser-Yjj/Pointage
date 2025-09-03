package com.lykos.pointage.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lykos.pointage.data.database.entity.DailyInsideTime

@Dao
interface DailyInsideTimeDao {
    @Query("SELECT * FROM daily_inside_time ORDER BY date DESC")
    suspend fun getAll(): List<DailyInsideTime>

    @Query("SELECT * FROM daily_inside_time WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyInsideTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dailyInsideTime: DailyInsideTime)

    @Update
    suspend fun update(dailyInsideTime: DailyInsideTime)
}
