package com.senda.app.data.repository

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.ReportDayEntity
import java.time.LocalDate
import kotlin.math.roundToInt

internal object UsageStatsCalculator {
    fun calculate(
        today: LocalDate, groups: List<AppGroupEntity>, logs: List<DailyUsageLogEntity>,
        reasons: List<ChallengeReasonEntity>, days: List<ReportDayEntity>, goals: List<DailyGoalEntity>
    ): UsageStats {
        val logsByDate = logs.groupBy { it.date }
        val daysByDate = days.associateBy { it.date }
        val goalsByDate = goals.groupBy { it.date }
        val currentGroups = groups.associateBy { it.id }
        val historicalNames = goals.sortedBy { it.date }.associate { it.groupId to it.groupName }
        fun total(date: String) = logsByDate[date].orEmpty().sumOf { it.totalTimeUsedSeconds }
        fun opens(date: String) = logsByDate[date].orEmpty().sumOf { it.totalOpens }
        fun coverage(date: String) = when {
            daysByDate[date]?.hasFullCoverage == true -> DayCoverage.COMPLETE
            daysByDate[date] != null -> DayCoverage.PARTIAL
            else -> DayCoverage.UNKNOWN
        }
        fun goalResults(date: String) = goalsByDate[date].orEmpty().filter { it.limitSeconds > 0 }.map { goal ->
            val seconds = logsByDate[date].orEmpty().filter { log ->
                if (goal.shared) UsageKey.scopeKey(log.groupId) == UsageKey.scopeKey(goal.usageKey) else log.groupId == goal.usageKey
            }.sumOf { it.totalTimeUsedSeconds }
            DayGoalResult(if (goal.shared && goal.targetName == goal.groupName) goal.groupName
                else "${goal.groupName} • ${goal.targetName}", seconds, goal.limitSeconds)
        }
        // Adjacent periods of seven completed days. Today is shown separately.
        val dates = (7 downTo 1).map { today.minusDays(it.toLong()) }
        val previousDates = (14 downTo 8).map { today.minusDays(it.toLong()) }
        val complete = dates.filter { coverage(it.toString()) == DayCoverage.COMPLETE }
        val previousComplete = previousDates.filter { coverage(it.toString()) == DayCoverage.COMPLETE }
        val average = if (complete.isEmpty()) 0 else complete.sumOf { total(it.toString()) } / complete.size
        val previousAverage = if (previousComplete.isEmpty()) 0 else previousComplete.sumOf { total(it.toString()) } / previousComplete.size
        val unavailable = when {
            complete.size != 7 || previousComplete.size != 7 -> TrendUnavailableReason.INSUFFICIENT_COVERAGE
            previousAverage == 0 -> TrendUnavailableReason.PREVIOUS_ZERO
            else -> null
        }
        val trend = if (unavailable == null) ((average - previousAverage).toDouble() / previousAverage * 100).roundToInt() else null
        var streak = 0
        var cursor = today.minusDays(1)
        while (true) {
            val date = cursor.toString()
            val results = goalResults(date)
            if (coverage(date) != DayCoverage.COMPLETE || daysByDate[date]?.goalsRecorded != true || daysByDate[date]?.goalsReliable != true ||
                results.isEmpty() || results.any { !it.met }) break
            streak++
            cursor = cursor.minusDays(1)
        }
        val start = dates.first().toString()
        val end = dates.last().toString()
        val periodLogs = logs.filter { it.date in start..end }
        val completeSeconds = complete.sumOf { total(it.toString()) }
        val completeOpens = complete.sumOf { opens(it.toString()) }
        fun groupName(id: String) = currentGroups[id]?.name ?: historicalNames[id] ?: "Grupo removido"
        val summaries = (groups.map { it.id } + periodLogs.map { UsageKey.baseGroupId(it.groupId) }).distinct().map { id ->
            GroupUsageSummary(id, groupName(id), currentGroups[id]?.dailyLimitMinutes ?: 0,
                periodLogs.filter { UsageKey.baseGroupId(it.groupId) == id }.sumOf { it.totalTimeUsedSeconds }, id !in currentGroups,
                hasCustomLimits = goals.any { it.groupId == id && UsageKey.scopeKey(it.usageKey) != id })
        }.sortedByDescending { it.seconds }
        return UsageStats(
            todaySeconds = total(today.toString()),
            todayGroups = logsByDate[today.toString()].orEmpty()
                .groupBy { UsageKey.baseGroupId(it.groupId) }
                .map { (id, entries) -> DayGroupUsage(groupName(id), entries.sumOf { it.totalTimeUsedSeconds }) }
                .filter { it.seconds > 0 }.sortedByDescending { it.seconds },
            week = dates.map { date -> DailyUsageBar(date.toString(),
                listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[date.dayOfWeek.value - 1],
                total(date.toString()), false, coverage(date.toString())) },
            streakDays = streak,
            impulses = periodLogs.sumOf { it.impulsesAborted },
            weekAverageSeconds = average,
            previousWeekAverageSeconds = previousAverage,
            trendPercent = trend,
            groupSummaries = summaries,
            reasons = reasons.mapNotNull { reason -> reason.reason.trim().takeIf { it.isNotEmpty() }?.let {
                ChallengeReason(reason.packageName, reason.displayName, it, reason.createdAt)
            } },
            dayDetails = dates.map { date ->
                val key = date.toString()
                val detailGroups = logsByDate[key].orEmpty().groupBy { UsageKey.baseGroupId(it.groupId) }
                    .map { (id, entries) -> DayGroupUsage(groupName(id), entries.sumOf { it.totalTimeUsedSeconds }) }
                    .filter { it.seconds > 0 }.sortedByDescending { it.seconds }
                DailyUsageDetail(key, total(key), detailGroups, coverage(key),
                    daysByDate[key]?.goalsRecorded == true && daysByDate[key]?.goalsReliable == true, goalResults(key),
                    opens(key))
            },
            completeDays = complete.size,
            trendUnavailableReason = unavailable,
            totalOpens = periodLogs.sumOf { it.totalOpens },
            completeOpens = completeOpens,
            completeSeconds = completeSeconds,
            // Só dias completos entram: média de sessão e aberturas por dia não podem
            // ser infladas por um dia em que o Senda ficou desligado.
            averageSessionSeconds = if (completeOpens > 0) completeSeconds / completeOpens else 0,
            opensPerDay = if (complete.isNotEmpty()) completeOpens / complete.size else 0
        )
    }
}
