package com.senda.app.domain.policy

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.ScheduleType
import java.time.Clock
import java.time.LocalDateTime
import kotlin.math.max

class InterventionEngine(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    fun evaluateApp(
        packageName: String,
        snapshot: PolicySnapshot,
        now: LocalDateTime = LocalDateTime.now(clock)
    ): InterventionDecision {
        val policies = snapshot.groupsForPackage(packageName)
        if (policies.isEmpty()) return InterventionDecision.Allow

        val nowMillis = now.atZone(clock.zone).toInstant().toEpochMilli()
        return resolve(
            policies.map { policy ->
                val target = InterventionTarget(
                    packageName = packageName,
                    identifier = packageName,
                    displayName = policy.displayNameFor(packageName),
                    isWebsite = false
                )
                evaluatePolicy(policy, snapshot, target, now, nowMillis)
            }
        )
    }

    fun evaluateDomain(
        host: String,
        browserPackage: String,
        snapshot: PolicySnapshot,
        now: LocalDateTime = LocalDateTime.now(clock)
    ): InterventionDecision {
        val policies = snapshot.groupsForHost(host)
        if (policies.isEmpty()) return InterventionDecision.Allow

        val nowMillis = now.atZone(clock.zone).toInstant().toEpochMilli()
        return resolve(
            policies.map { policy ->
                val target = InterventionTarget(
                    packageName = browserPackage,
                    identifier = host,
                    displayName = policy.displayNameFor(host),
                    isWebsite = true
                )
                evaluateExplicitBlock(
                    policy = policy,
                    snapshot = snapshot,
                    target = target,
                    now = now,
                    nowMillis = nowMillis,
                    reason = BlockReason.BLOCKED_DOMAIN
                )
            }
        )
    }

    fun evaluateKeyword(
        keyword: String,
        browserPackage: String,
        snapshot: PolicySnapshot,
        now: LocalDateTime = LocalDateTime.now(clock)
    ): InterventionDecision {
        val policies = snapshot.groupsForKeyword(keyword)
        if (policies.isEmpty()) return InterventionDecision.Allow

        val nowMillis = now.atZone(clock.zone).toInstant().toEpochMilli()
        return resolve(
            policies.map { policy ->
                val target = InterventionTarget(
                    packageName = browserPackage,
                    identifier = keyword,
                    displayName = policy.displayNameFor(keyword),
                    isWebsite = false
                )
                evaluateExplicitBlock(
                    policy = policy,
                    snapshot = snapshot,
                    target = target,
                    now = now,
                    nowMillis = nowMillis,
                    reason = BlockReason.BLOCKED_KEYWORD
                )
            }
        )
    }

    /**
     * Sites e palavras-chave cadastrados são bloqueios explícitos: não passam
     * por desafio, cooldown ou limite diário. Só deixam de valer se o grupo
     * estiver desativado/pausado ou fora de uma janela de proteção.
     */
    private fun evaluateExplicitBlock(
        policy: GroupPolicy,
        snapshot: PolicySnapshot,
        target: InterventionTarget,
        now: LocalDateTime,
        nowMillis: Long,
        reason: BlockReason
    ): InterventionDecision {
        val group = policy.group
        if (!group.isEnabled) return InterventionDecision.Allow

        if (ScheduleEvaluator.currentSchedule(policy.schedules, now) == null) return InterventionDecision.Allow

        if (group.temporaryDisableUntil?.let { it > nowMillis } == true) {
            return InterventionDecision.Allow
        }

        if (snapshot.clockTampered) {
            return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.CLOCK_TAMPER,
                cooldownRemainingSeconds = snapshot.clockTamperRemainingSeconds
            )
        }

        when (ScheduleEvaluator.currentSchedule(policy.schedules, now)) {
            ScheduleType.BREAK -> return InterventionDecision.Allow
            ScheduleType.STRICT -> return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.STRICT_SCHEDULE
            )
            ScheduleType.LIMITED, null -> Unit
        }

        return InterventionDecision.Block(
            target = target,
            groupId = group.id,
            groupName = group.name,
            reason = reason
        )
    }

    internal fun evaluateGroup(
        policy: GroupPolicy,
        snapshot: PolicySnapshot,
        target: InterventionTarget,
        now: LocalDateTime = LocalDateTime.now(clock)
    ): InterventionDecision {
        val nowMillis = now.atZone(clock.zone).toInstant().toEpochMilli()
        return evaluatePolicy(policy, snapshot, target, now, nowMillis, useAggregateUsage = true)
    }

    private fun evaluatePolicy(
        policy: GroupPolicy,
        snapshot: PolicySnapshot,
        target: InterventionTarget,
        now: LocalDateTime,
        nowMillis: Long,
        useAggregateUsage: Boolean = false
    ): InterventionDecision {
        val resolved = GroupRuleResolver.resolve(policy, now)
        val scopedPolicy = policy.scoped(resolved.scopeKey, resolved.group, now.toLocalDate())
        val cooldowns = snapshot.cooldowns.toMutableMap().apply {
            remove(policy.group.id)
            snapshot.cooldowns[resolved.scopeKey]?.let { put(policy.group.id, it) }
        }
        val usageKey = if (resolved.group.sharedLimitAcrossApps) resolved.scopeKey
            else UsageKey.forTarget(resolved.scopeKey, target.identifier)
        return when (val decision = evaluateRules(scopedPolicy, snapshot.copy(cooldowns = cooldowns), target, now, nowMillis, useAggregateUsage)) {
            is InterventionDecision.Challenge -> decision.copy(usageKey = usageKey)
            is InterventionDecision.Block -> decision.copy(usageKey = usageKey, dailyLimitMinutes = resolved.group.dailyLimitMinutes)
            else -> decision
        }
    }

    private fun evaluateRules(
        policy: GroupPolicy, snapshot: PolicySnapshot, target: InterventionTarget,
        now: LocalDateTime, nowMillis: Long, useAggregateUsage: Boolean
    ): InterventionDecision {
        val group = policy.group
        if (!group.isEnabled) return InterventionDecision.Allow

        if (ScheduleEvaluator.currentSchedule(policy.schedules, now) == null) return InterventionDecision.Allow

        if (group.temporaryDisableUntil?.let { it > nowMillis } == true) {
            return InterventionDecision.Allow
        }

        if (snapshot.clockTampered) {
            return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.CLOCK_TAMPER,
                cooldownRemainingSeconds = snapshot.clockTamperRemainingSeconds
            )
        }

        when (ScheduleEvaluator.currentSchedule(policy.schedules, now)) {
            ScheduleType.BREAK -> return InterventionDecision.Allow
            ScheduleType.STRICT -> return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.STRICT_SCHEDULE
            )
            ScheduleType.LIMITED, null -> Unit
        }

        val cooldownUntil = snapshot.cooldowns[group.id]
        if (cooldownUntil != null && cooldownUntil > nowMillis) {
            val remainingSeconds = ((cooldownUntil - nowMillis + 999L) / 1000L)
                .coerceAtLeast(1L)
            return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.COOLDOWN,
                cooldownRemainingSeconds = remainingSeconds
            )
        }

        val usage = if (useAggregateUsage) policy.usage else policy.usageFor(target.identifier)
        val usedSeconds = usage?.totalTimeUsedSeconds ?: 0
        val dailyLimitSeconds = group.dailyLimitMinutes * 60L
        if (dailyLimitSeconds > 0 && usedSeconds.toLong() >= dailyLimitSeconds) {
            return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.DAILY_LIMIT
            )
        }

        val totalOpens = usage?.totalOpens ?: 0
        if (group.maxDailyOpens > 0 && totalOpens >= group.maxDailyOpens) {
            return InterventionDecision.Block(
                target = target,
                groupId = group.id,
                groupName = group.name,
                reason = BlockReason.OPEN_LIMIT
            )
        }

        // A fricção progressiva alonga a pausa de respiração. Nos outros
        // desafios (matemática, motivo) ela não teria efeito e só confundia
        // quem configurou o grupo.
        val progressiveExtra = if (
            group.progressiveFrictionEnabled &&
            group.interventionType == InterventionType.BREATHING
        ) {
            totalOpens * group.progressiveSecondsPerOpen
        } else {
            0
        }

        return InterventionDecision.Challenge(
            target = target,
            groupId = group.id,
            groupName = group.name,
            interventionType = group.interventionType,
            frictionSeconds = group.breathingSeconds + progressiveExtra,
            sessionLimitMinutes = max(1, group.sessionLimitMinutes),
            cooldownMinutes = max(0, group.cooldownMinutes),
            countMode = group.sessionCountMode,
            alternativeAppPackage = group.alternativeAppPackage,
            usageKey = if (group.sharedLimitAcrossApps) {
                group.id
            } else {
                UsageKey.forTarget(group.id, target.identifier)
            },
            allowedPackages = if (group.sharedLimitAcrossApps) {
                (policy.appPackages + target.packageName).toSet()
            } else {
                setOf(target.packageName)
            },
            mathDifficulty = group.mathDifficulty,
            mathEquationCount = group.mathEquationCount.coerceAtLeast(3)
        )
    }

    private fun resolve(decisions: List<InterventionDecision>): InterventionDecision {
        val blocks = decisions.filterIsInstance<InterventionDecision.Block>()
        if (blocks.isNotEmpty()) {
            return blocks.minByOrNull { blockPriority(it.reason) } ?: blocks.first()
        }

        val challenges = decisions.filterIsInstance<InterventionDecision.Challenge>()
        if (challenges.isNotEmpty()) {
            return challenges.minWithOrNull(
                compareBy<InterventionDecision.Challenge> { it.sessionLimitMinutes }
                    .thenBy { it.frictionSeconds }
            ) ?: challenges.first()
        }

        return InterventionDecision.Allow
    }

    private fun blockPriority(reason: BlockReason): Int = when (reason) {
        BlockReason.STRICT_SCHEDULE -> 0
        BlockReason.CLOCK_TAMPER -> 0
        BlockReason.BLOCKED_DOMAIN -> 0
        BlockReason.BLOCKED_KEYWORD -> 0
        BlockReason.COOLDOWN -> 1
        BlockReason.DAILY_LIMIT -> 2
        BlockReason.OPEN_LIMIT -> 3
    }
}
