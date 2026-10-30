package com.senda.app.ui.focus.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.domain.policy.ScheduleTimeline
import com.senda.app.ui.theme.*
import java.util.Locale
import com.senda.app.data.local.entity.MathDifficulty

@Composable
fun GroupRulesSummary(state: GroupEditorUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.rules_summary_title), style = MaterialTheme.typography.titleMedium, color = TextWarmWhite)
            val scope = stringResource(if (state.sharedLimitAcrossApps) R.string.rules_shared_scope else R.string.rules_individual_scope)
            Text(if (state.dailyLimitMinutes == 0) stringResource(R.string.rules_daily_unlimited, scope)
                else stringResource(R.string.rules_daily_limit, state.dailyLimitMinutes, scope),
                style = MaterialTheme.typography.bodySmall, color = TextSoft)
            Text(stringResource(R.string.rules_session_limit, state.sessionLimitMinutes) + " " + stringResource(
                if (state.sessionCountMode == SessionCountMode.ACTIVE_SCREEN_TIME) R.string.rules_active_count else R.string.rules_wall_count),
                style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            if (state.schedules.any { it.customRules != null }) Text(stringResource(R.string.custom_overrides_note),
                style = MaterialTheme.typography.bodySmall, color = AccentSage)
            if (expanded) {
                val challenge = when (state.interventionType) {
                    InterventionType.BREATHING -> if (state.progressiveFrictionEnabled)
                        stringResource(R.string.rules_breathing_progressive, state.breathingSeconds, state.progressiveSecondsPerOpen)
                        else stringResource(R.string.rules_breathing, state.breathingSeconds)
                    InterventionType.MATH -> stringResource(R.string.rules_math, state.mathEquationCount,
                        stringResource(when (state.mathDifficulty) {
                            MathDifficulty.EASY -> R.string.editor_math_easy
                            MathDifficulty.MEDIUM -> R.string.editor_math_medium
                            MathDifficulty.HARD -> R.string.editor_math_hard
                        }))
                    InterventionType.REASON -> stringResource(R.string.rules_reason)
                }
                Text(challenge, style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                Text(if (state.maxDailyOpens == 0) stringResource(R.string.rules_opens_unlimited)
                    else stringResource(R.string.rules_opens, state.maxDailyOpens, scope),
                    style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                if (state.sharedLimitAcrossApps) Text(stringResource(R.string.rules_shared_switch),
                    style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                Text(if (state.cooldownMinutes == 0) stringResource(R.string.rules_no_cooldown)
                    else stringResource(R.string.rules_cooldown, state.cooldownMinutes),
                    style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                Text(stringResource(R.string.rules_existing_cooldown), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                Text(stringResource(R.string.rules_sites_separate), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.rules_hide_details else R.string.rules_show_details), color = AccentSage)
            }
        }
    }
}

@Composable
fun ScheduleConflictNotice(schedules: List<ScheduleDraft>) {
    val overlaps = remember(schedules) { ScheduleTimeline.week(schedules.map { it.toScheduleEntity() }).flatMap { it.overlaps } }
    val customConflict = remember(schedules) { com.senda.app.domain.policy.GroupRuleResolver.overlappingCustomLimits(schedules.map { it.toScheduleEntity() }) }
    if (overlaps.isEmpty() && !customConflict) return
    Surface(modifier = Modifier.fillMaxWidth(), color = ChartWarm.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, ChartWarm.copy(alpha = 0.35f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.rules_schedule_conflict_title), color = ChartWarm, style = MaterialTheme.typography.titleSmall)
            if (customConflict) Text(stringResource(R.string.custom_conflict), color = ChartWarm, style = MaterialTheme.typography.bodySmall)
            overlaps.take(3).forEach { overlap ->
                val name = stringResource(when (overlap.winner) {
                    ScheduleType.BREAK -> R.string.schedule_type_break
                    ScheduleType.STRICT -> R.string.schedule_type_strict
                    ScheduleType.LIMITED -> R.string.schedule_type_limited
                })
                Text(stringResource(R.string.rules_schedule_conflict, listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")[overlap.day - 1],
                    formatScheduleMinute(overlap.startMinute), formatScheduleMinute(overlap.endMinute), name),
                    style = MaterialTheme.typography.bodySmall, color = TextSoft)
            }
            if (overlaps.size > 3) Text(stringResource(R.string.rules_more_conflicts, overlaps.size - 3),
                style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            Text(stringResource(R.string.editor_schedule_overlap_hint), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
        }
    }
}

private fun formatScheduleMinute(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
