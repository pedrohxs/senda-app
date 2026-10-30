package com.senda.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senda.app.data.local.entity.ChallengeReasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeReasonDao {

    @Insert
    suspend fun insert(reason: ChallengeReasonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reasons: List<ChallengeReasonEntity>)

    @Query("SELECT * FROM challenge_reasons ORDER BY createdAt")
    suspend fun all(): List<ChallengeReasonEntity>

    @Query("DELETE FROM challenge_reasons")
    suspend fun deleteAll()

    @Query("SELECT * FROM challenge_reasons ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ChallengeReasonEntity>>

    @Query(
        """
        DELETE FROM challenge_reasons
        WHERE id NOT IN (
            SELECT id FROM challenge_reasons
            ORDER BY createdAt DESC
            LIMIT :keep
        )
        """
    )
    suspend fun prune(keep: Int)
}
