package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "daily_usage_logs", indices = [Index("date"), Index("groupId")])
data class DailyUsageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val groupId: String,
    val totalTimeUsedMinutes: Int = 0,
    val totalTimeUsedSeconds: Int = 0,
    val totalOpens: Int = 0,
    val impulsesAborted: Int = 0
)
