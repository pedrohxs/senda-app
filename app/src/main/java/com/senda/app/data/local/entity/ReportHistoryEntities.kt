package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_days")
data class ReportDayEntity(
    @PrimaryKey val date: String,
    val coveredMillis: Long,
    val expectedMillis: Long,
    val goalsRecorded: Boolean,
    val zoneId: String,
    val goalsReliable: Boolean = true
) {
    val hasFullCoverage: Boolean get() = expectedMillis > 0L && coveredMillis >= expectedMillis
}

@Entity(tableName = "daily_goals", primaryKeys = ["date", "usageKey"])
data class DailyGoalEntity(
    val date: String,
    val usageKey: String,
    val groupId: String,
    val groupName: String,
    val targetName: String,
    val limitSeconds: Int,
    val shared: Boolean
)

@Entity(tableName = "report_monitor_checkpoint")
data class ReportMonitorCheckpointEntity(
    @PrimaryKey val id: Int = 0,
    val epochMillis: Long,
    val elapsedRealtime: Long,
    val bootCount: Int,
    val healthy: Boolean,
    val zoneId: String,
    val screenOff: Boolean,
    val processToken: String
)
