package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senda.app.data.local.entity.GroupScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupScheduleDao {

    @Query("SELECT * FROM group_schedules")
    fun observeAll(): Flow<List<GroupScheduleEntity>>

    @Query("SELECT * FROM group_schedules WHERE groupId = :groupId")
    suspend fun getForGroup(groupId: String): List<GroupScheduleEntity>

    @Query("SELECT * FROM group_schedules")
    suspend fun allSchedules(): List<GroupScheduleEntity>

    @Query("DELETE FROM group_schedules WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<GroupScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: GroupScheduleEntity): Long

    @Delete
    suspend fun delete(schedule: GroupScheduleEntity)
}
