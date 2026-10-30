package com.senda.app.domain.policy

import com.senda.app.core.UrlMatcher
import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.TargetType

data class GroupPolicy(
    val group: AppGroupEntity,
    val items: List<GroupItemEntity>,
    val schedules: List<GroupScheduleEntity>,
    val usage: DailyUsageLogEntity?,
    val usageByTarget: Map<String, DailyUsageLogEntity> = emptyMap(),
    val usageLogs: List<DailyUsageLogEntity> = emptyList()
) {

    val appPackages: Set<String> = items
        .filter { it.targetType == TargetType.APP }
        .map { it.targetIdentifier }
        .toSet()

    val domains: Set<String> = items
        .filter { it.targetType == TargetType.WEBSITE }
        .mapNotNull { UrlMatcher.extractHost(it.targetIdentifier) }
        .toSet()

    val keywords: Set<String> = items
        .filter { it.targetType == TargetType.KEYWORD }
        .map { it.targetIdentifier.lowercase() }
        .toSet()

    fun usageFor(targetIdentifier: String): DailyUsageLogEntity? =
        if (group.sharedLimitAcrossApps) {
            usage
        } else {
            usageByTarget[targetIdentifier]
        }

    fun scoped(scopeKey: String, effectiveGroup: AppGroupEntity, today: java.time.LocalDate? = null): GroupPolicy {
        if (today != null) {
            val date = today.toString()
            return copy(usage = usage?.takeIf { it.date == date },
                usageByTarget = usageByTarget.filterValues { it.date == date },
                usageLogs = usageLogs.filter { it.date == date }).scoped(scopeKey, effectiveGroup)
        }
        if (usageLogs.isEmpty()) return copy(group = effectiveGroup,
            usage = if (scopeKey == group.id) usage else null,
            usageByTarget = if (scopeKey == group.id) usageByTarget else emptyMap())
        val rows = usageLogs.filter { UsageKey.scopeKey(it.groupId) == scopeKey }
        return copy(group = effectiveGroup, usage = PolicySnapshot.aggregateUsage(scopeKey, rows),
            usageByTarget = rows.mapNotNull { row -> UsageKey.targetIdentifier(row.groupId)?.let { it to row } }.toMap(),
            usageLogs = emptyList())
    }

    fun displayNameFor(identifier: String): String {
        val normalizedIdentifier = UrlMatcher.extractHost(identifier) ?: identifier
        return items.firstOrNull { item ->
            when (item.targetType) {
                TargetType.APP -> item.targetIdentifier == identifier
                TargetType.WEBSITE ->
                    UrlMatcher.extractHost(item.targetIdentifier) == normalizedIdentifier
                TargetType.KEYWORD ->
                    item.targetIdentifier.equals(identifier, ignoreCase = true)
            }
        }?.displayName ?: identifier
    }
}

data class PolicySnapshot(
    val groups: List<GroupPolicy>,
    val cooldowns: Map<String, Long>,
    val clockTamperRemainingSeconds: Long = 0L
) {

    val clockTampered: Boolean
        get() = clockTamperRemainingSeconds > 0L

    private val groupsByPackage: Map<String, List<GroupPolicy>> = groups
        .flatMap { policy -> policy.appPackages.map { packageName -> packageName to policy } }
        .groupBy({ it.first }, { it.second })

    private val groupsByDomain: Map<String, List<GroupPolicy>> = groups
        .flatMap { policy -> policy.domains.map { domain -> domain to policy } }
        .groupBy({ it.first }, { it.second })

    val hasBlockedDomains: Boolean = groupsByDomain.isNotEmpty()

    val hasKeywords: Boolean = groups.any { it.keywords.isNotEmpty() }

    val allKeywords: Set<String> = groups
        .flatMap { it.keywords }
        .toSet()

    fun groupsForPackage(packageName: String): List<GroupPolicy> =
        groupsByPackage[packageName].orEmpty()

    fun groupsForKeyword(keyword: String): List<GroupPolicy> =
        groups.filter { policy -> policy.keywords.any { it.equals(keyword, ignoreCase = true) } }

    fun groupsForHost(host: String): List<GroupPolicy> {
        val normalizedHost = host.lowercase().removePrefix("www.")
        return groupsByDomain.entries
            .filter { (domain, _) ->
                normalizedHost == domain || normalizedHost.endsWith(".$domain")
            }
            .flatMap { it.value }
            .distinctBy { it.group.id }
    }

    companion object {

        fun build(
            groups: List<AppGroupEntity>,
            items: List<GroupItemEntity>,
            schedules: List<GroupScheduleEntity>,
            usage: List<DailyUsageLogEntity>,
            cooldowns: Map<String, Long>,
            clockTamperRemainingSeconds: Long = 0L
        ): PolicySnapshot {
            val itemsByGroup = items.groupBy { it.groupId }
            val schedulesByGroup = schedules.groupBy { it.groupId }

            val policies = groups.map { group ->
                val allGroupUsage = usage.filter { entry ->
                    UsageKey.baseGroupId(entry.groupId) == group.id
                }
                val usageByTarget = allGroupUsage
                    .mapNotNull { entry ->
                        UsageKey.targetIdentifier(entry.groupId)?.let { target -> target to entry }
                    }
                    .toMap()

                GroupPolicy(
                    group = group,
                    items = itemsByGroup[group.id].orEmpty(),
                    schedules = schedulesByGroup[group.id].orEmpty(),
                    usage = aggregateUsage(group.id, allGroupUsage),
                    usageByTarget = usageByTarget,
                    usageLogs = allGroupUsage
                )
            }

            return PolicySnapshot(
                groups = policies,
                cooldowns = cooldowns,
                clockTamperRemainingSeconds = clockTamperRemainingSeconds
            )
        }

        internal fun aggregateUsage(
            groupId: String,
            entries: List<DailyUsageLogEntity>
        ): DailyUsageLogEntity? {
            if (entries.isEmpty()) return null
            val seconds = entries.sumOf { it.totalTimeUsedSeconds }
            return DailyUsageLogEntity(
                date = entries.first().date,
                groupId = groupId,
                totalTimeUsedMinutes = seconds / 60,
                totalTimeUsedSeconds = seconds,
                totalOpens = entries.sumOf { it.totalOpens },
                impulsesAborted = entries.sumOf { it.impulsesAborted }
            )
        }
    }
}
