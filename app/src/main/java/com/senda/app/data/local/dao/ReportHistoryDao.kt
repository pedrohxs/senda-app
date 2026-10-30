package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.ReportDayEntity
import com.senda.app.data.local.entity.ReportMonitorCheckpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportHistoryDao {
    @Query("SELECT * FROM report_days ORDER BY date")
    fun observeDays(): Flow<List<ReportDayEntity>>

    @Query("SELECT * FROM daily_goals ORDER BY date, usageKey")
    fun observeGoals(): Flow<List<DailyGoalEntity>>

    @Query("SELECT * FROM report_days WHERE date = :date")
    suspend fun findDay(date: String): ReportDayEntity?

    @Query("SELECT * FROM report_days")
    suspend fun allDays(): List<ReportDayEntity>

    @Query("SELECT * FROM daily_goals")
    suspend fun allGoals(): List<DailyGoalEntity>

    @Query("DELETE FROM report_days")
    suspend fun deleteAllDays()

    @Query("DELETE FROM daily_goals")
    suspend fun deleteAllGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDays(days: List<ReportDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllGoals(goals: List<DailyGoalEntity>)

    @Query("DELETE FROM report_days WHERE date < :cutoff")
    suspend fun deleteDaysBefore(cutoff: String)

    @Query("DELETE FROM daily_goals WHERE date < :cutoff")
    suspend fun deleteGoalsBefore(cutoff: String)

    @Query("SELECT MAX(date) FROM report_days")
    suspend fun latestDate(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDay(day: ReportDayEntity)

    @Query("SELECT * FROM daily_goals WHERE date = :date ORDER BY usageKey")
    suspend fun goalsForDate(date: String): List<DailyGoalEntity>

    @Query("DELETE FROM daily_goals WHERE date = :date")
    suspend fun deleteGoals(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGoals(goals: List<DailyGoalEntity>)

    @Query("SELECT * FROM report_monitor_checkpoint WHERE id = 0")
    suspend fun checkpoint(): ReportMonitorCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(checkpoint: ReportMonitorCheckpointEntity)
}
