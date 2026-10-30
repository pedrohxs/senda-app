package com.senda.app.ui.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.preferences.ActiveSessionStore
import com.senda.app.data.preferences.PersistedActiveSession
import com.senda.app.data.repository.AppLabels
import com.senda.app.data.repository.FocoPolicyRepository
import com.senda.app.data.repository.ProtectionHealthRepository
import com.senda.app.di.AppContainer
import com.senda.app.domain.lifecycle.ProtectionHealth
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.GroupRuleResolver
import com.senda.app.domain.policy.GroupStatus
import com.senda.app.domain.policy.GroupStatusResolver
import com.senda.app.domain.policy.GroupWindows
import com.senda.app.domain.policy.NextWindowCalculator
import com.senda.app.domain.policy.PolicySnapshot
import com.senda.app.domain.policy.UpcomingWindow
import com.senda.app.domain.security.ClockGuard
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class GroupCardUi(
    val id: String,
    val name: String,
    val colorHex: String,
    val status: GroupStatus,
    val blockReason: BlockReason?,
    val usedSeconds: Int,
    val dailyLimitMinutes: Int,
    val appPackages: List<String>,
    val hasSchedules: Boolean,
    val pausedUntilMillis: Long? = null,
    val sharedLimitAcrossApps: Boolean = true,
    val activeRuleName: String? = null
)

/** Sessão liberada e ainda em andamento. */
data class ActiveSessionUi(
    val groupName: String,
    val targetLabel: String,
    val remainingSeconds: Int
)

/** Próxima janela de bloqueio que o serviço vai aplicar. */
data class NextBlockUi(
    val groupName: String,
    val windowName: String?,
    val type: ScheduleType,
    val startMillis: Long,
    val endMillis: Long,
    val isNow: Boolean
)

data class FocusUiState(
    val isLoading: Boolean = true,
    val groups: List<GroupCardUi> = emptyList(),
    val totalUsedSeconds: Int = 0,
    val totalImpulses: Int = 0,
    val totalDailyLimitMinutes: Int = 0,
    val protection: ProtectionHealth = ProtectionHealth(),
    val showDailyProgress: Boolean = false,
    val activeSession: ActiveSessionUi? = null,
    val nextBlock: NextBlockUi? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val loadFailed: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    private val policyRepository: FocoPolicyRepository,
    private val groupStatusResolver: GroupStatusResolver,
    private val clockGuard: ClockGuard,
    protectionHealth: ProtectionHealthRepository,
    private val activeSessionStore: ActiveSessionStore,
    private val appLabels: AppLabels
) : ViewModel() {

    private val bootCount = activeSessionStore.currentBootCount()
    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<FocusUiState> = retries
        .flatMapLatest {
            combine(
                policyRepository.observeSnapshot(),
                statusTicker(),
                sessionTicker()
            ) { snapshot, _, session ->
                buildState(snapshot, session, System.currentTimeMillis())
            }.catch {
                emit(FocusUiState(isLoading = false, loadFailed = true))
            }
        }
        .combine(protectionHealth.state) { state, protection ->
            state.copy(protection = protection)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FocusUiState()
        )

    fun retry() {
        retries.value += 1
    }

    private fun buildState(
        snapshot: PolicySnapshot,
        session: PersistedActiveSession?,
        nowMillis: Long
    ): FocusUiState {
        val now = LocalDateTime.now()
        val clockStatus = clockGuard.check()
        val effectiveSnapshot = snapshot.copy(
            clockTamperRemainingSeconds = clockStatus.remainingSeconds
        )
        val groups = effectiveSnapshot.groups
            .map { policy ->
                val effective = GroupRuleResolver.resolve(policy, now)
                val status = groupStatusResolver.resolve(
                    policy = policy,
                    cooldowns = effectiveSnapshot.cooldowns,
                    clockTamperRemainingSeconds = effectiveSnapshot.clockTamperRemainingSeconds
                )
                GroupCardUi(
                    id = policy.group.id,
                    name = policy.group.name,
                    colorHex = policy.group.colorHex,
                    status = status.status,
                    blockReason = status.blockReason,
                    usedSeconds = policy.usage?.totalTimeUsedSeconds ?: 0,
                    dailyLimitMinutes = effective.group.dailyLimitMinutes,
                    appPackages = policy.appPackages.toList().sorted(),
                    hasSchedules = policy.schedules.isNotEmpty(),
                    pausedUntilMillis = policy.group.temporaryDisableUntil,
                    sharedLimitAcrossApps = effective.group.sharedLimitAcrossApps,
                    activeRuleName = policy.schedules.firstOrNull {
                        com.senda.app.core.UsageKey.forLimit(policy.group.id, it.ruleId) == effective.scopeKey
                    }?.name?.ifBlank { "Limite personalizado" }
                )
            }
            .sortedBy { it.name.lowercase() }

        return FocusUiState(
            isLoading = false,
            groups = groups,
            totalUsedSeconds = effectiveSnapshot.groups.sumOf {
                it.usage?.totalTimeUsedSeconds ?: 0
            },
            totalImpulses = effectiveSnapshot.groups.sumOf {
                it.usage?.impulsesAborted ?: 0
            },
            totalDailyLimitMinutes = effectiveSnapshot.groups.sumOf { it.group.dailyLimitMinutes },
            showDailyProgress = effectiveSnapshot.groups.singleOrNull()?.group?.let {
                it.sharedLimitAcrossApps && it.dailyLimitMinutes > 0 &&
                    groups.single().status != GroupStatus.FREE &&
                    effectiveSnapshot.groups.single().schedules.any { window ->
                        window.scheduleType == ScheduleType.LIMITED
                    } &&
                    effectiveSnapshot.groups.single().schedules.none { window -> window.customRules != null }
            } == true,
            activeSession = session?.toUi(groups),
            nextBlock = nextBlock(effectiveSnapshot, now),
            nowMillis = nowMillis
        )
    }

    private fun nextBlock(snapshot: PolicySnapshot, now: LocalDateTime): NextBlockUi? {
        val windows = snapshot.groups.mapNotNull { policy ->
            val relevant = policy.schedules.filter { it.scheduleType != ScheduleType.BREAK }
            if (relevant.isEmpty()) null else GroupWindows(policy.group.id, policy.group.name, relevant)
        }
        if (windows.isEmpty()) return null

        val plans = windows.map { group ->
            group to NextWindowCalculator.plan(
                schedules = group.schedules,
                groupId = group.groupId,
                groupName = group.groupName,
                now = now
            )
        }
        // Uma janela em curso é mais importante que a próxima do dia.
        plans.mapNotNull { (group, plan) -> plan.current?.toUi(group.groupName, true) }
            .minByOrNull { it.endMillis }
            ?.let { return it }

        return plans.mapNotNull { (group, plan) -> plan.next?.toUi(group.groupName, false) }
            .minByOrNull { it.startMillis }
    }

    private fun UpcomingWindow.toUi(groupName: String, isNow: Boolean): NextBlockUi {
        val zone = ZoneId.systemDefault()
        return NextBlockUi(
            groupName = groupName,
            windowName = name.ifBlank { null },
            type = type,
            startMillis = start.atZone(zone).toInstant().toEpochMilli(),
            endMillis = end.atZone(zone).toInstant().toEpochMilli(),
            isNow = isNow
        )
    }

    private fun PersistedActiveSession.toUi(groups: List<GroupCardUi>): ActiveSessionUi? {
        if (finished) return null
        val remaining = if (this@FocusViewModel.bootCount == this.bootCount) {
            (deadlineElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else {
            remainingMillis.coerceAtLeast(0L)
        }
        val groupName = groups.firstOrNull { it.id == groupId }?.name ?: "Grupo"
        return ActiveSessionUi(
            groupName = groupName,
            targetLabel = if (isWebsite) targetIdentifier else appLabels.label(packageName),
            remainingSeconds = (remaining / 1_000L).toInt()
        )
    }

    /** Recalcula os estados a cada minuto. */
    private fun statusTicker() = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    /** Com sessão ativa o tique é de um segundo, para o tempo restante andar. */
    private fun sessionTicker(): Flow<PersistedActiveSession?> =
        activeSessionStore.observe().flatMapLatest { session ->
            if (session == null || session.finished) {
                flowOf(null)
            } else {
                flow {
                    while (true) {
                        emit(session)
                        delay(1_000L)
                    }
                }
            }
        }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FocusViewModel(
                    policyRepository = container.policyRepository,
                    groupStatusResolver = container.groupStatusResolver,
                    clockGuard = container.clockGuard,
                    protectionHealth = container.protectionHealth,
                    activeSessionStore = container.activeSessionStore,
                    appLabels = container.appLabels
                )
            }
        }
    }
}
