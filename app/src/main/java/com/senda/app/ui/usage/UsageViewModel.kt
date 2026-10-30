package com.senda.app.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.senda.app.data.repository.ChallengeReason
import com.senda.app.data.repository.DailyUsageBar
import com.senda.app.data.repository.DailyUsageDetail
import com.senda.app.data.repository.DayCoverage
import com.senda.app.data.repository.GroupUsageSummary
import com.senda.app.data.repository.StatsRepository
import com.senda.app.data.repository.TrendUnavailableReason
import com.senda.app.data.repository.UsageStats
import com.senda.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class UsageUiState(
    val todayGroups: List<com.senda.app.data.repository.DayGroupUsage> = emptyList(),
    val isLoading: Boolean = true,
    val todaySeconds: Int = 0,
    val week: List<DailyUsageBar> = emptyList(),
    val streakDays: Int = 0,
    val impulses: Int = 0,
    val weekAverageSeconds: Int = 0,
    val previousWeekAverageSeconds: Int = 0,
    val trendPercent: Int? = null,
    val groups: List<GroupUsageSummary> = emptyList(),
    val reasons: List<ChallengeReason> = emptyList(),
    val dayDetails: List<DailyUsageDetail> = emptyList(),
    val completeDays: Int = 0,
    val trendUnavailableReason: TrendUnavailableReason? = TrendUnavailableReason.INSUFFICIENT_COVERAGE,
    val totalOpens: Int = 0,
    val averageSessionSeconds: Int = 0,
    val opensPerDay: Int = 0,
    val weekOffset: Int = 0,
    val loadFailed: Boolean = false
) {
    val isCurrentWeek: Boolean get() = weekOffset == 0

    val canGoBack: Boolean get() = weekOffset < MAX_WEEK_OFFSET

    val canGoForward: Boolean get() = weekOffset > 0

    /** Sem nenhum segundo e sem nenhum dia com cobertura: não há o que relatar. */
    val isEmpty: Boolean
        get() = todaySeconds == 0 && totalOpens == 0 &&
            week.all { it.seconds == 0 && it.coverage == DayCoverage.UNKNOWN } &&
            dayDetails.all { it.totalSeconds == 0 }

    companion object {
        const val MAX_WEEK_OFFSET = 26
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModel(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val weekOffset = MutableStateFlow(0)
    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<UsageUiState> = combine(weekOffset, retries) { offset, attempt ->
        offset to attempt
    }
        .flatMapLatest { (offset, _) ->
            statsRepository.observeUsage(offset)
                .map { stats -> stats.toUi(offset) }
                .catch {
                    emit(
                        UsageUiState(
                            isLoading = false,
                            weekOffset = offset,
                            loadFailed = true
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UsageUiState()
        )

    fun previousWeek() {
        weekOffset.update { (it + 1).coerceAtMost(UsageUiState.MAX_WEEK_OFFSET) }
    }

    fun nextWeek() {
        weekOffset.update { (it - 1).coerceAtLeast(0) }
    }

    fun currentWeek() {
        weekOffset.value = 0
    }

    fun retry() {
        retries.update { it + 1 }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UsageViewModel(statsRepository = container.statsRepository)
            }
        }
    }
}

private fun UsageStats.toUi(weekOffset: Int) = UsageUiState(
    isLoading = false,
    todaySeconds = todaySeconds,
    todayGroups = todayGroups,
    week = week,
    streakDays = streakDays,
    impulses = impulses,
    weekAverageSeconds = weekAverageSeconds,
    previousWeekAverageSeconds = previousWeekAverageSeconds,
    trendPercent = trendPercent,
    groups = groupSummaries,
    reasons = reasons,
    dayDetails = dayDetails,
    completeDays = completeDays,
    trendUnavailableReason = trendUnavailableReason,
    totalOpens = totalOpens,
    averageSessionSeconds = averageSessionSeconds,
    opensPerDay = opensPerDay,
    weekOffset = weekOffset
)

/**
 * O gráfico inclui o dia em andamento na semana atual; semanas passadas já
 * estão completas e aparecem inteiras.
 */
internal fun UsageUiState.chartDays(): List<DailyUsageBar> {
    if (!isCurrentWeek) return week
    val today = week.lastOrNull()?.date?.let { runCatching { java.time.LocalDate.parse(it).plusDays(1) }.getOrNull() }
        ?: java.time.LocalDate.now()
    return week.takeLast(6) + DailyUsageBar(today.toString(), "Hoje", todaySeconds, true, DayCoverage.PARTIAL)
}
