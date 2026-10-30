package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.SessionUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageLogDao {

    @Query("SELECT * FROM daily_usage_logs")
    fun observeAll(): Flow<List<DailyUsageLogEntity>>

    @Query("SELECT * FROM daily_usage_logs")
    suspend fun allUsage(): List<DailyUsageLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<DailyUsageLogEntity>)

    @Query("DELETE FROM daily_usage_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM daily_usage_logs WHERE date < :cutoff")
    suspend fun deleteBefore(cutoff: String)

    @Query("DELETE FROM session_usage WHERE date < :cutoff")
    suspend fun deleteSessionUsageBefore(cutoff: String)

    /**
     * Uso gravado para um grupo que não existe mais (arquivo restaurado sem
     * aquele grupo, sessão encerrada durante a restauração). Sem isso esses
     * registros apareceriam nos relatórios sem nome de grupo.
     */
    @Query(
        """
        DELETE FROM daily_usage_logs
        WHERE CASE
            WHEN instr(groupId, '::item::') > 0
                THEN substr(groupId, 1, instr(groupId, '::item::') - 1)
            ELSE groupId
        END NOT IN (SELECT id FROM app_groups)
        """
    )
    suspend fun deleteOrphans()

    @Query("DELETE FROM session_usage")
    suspend fun deleteAllSessionUsage()

    @Query("SELECT * FROM session_usage WHERE sessionId = :sessionId AND date = :date")
    suspend fun findSessionUsage(sessionId: String, date: String): SessionUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSessionUsage(usage: SessionUsageEntity)

    @Transaction
    suspend fun recordSessionUsage(sessionId: String, usageKey: String, totals: Map<String, Int>) {
        for ((date, seconds) in totals) {
            val recorded = findSessionUsage(sessionId, date)?.accountedSeconds ?: 0
            if (seconds > recorded) {
                recordUsageSeconds(date, usageKey, seconds - recorded)
                saveSessionUsage(SessionUsageEntity(sessionId, date, usageKey, seconds))
            }
        }
    }

    // One SQL statement observes both values from the same database snapshot.
    @Query("""
        SELECT COALESCE(SUM(totalTimeUsedSeconds), 0) AS totalSeconds,
            COALESCE((SELECT accountedSeconds FROM session_usage
                WHERE sessionId = :sessionId AND date = :date), 0) AS sessionSeconds
        FROM daily_usage_logs WHERE date = :date AND
            ((:shared = 1 AND (groupId = :groupId OR groupId LIKE :groupId || '::item::%'))
                OR (:shared = 0 AND groupId = :usageKey))
    """)
    suspend fun dailySessionBalance(
        date: String, groupId: String, usageKey: String, shared: Boolean, sessionId: String
    ): DailySessionBalance

    @Query("SELECT * FROM daily_usage_logs WHERE date = :date")
    fun observeByDate(date: String): Flow<List<DailyUsageLogEntity>>

    @Query(
        """
        SELECT * FROM daily_usage_logs
        WHERE date >= :startDate AND date <= :endDate
        """
    )
    fun observeBetween(startDate: String, endDate: String): Flow<List<DailyUsageLogEntity>>

    @Query(
        """
        SELECT * FROM daily_usage_logs
        WHERE date = :date AND groupId = :groupId
        LIMIT 1
        """
    )
    suspend fun findByDateAndGroup(date: String, groupId: String): DailyUsageLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DailyUsageLogEntity): Long

    @Query(
        """
        DELETE FROM daily_usage_logs
        WHERE groupId = :groupId OR groupId LIKE :groupId || '::item::%'
        """
    )
    suspend fun deleteByGroup(groupId: String)

    @Query("UPDATE daily_usage_logs SET totalOpens = totalOpens + 1 WHERE date = :date AND groupId = :groupId")
    suspend fun incrementOpens(date: String, groupId: String)

    @Query(
        """
        UPDATE daily_usage_logs
        SET impulsesAborted = impulsesAborted + 1
        WHERE date = :date AND groupId = :groupId
        """
    )
    suspend fun incrementImpulsesAborted(date: String, groupId: String)

    @Query(
        """
        UPDATE daily_usage_logs
        SET totalTimeUsedSeconds = totalTimeUsedSeconds + :seconds
        WHERE date = :date AND groupId = :groupId
        """
    )
    suspend fun addUsedSeconds(date: String, groupId: String, seconds: Int)

    @Query(
        """
        UPDATE daily_usage_logs
        SET totalTimeUsedMinutes = totalTimeUsedSeconds / 60
        WHERE date = :date AND groupId = :groupId
        """
    )
    suspend fun refreshUsedMinutes(date: String, groupId: String)

    @Transaction
    suspend fun recordOpen(date: String, groupId: String) {
        if (findByDateAndGroup(date, groupId) == null) {
            insert(DailyUsageLogEntity(date = date, groupId = groupId, totalOpens = 1))
        } else {
            incrementOpens(date, groupId)
        }
    }

    @Transaction
    suspend fun recordImpulseAborted(date: String, groupId: String) {
        if (findByDateAndGroup(date, groupId) == null) {
            insert(DailyUsageLogEntity(date = date, groupId = groupId, impulsesAborted = 1))
        } else {
            incrementImpulsesAborted(date, groupId)
        }
    }

    @Transaction
    suspend fun recordUsageSeconds(date: String, groupId: String, seconds: Int) {
        if (seconds <= 0) return

        if (findByDateAndGroup(date, groupId) == null) {
            insert(
                DailyUsageLogEntity(
                    date = date,
                    groupId = groupId,
                    totalTimeUsedMinutes = seconds / 60,
                    totalTimeUsedSeconds = seconds
                )
            )
        } else {
            addUsedSeconds(date, groupId, seconds)
            refreshUsedMinutes(date, groupId)
        }
    }
}

data class DailySessionBalance(val totalSeconds: Int, val sessionSeconds: Int)
