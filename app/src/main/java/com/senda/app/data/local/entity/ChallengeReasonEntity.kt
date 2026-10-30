package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "challenge_reasons", indices = [Index("createdAt")])
data class ChallengeReasonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val displayName: String,
    val groupId: String,
    val reason: String,
    val createdAt: Long
)
