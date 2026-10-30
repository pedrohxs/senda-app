package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.senda.app.data.local.entity.AppGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppGroupDao {

    @Query("SELECT * FROM app_groups")
    fun observeAllGroups(): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM app_groups")
    suspend fun allGroups(): List<AppGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<AppGroupEntity>)

    @Query("DELETE FROM app_groups")
    suspend fun deleteAll()

    @Query("SELECT * FROM app_groups WHERE isEnabled = 1")
    suspend fun enabledGroups(): List<AppGroupEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM app_groups)")
    suspend fun hasGroups(): Boolean

    @Query("SELECT * FROM app_groups WHERE isEnabled = 1")
    fun observeEnabledGroups(): Flow<List<AppGroupEntity>>

    @Query(
        """
        SELECT SUM(dailyLimitMinutes) FROM app_groups
        WHERE isEnabled = 1 AND dailyLimitMinutes > 0
        """
    )
    fun observeDailyLimitSum(): Flow<Int?>

    @Query("SELECT * FROM app_groups WHERE id = :groupId LIMIT 1")
    suspend fun findById(groupId: String): AppGroupEntity?

    @Query("UPDATE app_groups SET temporaryDisableUntil = :untilMillis WHERE id = :groupId")
    suspend fun setTemporaryDisableUntil(groupId: String, untilMillis: Long?)

    @Query("UPDATE app_groups SET name = :name, colorHex = :colorHex WHERE id = :groupId")
    suspend fun updateAppearance(groupId: String, name: String, colorHex: String): Int

    @Upsert
    suspend fun upsert(group: AppGroupEntity)

    @Query("DELETE FROM app_groups WHERE id = :groupId")
    suspend fun deleteById(groupId: String)

    @Delete
    suspend fun delete(group: AppGroupEntity)
}
