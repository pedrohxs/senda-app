package com.senda.app.data.local.entity

import androidx.room.Entity

/** High-water mark: replaying a persisted session must never add its usage twice. */
@Entity(tableName = "session_usage", primaryKeys = ["sessionId", "date"])
data class SessionUsageEntity(
    val sessionId: String,
    val date: String,
    val usageKey: String,
    val accountedSeconds: Int
)
