package com.senda.app.data.repository

import com.senda.app.data.local.dao.AppGroupDao
import com.senda.app.data.local.dao.ChallengeReasonDao
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.local.dao.ReportHistoryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate

enum class DayCoverage { COMPLETE, PARTIAL, UNKNOWN }
enum class TrendUnavailableReason { INSUFFICIENT_COVERAGE, PREVIOUS_ZERO }

data class DailyUsageBar(
    val date: String, val label: String, val seconds: Int, val isToday: Boolean,
    val coverage: DayCoverage = DayCoverage.UNKNOWN
)
data class GroupUsageSummary(
    val groupId: String, val name: String, val limitMinutes: Int, val seconds: Int,
    val isArchived: Boolean = false,
    val hasCustomLimits: Boolean = false
)
data class ChallengeReason(val packageName: String, val displayName: String, val reason: String, val createdAt: Long)
data class DayGroupUsage(val name: String, val seconds: Int)
data class DayGoalResult(val name: String, val usedSeconds: Int, val limitSeconds: Int) {
    val met: Boolean get() = usedSeconds <= limitSeconds
}
data class DailyUsageDetail(
    val date: String, val totalSeconds: Int, val groups: List<DayGroupUsage>,
    val coverage: DayCoverage = DayCoverage.UNKNOWN,
    val goalsKnown: Boolean = false,
    val goals: List<DayGoalResult> = emptyList(),
    val opens: Int = 0
)
data class UsageStats(
    val todayGroups: List<DayGroupUsage> = emptyList(),
    val todaySeconds: Int = 0,
    val week: List<DailyUsageBar> = emptyList(),
    val streakDays: Int = 0,
    val impulses: Int = 0,
    val weekAverageSeconds: Int = 0,
    val previousWeekAverageSeconds: Int = 0,
    val trendPercent: Int? = null,
    val groupSummaries: List<GroupUsageSummary> = emptyList(),
    val reasons: List<ChallengeReason> = emptyList(),
    val dayDetails: List<DailyUsageDetail> = emptyList(),
    val completeDays: Int = 0,
    val trendUnavailableReason: TrendUnavailableReason? = TrendUnavailableReason.INSUFFICIENT_COVERAGE,
    val totalOpens: Int = 0,
    val completeOpens: Int = 0,
    val completeSeconds: Int = 0,
    val averageSessionSeconds: Int = 0,
    val opensPerDay: Int = 0
)
class StatsRepository(
    private val appGroupDao: AppGroupDao,
    private val dailyUsageLogDao: DailyUsageLogDao,
    private val challengeReasonDao: ChallengeReasonDao,
    private val reportHistoryDao: ReportHistoryDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUsage(weekOffset: Int = 0): Flow<UsageStats> = todayFlow().flatMapLatest { today ->
        val reference = today.minusWeeks(weekOffset.toLong())
        combine(
            appGroupDao.observeAllGroups(), dailyUsageLogDao.observeAll(),
            challengeReasonDao.observeRecent(8), reportHistoryDao.observeDays(), reportHistoryDao.observeGoals()
        ) { groups, logs, reasons, days, goals ->
            UsageStatsCalculator.calculate(reference, groups, logs, reasons, days, goals)
        }
    }
    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(30_000L)
        }
    }.distinctUntilChanged()
}
