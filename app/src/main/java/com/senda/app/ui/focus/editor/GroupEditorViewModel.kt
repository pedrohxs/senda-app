package com.senda.app.ui.focus.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.repository.GroupRepository
import com.senda.app.data.repository.InstalledApp
import com.senda.app.data.repository.InstalledAppsRepository
import com.senda.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.domain.policy.GroupRuleResolver
import com.senda.app.domain.setup.SetupGoal

private const val DEFAULT_GROUP_COLOR = "#C6D29E"

data class ScheduleDraft(
    val daysOfWeek: List<Int>,
    val scheduleType: ScheduleType,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val ruleId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val customRules: LimitRules? = null
)

data class GroupEditorUiState(
    val isNew: Boolean,
    val groupId: String,
    val name: String = "",
    val colorHex: String = DEFAULT_GROUP_COLOR,
    val selectedApps: Map<String, String> = emptyMap(),
    val otherItems: List<GroupItemEntity> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val isLoadingApps: Boolean = true,
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
    val temporaryDisableUntil: Long? = null,
    val schedules: List<ScheduleDraft> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val baseline: GroupEditorDraft? = null
) {
    val isValid: Boolean
        get() = name.isNotBlank() && selectedApps.isNotEmpty()

    val hasUnsavedChanges: Boolean
        get() = baseline?.let { it != toDraft() } ?: false

    val hasRuleChanges: Boolean
        get() = baseline?.let { toDraft().hasRuleChangesFrom(it) } ?: true

    val alternativeAppLabel: String?
        get() = alternativeAppPackage?.let { packageName ->
            installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
        }
}

class GroupEditorViewModel(
    groupId: String,
    private val isNew: Boolean = false,
    private val groupRepository: GroupRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    starterGoal: SetupGoal? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GroupEditorUiState(
            isNew = isNew,
            groupId = groupId,
            name = if (isNew) starterGoal?.suggestedGroupName.orEmpty() else "",
            dailyLimitMinutes = if (isNew) starterGoal?.dailyMinutes ?: 30 else 30,
            sessionLimitMinutes = if (isNew) starterGoal?.sessionMinutes ?: 5 else 5,
            cooldownMinutes = if (isNew) starterGoal?.cooldownMinutes ?: 10 else 10
        )
    )
    val uiState: StateFlow<GroupEditorUiState> = _uiState.asStateFlow()

    init {
        loadInstalledApps()
        if (!isNew) {
            loadGroup()
        } else {
            // Grupo novo: o baseline são os valores padrão do formulário.
            _uiState.update { it.copy(baseline = it.toDraft()) }
        }
    }

    fun setName(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun setColor(colorHex: String) {
        _uiState.update { it.copy(colorHex = colorHex) }
    }

    fun toggleApp(app: InstalledApp) {
        _uiState.update { state ->
            val selected = state.selectedApps.toMutableMap()
            if (selected.containsKey(app.packageName)) {
                selected.remove(app.packageName)
            } else {
                selected[app.packageName] = app.label
            }
            state.copy(selectedApps = selected)
        }
    }

    fun setDailyLimit(minutes: Int) {
        _uiState.update { it.copy(dailyLimitMinutes = minutes.coerceIn(0, 240)) }
    }

    fun setSessionLimit(minutes: Int) {
        _uiState.update { it.copy(sessionLimitMinutes = minutes.coerceIn(1, 60)) }
    }

    fun setCooldown(minutes: Int) {
        _uiState.update { it.copy(cooldownMinutes = minutes.coerceIn(0, 120)) }
    }

    fun setMaxDailyOpens(opens: Int) {
        _uiState.update { it.copy(maxDailyOpens = opens.coerceIn(0, 50)) }
    }

    fun setSharedLimitAcrossApps(shared: Boolean) {
        _uiState.update { it.copy(sharedLimitAcrossApps = shared) }
    }

    fun setSessionCountMode(mode: SessionCountMode) {
        _uiState.update { it.copy(sessionCountMode = mode) }
    }

    fun setInterventionType(type: InterventionType) {
        _uiState.update { it.copy(interventionType = type) }
    }

    fun setBreathingSeconds(seconds: Int) {
        _uiState.update { it.copy(breathingSeconds = seconds.coerceIn(5, 60)) }
    }

    fun setProgressiveFriction(enabled: Boolean) {
        _uiState.update { it.copy(progressiveFrictionEnabled = enabled) }
    }

    fun setProgressiveSeconds(seconds: Int) {
        _uiState.update { it.copy(progressiveSecondsPerOpen = seconds.coerceIn(1, 30)) }
    }

    fun setMathDifficulty(difficulty: MathDifficulty) {
        _uiState.update { it.copy(mathDifficulty = difficulty) }
    }

    fun setMathEquationCount(count: Int) {
        _uiState.update { it.copy(mathEquationCount = count.coerceIn(3, 20)) }
    }

    fun setAlternativeApp(packageName: String?) {
        _uiState.update { it.copy(alternativeAppPackage = packageName) }
    }

    /**
     * Pausa o grupo até o horário informado, ou retoma imediatamente com null.
     * A gravação é imediata para o efeito valer sem precisar salvar o editor.
     */
    fun setTemporaryDisable(untilMillis: Long?) {
        val state = _uiState.value
        if (state.isNew) return

        viewModelScope.launch {
            runCatching {
                groupRepository.setTemporaryDisableUntil(state.groupId, untilMillis)
            }.onSuccess {
                _uiState.update { it.copy(temporaryDisableUntil = untilMillis) }
            }.onFailure {
                _uiState.update {
                    it.copy(errorMessage = "Não foi possível alterar a pausa do grupo.")
                }
            }
        }
    }

    fun addSchedule(schedule: ScheduleDraft) {
        _uiState.update { it.copy(schedules = it.schedules + schedule) }
    }

    fun updateSchedule(index: Int, schedule: ScheduleDraft) {
        _uiState.update { state ->
            if (index !in state.schedules.indices) {
                state
            } else {
                state.copy(
                    schedules = state.schedules.toMutableList().apply {
                        this[index] = schedule
                    }
                )
            }
        }
    }

    fun removeSchedule(index: Int) {
        _uiState.update { state ->
            if (index !in state.schedules.indices) {
                state
            } else {
                state.copy(
                    schedules = state.schedules.toMutableList().apply {
                        removeAt(index)
                    }
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid || state.isSaving || state.isLoading || (!state.isNew && state.baseline == null)) return
        if (GroupRuleResolver.overlappingCustomLimits(state.toSchedules())) {
            _uiState.update { it.copy(errorMessage = "Dois limites personalizados se sobrepõem. Ajuste os horários antes de salvar.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                when {
                    state.isNew -> groupRepository.saveGroup(state.toEntity(), state.toItems(), state.toSchedules())
                    state.hasRuleChanges -> groupRepository.updateGroup(state.toEntity(), state.toItems(), state.toSchedules())
                    else -> groupRepository.updateAppearance(state.groupId, state.name.trim(), state.colorHex)
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Não foi possível salvar o grupo."
                    )
                }
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.isNew || state.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            runCatching {
                groupRepository.deleteGroup(state.groupId)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, deleted = true) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Não foi possível excluir o grupo."
                    )
                }
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = runCatching {
                installedAppsRepository.getLaunchableApps()
            }.getOrDefault(emptyList())

            _uiState.update {
                it.copy(installedApps = apps, isLoadingApps = false)
            }
        }
    }

    private fun loadGroup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val data = runCatching {
                groupRepository.getGroupForEdit(_uiState.value.groupId)
            }.getOrNull()

            if (data == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Grupo não encontrado."
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isNew = false,
                    groupId = data.group.id,
                    name = data.group.name,
                    colorHex = data.group.colorHex,
                    selectedApps = data.items
                        .filter { item -> item.targetType == TargetType.APP }
                        .associate { item ->
                            item.targetIdentifier to item.displayName
                        },
                    otherItems = data.items.filter { item ->
                        item.targetType != TargetType.APP
                    },
                    dailyLimitMinutes = data.group.dailyLimitMinutes,
                    sessionLimitMinutes = data.group.sessionLimitMinutes,
                    cooldownMinutes = data.group.cooldownMinutes,
                    maxDailyOpens = data.group.maxDailyOpens,
                    sharedLimitAcrossApps = data.group.sharedLimitAcrossApps,
                    sessionCountMode = data.group.sessionCountMode,
                    interventionType = data.group.interventionType,
                    breathingSeconds = data.group.breathingSeconds,
                    mathDifficulty = data.group.mathDifficulty,
                    mathEquationCount = data.group.mathEquationCount,
                    progressiveFrictionEnabled = data.group.progressiveFrictionEnabled,
                    progressiveSecondsPerOpen = data.group.progressiveSecondsPerOpen,
                    alternativeAppPackage = data.group.alternativeAppPackage,
                    temporaryDisableUntil = data.group.temporaryDisableUntil,
                    schedules = data.schedules.map { schedule ->
                        ScheduleDraft(
                            daysOfWeek = schedule.daysOfWeek,
                            scheduleType = schedule.scheduleType,
                            startHour = schedule.startHour,
                            startMinute = schedule.startMinute,
                            endHour = schedule.endHour,
                            endMinute = schedule.endMinute,
                            ruleId = schedule.ruleId,
                            name = schedule.name,
                            customRules = schedule.customRules
                        )
                    },
                    isLoading = false
                )
            }

            // Baseline só depois do grupo carregado, para não marcar tudo como alterado.
            _uiState.update { it.copy(baseline = it.toDraft()) }
        }
    }

    private fun GroupEditorUiState.toEntity() = AppGroupEntity(
        id = groupId,
        name = name.trim(),
        colorHex = colorHex,
        isEnabled = true,
        dailyLimitMinutes = dailyLimitMinutes,
        sessionLimitMinutes = sessionLimitMinutes,
        cooldownMinutes = cooldownMinutes,
        maxDailyOpens = maxDailyOpens,
        sharedLimitAcrossApps = sharedLimitAcrossApps,
        sessionCountMode = sessionCountMode,
        interventionType = interventionType,
        breathingSeconds = breathingSeconds,
        mathDifficulty = mathDifficulty,
        mathEquationCount = mathEquationCount,
        progressiveFrictionEnabled = progressiveFrictionEnabled,
        progressiveSecondsPerOpen = progressiveSecondsPerOpen,
        alternativeAppPackage = alternativeAppPackage,
        temporaryDisableUntil = temporaryDisableUntil
    )

    private fun GroupEditorUiState.toItems() =
        selectedApps.map { (packageName, label) ->
            GroupItemEntity(
                groupId = groupId,
                targetType = TargetType.APP,
                targetIdentifier = packageName,
                displayName = label
            )
        } + otherItems

    private fun GroupEditorUiState.toSchedules() = schedules.map { it.toScheduleEntity(groupId) }

    companion object {
        fun factory(
            container: AppContainer,
            groupId: String?,
            starterGoal: SetupGoal? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GroupEditorViewModel(
                    groupId = groupId ?: UUID.randomUUID().toString(),
                    isNew = groupId == null,
                    groupRepository = container.groupRepository,
                    installedAppsRepository = container.installedAppsRepository,
                    starterGoal = starterGoal
                )
            }
        }
    }
}
