package com.senda.app.domain.policy

import java.time.Clock
import java.time.LocalDateTime

enum class GroupStatus {
    FREE,
    LIMITED,
    PARTIAL,
    STRICT,
    BLOCKED
}

data class GroupStatusInfo(
    val status: GroupStatus,
    val blockReason: BlockReason? = null
)

class GroupStatusResolver(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val engine = InterventionEngine(clock)

    fun resolve(
        policy: GroupPolicy,
        cooldowns: Map<String, Long>,
        now: LocalDateTime = LocalDateTime.now(clock),
        clockTamperRemainingSeconds: Long = 0L
    ): GroupStatusInfo {
        val snapshot = PolicySnapshot(
            groups = listOf(policy),
            cooldowns = cooldowns,
            clockTamperRemainingSeconds = clockTamperRemainingSeconds
        )
        val target = InterventionTarget(
            packageName = policy.group.id,
            identifier = policy.group.id,
            displayName = policy.group.name,
            isWebsite = false
        )

        val effective = GroupRuleResolver.resolve(policy, now)
        val decision = if (!effective.group.sharedLimitAcrossApps && policy.appPackages.isNotEmpty()) {
            val decisions = policy.appPackages.map { engine.evaluateApp(it, snapshot, now) }
            val blocked = decisions.filterIsInstance<InterventionDecision.Block>()
            if (blocked.isNotEmpty() && blocked.size < decisions.size) return GroupStatusInfo(GroupStatus.PARTIAL)
            blocked.firstOrNull() ?: decisions.first()
        } else engine.evaluateGroup(policy, snapshot, target, now)
        return when (decision) {
            is InterventionDecision.Allow -> GroupStatusInfo(GroupStatus.FREE)
            is InterventionDecision.Challenge -> GroupStatusInfo(GroupStatus.LIMITED)
            is InterventionDecision.Block -> when (decision.reason) {
                BlockReason.STRICT_SCHEDULE -> GroupStatusInfo(
                    status = GroupStatus.STRICT,
                    blockReason = decision.reason
                )
                else -> GroupStatusInfo(
                    status = GroupStatus.BLOCKED,
                    blockReason = decision.reason
                )
            }
        }
    }
}
