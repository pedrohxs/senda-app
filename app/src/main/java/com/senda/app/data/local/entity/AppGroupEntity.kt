package com.senda.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "app_groups")
data class AppGroupEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#A1B976",
    val isEnabled: Boolean = true,
    val dailyLimitMinutes: Int = 30,
    val sessionLimitMinutes: Int = 5,
    val cooldownMinutes: Int = 10,
    val maxDailyOpens: Int = 15,
    val sharedLimitAcrossApps: Boolean = true,
    val sessionCountMode: SessionCountMode = SessionCountMode.ACTIVE_SCREEN_TIME,
    val interventionType: InterventionType = InterventionType.BREATHING,
    val breathingSeconds: Int = 10,
    val mathDifficulty: MathDifficulty = MathDifficulty.EASY,
    val mathEquationCount: Int = 3,
    val progressiveFrictionEnabled: Boolean = true,
    val progressiveSecondsPerOpen: Int = 5,
    val alternativeAppPackage: String? = null,
    val temporaryDisableUntil: Long? = null
)
