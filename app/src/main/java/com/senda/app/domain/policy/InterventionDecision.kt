package com.senda.app.domain.policy

import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.SessionCountMode

data class InterventionTarget(
    val packageName: String,
    val identifier: String,
    val displayName: String,
    val isWebsite: Boolean
)

sealed interface InterventionDecision {

    data object Allow : InterventionDecision

    data class Block(
        val target: InterventionTarget,
        val groupId: String,
        val groupName: String,
        val reason: BlockReason,
        val cooldownRemainingSeconds: Long = 0L,
        val usageKey: String = groupId,
        val dailyLimitMinutes: Int = 0
    ) : InterventionDecision

    data class Challenge(
        val target: InterventionTarget,
        val groupId: String,
        val groupName: String,
        val interventionType: InterventionType,
        val frictionSeconds: Int,
        val sessionLimitMinutes: Int,
        val cooldownMinutes: Int,
        val countMode: SessionCountMode,
        val alternativeAppPackage: String?,
        val usageKey: String,
        val allowedPackages: Set<String> = setOf(target.packageName),
        val mathDifficulty: MathDifficulty = MathDifficulty.EASY,
        val mathEquationCount: Int = 3
    ) : InterventionDecision
}

enum class BlockReason {
    STRICT_SCHEDULE,
    COOLDOWN,
    DAILY_LIMIT,
    OPEN_LIMIT,
    CLOCK_TAMPER,
    BLOCKED_DOMAIN,
    BLOCKED_KEYWORD
}
