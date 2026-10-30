package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senda.app.data.local.entity.GroupItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupItemDao {

    @Query(
        """
        SELECT gi.* FROM group_items gi
        INNER JOIN app_groups ag ON ag.id = gi.groupId
        WHERE ag.isEnabled = 1
        """
    )
    fun observeActiveItems(): Flow<List<GroupItemEntity>>

    @Query("SELECT * FROM group_items WHERE groupId = :groupId")
    fun observeByGroup(groupId: String): Flow<List<GroupItemEntity>>

    @Query("SELECT * FROM group_items WHERE groupId = :groupId")
    suspend fun getByGroup(groupId: String): List<GroupItemEntity>

    @Query("SELECT * FROM group_items")
    suspend fun allItems(): List<GroupItemEntity>

    @Query("DELETE FROM group_items WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: GroupItemEntity): Long

    @Delete
    suspend fun delete(item: GroupItemEntity)
}
