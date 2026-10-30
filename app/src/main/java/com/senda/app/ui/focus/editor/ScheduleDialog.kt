package com.senda.app.ui.focus.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import java.util.Locale
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.data.repository.InstalledApp
import com.senda.app.domain.policy.GroupRuleResolver
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialSchedule: ScheduleDraft?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (ScheduleDraft) -> Unit,
    otherSchedules: List<ScheduleDraft> = emptyList(),
    defaultRules: LimitRules = LimitRules(),
    apps: List<InstalledApp> = emptyList()
) {
    var selectedDays by remember(initialSchedule) {
        mutableStateOf(initialSchedule?.daysOfWeek?.toSet() ?: setOf(2, 3, 4, 5, 6))
    }
    var scheduleType by remember(initialSchedule) {
        mutableStateOf(initialSchedule?.scheduleType ?: ScheduleType.LIMITED)
    }
    var startHour by remember(initialSchedule) {
        mutableIntStateOf(initialSchedule?.startHour ?: 9)
    }
    var startMinute by remember(initialSchedule) {
        mutableIntStateOf(initialSchedule?.startMinute ?: 0)
    }
    var endHour by remember(initialSchedule) {
        mutableIntStateOf(initialSchedule?.endHour ?: 18)
    }
    var endMinute by remember(initialSchedule) {
        mutableIntStateOf(initialSchedule?.endMinute ?: 0)
    }
    var preset by remember(initialSchedule) {
        mutableStateOf(
            when {
                initialSchedule == null -> SchedulePreset.CUSTOM
                initialSchedule.startHour == 0 &&
                    initialSchedule.startMinute == 0 &&
                    initialSchedule.endHour == 0 &&
                    initialSchedule.endMinute == 0 -> SchedulePreset.ALL_DAY
                initialSchedule.startHour == 6 &&
                    initialSchedule.startMinute == 0 &&
                    initialSchedule.endHour == 12 &&
                    initialSchedule.endMinute == 0 -> SchedulePreset.MORNING
                initialSchedule.startHour == 12 &&
                    initialSchedule.startMinute == 0 &&
                    initialSchedule.endHour == 18 &&
                    initialSchedule.endMinute == 0 -> SchedulePreset.AFTERNOON
                initialSchedule.startHour == 18 &&
                    initialSchedule.startMinute == 0 &&
                    initialSchedule.endHour == 23 &&
                    initialSchedule.endMinute == 59 -> SchedulePreset.EVENING
                else -> SchedulePreset.CUSTOM
            }
        )
    }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var name by remember(initialSchedule) { mutableStateOf(initialSchedule?.name.orEmpty()) }
    val ruleId = remember(initialSchedule) { initialSchedule?.ruleId ?: java.util.UUID.randomUUID().toString() }
    var customRules by remember(initialSchedule) { mutableStateOf(initialSchedule?.customRules) }
    var showCustomRules by remember { mutableStateOf(false) }
    val candidate = ScheduleDraft(selectedDays.sorted(), scheduleType, startHour, startMinute, endHour, endMinute,
        ruleId, name.trim(), if (scheduleType == ScheduleType.LIMITED) customRules else null)
    val hasCustomOverlap = remember(candidate, otherSchedules) {
        GroupRuleResolver.overlappingCustomLimits((otherSchedules + candidate).map { it.toScheduleEntity() })
    }

    fun applyPreset(newPreset: SchedulePreset) {
        preset = newPreset
        when (newPreset) {
            SchedulePreset.CUSTOM -> Unit
            SchedulePreset.ALL_DAY -> {
                startHour = 0
                startMinute = 0
                endHour = 0
                endMinute = 0
            }
            SchedulePreset.MORNING -> {
                startHour = 6
                startMinute = 0
                endHour = 12
                endMinute = 0
            }
            SchedulePreset.AFTERNOON -> {
                startHour = 12
                startMinute = 0
                endHour = 18
                endMinute = 0
            }
            SchedulePreset.EVENING -> {
                startHour = 18
                startMinute = 0
                endHour = 23
                endMinute = 59
            }
        }
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = DarkCanvas,
        contentColor = TextWarmWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(BorderMuted)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (initialSchedule == null) {
                                R.string.schedule_dialog_new
                            } else {
                                R.string.schedule_dialog_edit
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWarmWhite
                    )
                    if (initialSchedule != null) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = TextMutedGrey,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.schedule_cancel),
                        tint = TextMutedGrey
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.schedule_when_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey
            )

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SchedulePreset.entries.forEach { option ->
                    PresetChip(
                        label = stringResource(option.labelRes),
                        selected = preset == option,
                        onClick = { applyPreset(option) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TimeCard(
                    label = stringResource(R.string.schedule_start),
                    hour = startHour,
                    minute = startMinute,
                    modifier = Modifier.weight(1f),
                    onClick = { showStartPicker = true }
                )
                TimeCard(
                    label = stringResource(R.string.schedule_end),
                    hour = endHour,
                    minute = endMinute,
                    modifier = Modifier.weight(1f),
                    onClick = { showEndPicker = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.schedule_days_label),
                style = MaterialTheme.typography.labelMedium,
                color = TextMutedGrey
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DAY_SHORT_LABELS.forEachIndexed { index, label ->
                    val day = index + 1
                    DayPill(
                        label = label,
                        selected = day in selectedDays,
                        onClick = {
                            selectedDays = if (day in selectedDays) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.schedule_type_label),
                style = MaterialTheme.typography.labelMedium,
                color = TextMutedGrey
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleTypeChip(
                    label = stringResource(R.string.schedule_type_limited),
                    color = AccentSage,
                    selected = scheduleType == ScheduleType.LIMITED,
                    onClick = { scheduleType = ScheduleType.LIMITED },
                    modifier = Modifier.weight(1f)
                )
                ScheduleTypeChip(
                    label = stringResource(R.string.schedule_type_strict),
                    color = ChartWarm,
                    selected = scheduleType == ScheduleType.STRICT,
                    onClick = { scheduleType = ScheduleType.STRICT },
                    modifier = Modifier.weight(1f)
                )
            }

            if (scheduleType == ScheduleType.BREAK) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.schedule_pause_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }

            if (scheduleType == ScheduleType.LIMITED) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(name, { name = it.take(60) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.custom_name)) }, singleLine = true)
                Row(Modifier.fillMaxWidth().toggleable(value = customRules != null, role = Role.Switch, onValueChange = { enabled ->
                    if (enabled) showCustomRules = true else customRules = null
                }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.custom_use_own), Modifier.weight(1f), color = TextWarmWhite)
                    Switch(checked = customRules != null, onCheckedChange = null)
                }
                val rules = customRules
                Text(if (rules == null) stringResource(R.string.custom_inherit)
                    else if (rules.dailyLimitMinutes == 0) stringResource(R.string.custom_summary_unlimited, rules.sessionLimitMinutes, rules.cooldownMinutes)
                    else stringResource(R.string.custom_summary, rules.dailyLimitMinutes, rules.sessionLimitMinutes, rules.cooldownMinutes),
                    style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                if (rules != null) TextButton(onClick = { showCustomRules = true }) {
                    Text(stringResource(R.string.custom_edit), color = AccentSage)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (startHour == endHour && startMinute == endMinute) {
                Text(stringResource(R.string.rules_equal_times), style = MaterialTheme.typography.bodySmall, color = ChartWarm)
            } else if (endHour * 60 + endMinute < startHour * 60 + startMinute) {
                Text(stringResource(R.string.rules_overnight), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            }
            Spacer(modifier = Modifier.height(10.dp))
            ScheduleConflictNotice(otherSchedules + candidate)
            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    Surface(
                        onClick = onDelete,
                        shape = RoundedCornerShape(18.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderMuted)
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_delete_window),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Button(
                    onClick = {
                        onConfirm(
                            candidate
                        )
                    },
                    enabled = selectedDays.isNotEmpty() && !hasCustomOverlap,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSage,
                        contentColor = DarkCanvas,
                        disabledContainerColor = BorderMuted,
                        disabledContentColor = TextMutedGrey
                    )
                ) {
                    Text(
                        text = stringResource(R.string.schedule_save),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    if (showCustomRules) {
        CustomLimitRulesDialog(customRules ?: defaultRules, apps, onDismiss = { showCustomRules = false },
            onConfirm = { customRules = it; showCustomRules = false })
    }
    if (showStartPicker) {
        MinimalTimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                preset = SchedulePreset.CUSTOM
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        MinimalTimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                preset = SchedulePreset.CUSTOM
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) AccentSage.copy(alpha = 0.14f) else CardSurface,
        border = BorderStroke(
            1.dp,
            if (selected) AccentSage.copy(alpha = 0.7f) else BorderMuted
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) TextWarmWhite else TextMutedGrey
        )
    }
}

@Composable
private fun TimeCard(
    label: String,
    hour: Int,
    minute: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedGrey
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute),
                style = MaterialTheme.typography.headlineSmall,
                color = TextWarmWhite
            )
        }
    }
}

@Composable
private fun DayPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = if (selected) AccentSage else CardSurface,
        border = BorderStroke(
            1.dp,
            if (selected) AccentSage else BorderMuted
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) DarkCanvas else TextMutedGrey
            )
        }
    }
}

@Composable
private fun ScheduleTypeChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentSage.copy(alpha = 0.10f) else CardSurface,
        border = BorderStroke(
            1.dp,
            if (selected) color.copy(alpha = 0.7f) else BorderMuted
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) TextWarmWhite else TextMutedGrey,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCanvas,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                text = stringResource(R.string.schedule_time_picker_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(state.hour, state.minute) }
            ) {
                Text(
                    text = stringResource(R.string.schedule_save),
                    color = AccentSage
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.schedule_cancel),
                    color = TextMutedGrey
                )
            }
        }
    )
}

private enum class SchedulePreset(val labelRes: Int) {
    CUSTOM(R.string.schedule_preset_custom),
    ALL_DAY(R.string.schedule_preset_all_day),
    MORNING(R.string.schedule_preset_morning),
    AFTERNOON(R.string.schedule_preset_afternoon),
    EVENING(R.string.schedule_preset_evening)
}

private val DAY_SHORT_LABELS = listOf("D", "S", "T", "Q", "Q", "S", "S")
