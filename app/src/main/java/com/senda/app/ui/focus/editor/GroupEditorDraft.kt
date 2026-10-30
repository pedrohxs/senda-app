package com.senda.app.ui.focus.editor

import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.GroupScheduleEntity

/**
 * Retrato dos campos do editor que só valem depois de salvar.
 *
 * A pausa temporária fica de fora de propósito: ela é gravada na hora pelo
 * menu, então pausar não deve marcar o grupo como "com alterações pendentes".
 */
data class GroupEditorDraft(
    val name: String,
    val colorHex: String,
    val selectedApps: Map<String, String>,
    val otherItems: List<GroupItemEntity>,
    val dailyLimitMinutes: Int,
    val sessionLimitMinutes: Int,
    val cooldownMinutes: Int,
    val maxDailyOpens: Int,
    val sharedLimitAcrossApps: Boolean,
    val sessionCountMode: SessionCountMode,
    val interventionType: InterventionType,
    val breathingSeconds: Int,
    val mathDifficulty: MathDifficulty,
    val mathEquationCount: Int,
    val progressiveFrictionEnabled: Boolean,
    val progressiveSecondsPerOpen: Int,
    val alternativeAppPackage: String?,
    val schedules: List<ScheduleDraft>
) {
    /** Somente nome e cor podem ser alterados sem autorizar uma mudança de regras. */
    fun hasRuleChangesFrom(baseline: GroupEditorDraft): Boolean =
        copy(name = baseline.name, colorHex = baseline.colorHex) != baseline
}

fun ScheduleDraft.toScheduleEntity(groupId: String = "preview") = GroupScheduleEntity(
    groupId = groupId, scheduleType = scheduleType, daysOfWeek = daysOfWeek.distinct().sorted(),
    startHour = startHour, startMinute = startMinute, endHour = endHour, endMinute = endMinute,
    ruleId = ruleId, name = name, customRules = customRules
)

fun GroupEditorUiState.toLimitRules() = com.senda.app.data.local.entity.LimitRules(
    dailyLimitMinutes, sessionLimitMinutes, cooldownMinutes, maxDailyOpens, sharedLimitAcrossApps,
    sessionCountMode, interventionType, breathingSeconds, mathDifficulty, mathEquationCount,
    progressiveFrictionEnabled, progressiveSecondsPerOpen, alternativeAppPackage
)

fun GroupEditorUiState.toDraft(): GroupEditorDraft = GroupEditorDraft(
    name = name,
    colorHex = colorHex,
    selectedApps = selectedApps,
    otherItems = otherItems,
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
    schedules = schedules
)
