package com.senda.app.ui.focus.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.graphics.toColorInt
import com.senda.app.R
import com.senda.app.core.FocoPackages
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.preferences.GuardMode
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.di.AppContainer
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.components.SendaDashedButton
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaRow
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.security.CriticalActionGuardDialog
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartInactive
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import com.senda.app.domain.policy.ScheduleTimeline
import kotlin.math.roundToInt

@Composable
fun GroupEditorRoute(
    container: AppContainer,
    groupId: String?,
    sessionKey: Int,
    onDone: () -> Unit,
    starterGoal: com.senda.app.domain.setup.SetupGoal? = null
) {
    val viewModel: GroupEditorViewModel = viewModel(
        key = "group-editor-${groupId ?: "new"}-$sessionKey",
        factory = GroupEditorViewModel.factory(container, groupId, starterGoal)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }

    val requestClose = {
        if (state.hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onDone()
        }
    }

    BackHandler(onBack = requestClose)

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) {
            onDone()
        }
    }

    GroupEditorScreen(
        state = state,
        onBack = requestClose,
        viewModel = viewModel,
        protectionStore = container.protectionStore
    )

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = {
                Text(stringResource(R.string.editor_discard_title))
            },
            text = {
                Text(stringResource(R.string.editor_discard_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDone()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.editor_discard_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        text = stringResource(R.string.editor_discard_keep),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }
}

private data class GroupGuardRequest(
    val target: String,
    val title: String,
    val description: String,
    val onAuthorized: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupEditorScreen(
    state: GroupEditorUiState,
    onBack: () -> Unit,
    viewModel: GroupEditorViewModel,
    protectionStore: ProtectionStore
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var showAlternativePicker by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingScheduleIndex by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var guardRequest by remember { mutableStateOf<GroupGuardRequest?>(null) }
    var editingNumberField by remember { mutableStateOf<NumericEditField?>(null) }
    var showLimitsSheet by remember { mutableStateOf(false) }
    var showChallengeSheet by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val saveGuardTitle = stringResource(R.string.guard_action_group_save_title)
    val saveGuardMessage = stringResource(R.string.guard_action_group_save_message)
    val deleteGuardTitle = stringResource(R.string.guard_action_group_delete_title)
    val deleteGuardMessage = stringResource(R.string.guard_action_group_delete_message)
    val pauseGuardTitle = stringResource(R.string.guard_action_group_pause_title)
    val pauseGuardMessage = stringResource(R.string.guard_action_group_pause_message)

    fun requestGuarded(
        target: String,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        if (protectionStore.guardMode() == GuardMode.NONE) {
            action()
        } else {
            guardRequest = GroupGuardRequest(target, title, description, action)
        }
    }

    fun saveGroup() {
        if (state.isNew || !state.hasRuleChanges) {
            viewModel.save()
        } else {
            requestGuarded(
                target = "group:${state.groupId}",
                title = saveGuardTitle,
                description = saveGuardMessage
            ) {
                viewModel.save()
            }
        }
    }

    fun deleteGroup() {
        requestGuarded(
            target = "group:${state.groupId}",
            title = deleteGuardTitle,
            description = deleteGuardMessage
        ) {
            viewModel.delete()
        }
    }

    // Retomar é imediato; pausar exige autorização quando o Modo Blindado está ativo.
    fun pauseGroup(untilMillis: Long?) {
        if (untilMillis == null) {
            viewModel.setTemporaryDisable(null)
            return
        }

        requestGuarded(
            target = "group:${state.groupId}",
            title = pauseGuardTitle,
            description = pauseGuardMessage
        ) {
            viewModel.setTemporaryDisable(untilMillis)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                RoundIconButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.editor_more_rename),
                    onClick = { showMoreMenu = true }
                )
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = CardSurface
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.editor_more_rename),
                                color = TextWarmWhite
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            showNameDialog = true
                        }
                    )
                    if (!state.isNew) {
                        val now = System.currentTimeMillis()
                        val pausedUntil = state.temporaryDisableUntil?.takeIf { it > now }

                        if (pausedUntil != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.editor_more_resume),
                                        color = TextWarmWhite
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    pauseGroup(null)
                                }
                            )
                        } else {
                            listOf(
                                R.string.editor_more_pause_1h to 1L,
                                R.string.editor_more_pause_4h to 4L
                            ).forEach { (labelRes, hours) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(labelRes),
                                            color = TextWarmWhite
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        pauseGroup(System.currentTimeMillis() + hours * 3_600_000L)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.editor_more_pause_tomorrow),
                                        color = TextWarmWhite
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    pauseGroup(tomorrowStartMillis())
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.editor_more_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showDeleteConfirmation = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .weight(2f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showNameDialog = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.name.ifBlank {
                        stringResource(R.string.editor_header_new_group)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.editor_name_label),
                    tint = TextMutedGrey,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            RoundIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.editor_cancel),
                onClick = onBack
            )
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentSage)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderMuted)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OverlappingAppIcons(
                            packages = state.selectedApps.keys.toList()
                        )

                        Spacer(modifier = Modifier.size(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            val count = state.selectedApps.size
                            Text(
                                text = when (count) {
                                    0 -> stringResource(R.string.editor_section_apps)
                                    1 -> stringResource(R.string.editor_apps_count_one, count)
                                    else -> stringResource(R.string.editor_apps_count, count)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWarmWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.selectedApps.isEmpty()) {
                                    stringResource(R.string.editor_tap_to_choose)
                                } else {
                                    state.selectedApps.values.joinToString(", ")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMutedGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = TextMutedGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (state.selectedApps.keys.any { FocoPackages.isBrowser(it) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.editor_apps_browser_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedGrey
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderMuted)
                ) {
                    Column {
                        SendaRow(
                            icon = Icons.Outlined.TrackChanges,
                            title = stringResource(R.string.editor_limits_sheet_title),
                            trailingText = if (state.dailyLimitMinutes == 0) {
                                stringResource(R.string.editor_no_limit)
                            } else {
                                stringResource(
                                    R.string.editor_minutes_format,
                                    state.dailyLimitMinutes
                                )
                            },
                            onClick = { showLimitsSheet = true }
                        )
                        SendaRow(
                            icon = Icons.Outlined.Key,
                            title = stringResource(R.string.editor_challenge_sheet_title),
                            trailingText = interventionLabel(state.interventionType),
                            showDivider = true,
                            onClick = { showChallengeSheet = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderMuted)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.editor_weekly_schedule),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextWarmWhite
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WeeklyScheduleChart(schedules = state.schedules)
                        Spacer(modifier = Modifier.height(16.dp))
                        ScheduleLegend()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(stringResource(R.string.editor_schedule_fallback_hint),
                            style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                ScheduleConflictNotice(state.schedules)

                if (state.schedules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderMuted)
                    ) {
                        Column {
                            state.schedules.forEachIndexed { index, schedule ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 36.dp),
                                        color = BorderMuted
                                    )
                                }
                                ScheduleWindowRow(
                                    schedule = schedule,
                                    onClick = {
                                        editingScheduleIndex = index
                                        showScheduleDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SendaDashedButton(
                    text = stringResource(R.string.editor_schedule_add),
                    onClick = {
                        editingScheduleIndex = null
                        showScheduleDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                SendaSectionLabel(text = stringResource(R.string.editor_other_options))
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderMuted)
                ) {
                    Column {
                        SendaRow(
                            icon = Icons.Outlined.Edit,
                            title = stringResource(R.string.editor_name_appearance),
                            subtitle = state.name.ifBlank {
                                stringResource(R.string.editor_header_new_group)
                            },
                            onClick = { showNameDialog = true }
                        )
                        SendaRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = stringResource(R.string.editor_section_alternative),
                            subtitle = state.alternativeAppLabel
                                ?: stringResource(R.string.editor_alternative_none),
                            trailingText = stringResource(R.string.editor_alternative_switch),
                            showDivider = true,
                            onClick = { showAlternativePicker = true }
                        )
                        if (state.alternativeAppPackage != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = BorderMuted
                            )
                            TextButton(
                                onClick = { viewModel.setAlternativeApp(null) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_remove_alternative),
                                    color = TextMutedGrey,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                if (state.errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            Surface(
                color = DarkCanvas,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    SendaPrimaryButton(
                        text = stringResource(R.string.editor_save),
                        onClick = ::saveGroup,
                        enabled = state.isValid && !state.isSaving
                    )
                }
            }
        }
    }

    if (showLimitsSheet) {
        LimitsSheet(
            state = state,
            viewModel = viewModel,
            onEditNumber = { field ->
                showLimitsSheet = false
                editingNumberField = field
            },
            onDismiss = { showLimitsSheet = false }
        )
    }

    if (showChallengeSheet) {
        ChallengeSheet(
            state = state,
            viewModel = viewModel,
            onEditNumber = { field ->
                showChallengeSheet = false
                editingNumberField = field
            },
            onDismiss = { showChallengeSheet = false }
        )
    }

    if (showNameDialog) {
        GroupIdentityDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showNameDialog = false }
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            title = stringResource(R.string.app_picker_add_title),
            apps = state.installedApps,
            selectedPackages = state.selectedApps.keys,
            isLoading = state.isLoadingApps,
            onToggle = viewModel::toggleApp,
            onDismiss = { showAppPicker = false }
        )
    }

    if (showAlternativePicker) {
        AppPickerDialog(
            title = stringResource(R.string.app_picker_alternative_title),
            apps = state.installedApps,
            selectedPackages = setOfNotNull(state.alternativeAppPackage),
            isLoading = state.isLoadingApps,
            singleSelect = true,
            onToggle = { app -> viewModel.setAlternativeApp(app.packageName) },
            onDismiss = { showAlternativePicker = false }
        )
    }

    if (showScheduleDialog) {
        val initialSchedule = editingScheduleIndex?.let { index ->
            state.schedules.getOrNull(index)
        }
        ScheduleDialog(
            initialSchedule = initialSchedule,
            onDismiss = {
                showScheduleDialog = false
                editingScheduleIndex = null
            },
            onDelete = editingScheduleIndex?.let { index ->
                {
                    viewModel.removeSchedule(index)
                    showScheduleDialog = false
                    editingScheduleIndex = null
                }
            },
            onConfirm = { schedule ->
                val index = editingScheduleIndex
                if (index == null) {
                    viewModel.addSchedule(schedule)
                } else {
                    viewModel.updateSchedule(index, schedule)
                }
                showScheduleDialog = false
                editingScheduleIndex = null
            },
            otherSchedules = state.schedules.filterIndexed { index, _ -> index != editingScheduleIndex },
            defaultRules = state.toLimitRules(),
            apps = state.installedApps
        )
    }

    editingNumberField?.let { field ->
        val config = when (field) {
            NumericEditField.DAILY_LIMIT -> NumericEditConfig(
                title = stringResource(R.string.editor_daily_limit),
                description = stringResource(R.string.editor_custom_daily_description),
                value = state.dailyLimitMinutes,
                range = 0..240,
                onConfirm = viewModel::setDailyLimit
            )
            NumericEditField.SESSION_LIMIT -> NumericEditConfig(
                title = stringResource(R.string.editor_session_limit),
                description = stringResource(R.string.editor_custom_session_description),
                value = state.sessionLimitMinutes,
                range = 1..60,
                onConfirm = viewModel::setSessionLimit
            )
            NumericEditField.COOLDOWN -> NumericEditConfig(
                title = stringResource(R.string.editor_cooldown),
                description = stringResource(R.string.editor_custom_cooldown_description),
                value = state.cooldownMinutes,
                range = 0..120,
                onConfirm = viewModel::setCooldown
            )
            NumericEditField.MAX_OPENS -> NumericEditConfig(
                title = stringResource(R.string.editor_max_opens),
                description = stringResource(R.string.editor_custom_opens_description),
                value = state.maxDailyOpens,
                range = 0..50,
                onConfirm = viewModel::setMaxDailyOpens
            )
            NumericEditField.MATH_EQUATION_COUNT -> NumericEditConfig(
                title = stringResource(R.string.editor_math_equations_label),
                description = stringResource(R.string.editor_math_minimum_hint),
                value = state.mathEquationCount,
                range = 3..20,
                onConfirm = viewModel::setMathEquationCount
            )
        }
        NumberEditDialog(
            config = config,
            onDismiss = { editingNumberField = null },
            onConfirm = { value ->
                config.onConfirm(value)
                editingNumberField = null
            }
        )
    }

    guardRequest?.let { request ->
        CriticalActionGuardDialog(
            protectionStore = protectionStore,
            target = request.target,
            title = request.title,
            description = request.description,
            onDismiss = { guardRequest = null },
            onAuthorized = {
                guardRequest = null
                request.onAuthorized()
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = {
                Text(stringResource(R.string.editor_delete_title))
            },
            text = {
                Text(stringResource(R.string.editor_delete_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.delete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.editor_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(
                        text = stringResource(R.string.editor_cancel),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }
}

private enum class NumericEditField {
    DAILY_LIMIT,
    SESSION_LIMIT,
    COOLDOWN,
    MAX_OPENS,
    MATH_EQUATION_COUNT
}

private data class NumericEditConfig(
    val title: String,
    val description: String,
    val value: Int,
    val range: IntRange,
    val onConfirm: (Int) -> Unit
)

@Composable
private fun NumberEditDialog(
    config: NumericEditConfig,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember(config.value) { mutableStateOf(config.value.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.editor_custom_value_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                text = stringResource(R.string.editor_custom_value_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Text(
                    text = config.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        text = value.filter(Char::isDigit).take(4)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(config.title) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = text.toIntOrNull()
                    if (parsed == null || parsed !in config.range) {
                        error = invalidMessage
                    } else {
                        onConfirm(parsed)
                    }
                }
            ) {
                Text(
                    text = stringResource(R.string.editor_custom_value_apply),
                    color = AccentSage
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.editor_cancel),
                    color = TextMutedGrey
                )
            }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextWarmWhite
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SliderField(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onEdit != null) {
                        Modifier.clickable(onClick = onEdit)
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = TextWarmWhite,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = AccentSage
            )
            if (onEdit != null) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextMutedGrey,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(18.dp)
                )
            }
        }
        Slider(
            value = value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AccentSage,
                activeTrackColor = AccentSage,
                inactiveTrackColor = BorderMuted
            )
        )
    }
}

@Composable
private fun ModeOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentSage.copy(alpha = 0.09f) else CardSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) AccentSage.copy(alpha = 0.65f) else BorderMuted
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextWarmWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ScheduleDraft,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when (schedule.scheduleType) {
                            ScheduleType.LIMITED -> AccentSage
                            ScheduleType.STRICT -> ChartWarm
                            ScheduleType.BREAK -> TextMutedGrey
                        }
                    )
            )
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDays(schedule.daysOfWeek),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextWarmWhite
                )
                Text(
                    text = "${scheduleTypeLabel(schedule.scheduleType)} • ${scheduleTimeLabel(schedule)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }
            TextButton(onClick = onEdit) {
                Text(
                    text = stringResource(R.string.editor_schedule_edit),
                    color = AccentSage
                )
            }
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.editor_schedule_remove),
                    color = TextMutedGrey
                )
            }
        }
    }
}

@Composable
private fun scheduleTypeLabel(type: ScheduleType): String = when (type) {
    ScheduleType.BREAK -> stringResource(R.string.schedule_type_break)
    ScheduleType.LIMITED -> stringResource(R.string.schedule_type_limited)
    ScheduleType.STRICT -> stringResource(R.string.schedule_type_strict)
}

@Composable
private fun scheduleTimeLabel(schedule: ScheduleDraft): String =
    if (schedule.startHour == schedule.endHour && schedule.startMinute == schedule.endMinute) {
        stringResource(R.string.editor_schedule_all_day)
    } else {
        "${formatTime(schedule.startHour, schedule.startMinute)} – " +
            formatTime(schedule.endHour, schedule.endMinute) +
            if (schedule.endHour * 60 + schedule.endMinute < schedule.startHour * 60 + schedule.startMinute)
                " " + stringResource(R.string.rules_next_day) else ""
    }

private fun formatDays(days: List<Int>): String =
    days.sorted().joinToString(", ") { day -> DAY_NAMES[day - 1] }

private fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

private val DAY_NAMES = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

private val COLOR_OPTIONS = listOf(
    "#C6D29E",
    "#D5DFB3",
    "#C9B982",
    "#A4B48A",
    "#8D9185"
)

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = TextMutedGrey,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun OverlappingAppIcons(packages: List<String>) {
    val visible = packages.take(3)
    if (visible.isEmpty()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(IconContainer),
            contentAlignment = Alignment.Center
        ) {
            SendaMark(size = 25.dp)
        }
        return
    }

    val width = (42 + (visible.size - 1) * 22).dp
    Box(
        modifier = Modifier
            .width(width)
            .height(44.dp)
    ) {
        visible.forEachIndexed { index, packageName ->
            val shape = RoundedCornerShape(13.dp)
            Box(
                modifier = Modifier
                    .offset(x = (index * 22).dp)
                    .size(42.dp)
                    .clip(shape)
                    .background(IconContainer)
                    .border(2.dp, CardSurface, shape),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(
                    packageName = packageName,
                    size = 38.dp,
                    shape = shape
                )
            }
        }
    }
}

@Composable
private fun WeeklyScheduleChart(schedules: List<ScheduleDraft>) {
    val week = remember(schedules) { ScheduleTimeline.week(schedules.map { it.toScheduleEntity() }) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0h", "6h", "12h", "18h", "24h").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
        WEEK_DISPLAY_ORDER.forEach { day ->
            val dayPreview = week.first { it.day == day }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DAY_NAMES[day - 1],
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(ChartInactive)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        dayPreview.segments.forEach { segment ->
                            Box(
                                modifier = Modifier
                                    .weight(segment.minutes.toFloat())
                                    .fillMaxHeight()
                                    .background(segmentColor(segment.type))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(
            color = AccentSage,
            text = stringResource(R.string.editor_schedule_legend_limit)
        )
        LegendItem(
            color = ChartWarm,
            text = stringResource(R.string.editor_schedule_legend_strict)
        )
        LegendItem(
            color = ChartInactive,
            text = stringResource(R.string.editor_schedule_legend_break)
        )
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = TextMutedGrey
        )
    }
}

@Composable
private fun ScheduleWindowRow(
    schedule: ScheduleDraft,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(scheduleTypeColor(schedule.scheduleType))
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.name.ifBlank { scheduleTypeLabel(schedule.scheduleType) },
                style = MaterialTheme.typography.titleMedium,
                color = TextWarmWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatDaysLong(schedule.daysOfWeek)} • ${scheduleTimeLabel(schedule)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
            Text(stringResource(if (schedule.customRules != null) R.string.custom_own_badge else R.string.custom_inherit_badge),
                style = MaterialTheme.typography.labelSmall, color = if (schedule.customRules != null) AccentSage else TextMutedGrey)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextMutedGrey,
            modifier = Modifier.size(20.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitsSheet(
    state: GroupEditorUiState,
    viewModel: GroupEditorViewModel,
    onEditNumber: (NumericEditField) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
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
                Text(
                    text = stringResource(R.string.editor_limits_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWarmWhite,
                    modifier = Modifier.weight(1f)
                )
                RoundIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.editor_cancel),
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.editor_limits_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderMuted)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.sharedLimitAcrossApps) {
                            stringResource(
                                R.string.editor_limit_summary_shared,
                                state.dailyLimitMinutes
                            )
                        } else {
                            stringResource(
                                R.string.editor_limit_summary_individual,
                                state.dailyLimitMinutes
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWarmWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.dailyLimitMinutes == 0) {
                            stringResource(R.string.editor_limit_no_daily)
                        } else if (state.maxDailyOpens == 0) {
                            stringResource(
                                R.string.editor_limit_opens_sessions,
                                state.maxDailyOpens,
                                state.sessionLimitMinutes
                            )
                        } else {
                            stringResource(
                                R.string.editor_limit_opens_sessions,
                                state.maxDailyOpens,
                                state.sessionLimitMinutes
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedGrey
                    )
                    if (state.selectedApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OverlappingAppIcons(packages = state.selectedApps.keys.toList())
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            MinimalSliderField(
                label = stringResource(R.string.editor_daily_limit),
                value = state.dailyLimitMinutes,
                valueRange = 0f..240f,
                steps = 47,
                valueLabel = if (state.dailyLimitMinutes == 0) {
                    stringResource(R.string.editor_no_limit)
                } else {
                    stringResource(R.string.editor_minutes_format, state.dailyLimitMinutes)
                },
                onValueChange = viewModel::setDailyLimit,
                onEdit = { onEditNumber(NumericEditField.DAILY_LIMIT) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            MinimalSliderField(
                label = stringResource(R.string.editor_session_length),
                value = state.sessionLimitMinutes,
                valueRange = 1f..60f,
                steps = 59,
                valueLabel = stringResource(
                    R.string.editor_minutes_format,
                    state.sessionLimitMinutes
                ),
                onValueChange = viewModel::setSessionLimit,
                onEdit = { onEditNumber(NumericEditField.SESSION_LIMIT) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            MinimalSliderField(
                label = stringResource(R.string.editor_daily_opens),
                value = state.maxDailyOpens,
                valueRange = 0f..50f,
                steps = 49,
                valueLabel = if (state.maxDailyOpens == 0) {
                    stringResource(R.string.editor_open_count_none)
                } else {
                    pluralStringResource(
                        R.plurals.editor_opens_format,
                        state.maxDailyOpens,
                        state.maxDailyOpens
                    )
                },
                onValueChange = viewModel::setMaxDailyOpens,
                onEdit = { onEditNumber(NumericEditField.MAX_OPENS) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            MinimalSliderField(
                label = stringResource(R.string.editor_cooldown_length),
                value = state.cooldownMinutes,
                valueRange = 0f..120f,
                steps = 23,
                valueLabel = if (state.cooldownMinutes == 0) {
                    stringResource(R.string.editor_no_limit)
                } else {
                    stringResource(R.string.editor_minutes_format, state.cooldownMinutes)
                },
                onValueChange = viewModel::setCooldown,
                onEdit = { onEditNumber(NumericEditField.COOLDOWN) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SendaSectionLabel(text = stringResource(R.string.editor_limit_scope_label))
            Spacer(modifier = Modifier.height(8.dp))

            ModeOption(
                selected = state.sharedLimitAcrossApps,
                title = stringResource(R.string.editor_limit_scope_shared),
                description = stringResource(R.string.editor_limit_scope_shared_description),
                onClick = { viewModel.setSharedLimitAcrossApps(true) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeOption(
                selected = !state.sharedLimitAcrossApps,
                title = stringResource(R.string.editor_limit_scope_individual),
                description = stringResource(R.string.editor_limit_scope_individual_description),
                onClick = { viewModel.setSharedLimitAcrossApps(false) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SendaSectionLabel(text = stringResource(R.string.editor_count_mode_label))
            Spacer(modifier = Modifier.height(8.dp))

            ModeOption(
                selected = state.sessionCountMode == SessionCountMode.ACTIVE_SCREEN_TIME,
                title = stringResource(R.string.editor_mode_active_title),
                description = stringResource(R.string.editor_mode_active_description),
                onClick = {
                    viewModel.setSessionCountMode(SessionCountMode.ACTIVE_SCREEN_TIME)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeOption(
                selected = state.sessionCountMode == SessionCountMode.WALL_CLOCK_WINDOW,
                title = stringResource(R.string.editor_mode_wall_title),
                description = stringResource(R.string.editor_mode_wall_description),
                onClick = {
                    viewModel.setSessionCountMode(SessionCountMode.WALL_CLOCK_WINDOW)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SendaPrimaryButton(
                text = stringResource(R.string.editor_done),
                onClick = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeSheet(
    state: GroupEditorUiState,
    viewModel: GroupEditorViewModel,
    onEditNumber: (NumericEditField) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
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
                Text(
                    text = stringResource(R.string.editor_challenge_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWarmWhite,
                    modifier = Modifier.weight(1f)
                )
                RoundIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.editor_cancel),
                    onClick = onDismiss
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.editor_challenge_sheet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey
            )

            Spacer(modifier = Modifier.height(18.dp))

            ModeOption(
                selected = state.interventionType == InterventionType.BREATHING,
                title = stringResource(R.string.editor_challenge_breathing),
                description = stringResource(R.string.editor_challenge_breathing_description),
                onClick = { viewModel.setInterventionType(InterventionType.BREATHING) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeOption(
                selected = state.interventionType == InterventionType.MATH,
                title = stringResource(R.string.editor_challenge_math),
                description = stringResource(R.string.editor_challenge_math_description),
                onClick = { viewModel.setInterventionType(InterventionType.MATH) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModeOption(
                selected = state.interventionType == InterventionType.REASON,
                title = stringResource(R.string.editor_challenge_reason),
                description = stringResource(R.string.editor_challenge_reason_description),
                onClick = { viewModel.setInterventionType(InterventionType.REASON) }
            )

            if (state.interventionType == InterventionType.BREATHING) {
                Spacer(modifier = Modifier.height(18.dp))
                MinimalSliderField(
                    label = stringResource(R.string.editor_breathing_seconds),
                    value = state.breathingSeconds,
                    valueRange = 5f..60f,
                    steps = 10,
                    valueLabel = "${state.breathingSeconds}s",
                    onValueChange = viewModel::setBreathingSeconds
                )
            }

            if (state.interventionType == InterventionType.MATH) {
                Spacer(modifier = Modifier.height(18.dp))
                SendaSectionLabel(text = stringResource(R.string.editor_math_difficulty_label))
                Spacer(modifier = Modifier.height(8.dp))

                ModeOption(
                    selected = state.mathDifficulty == MathDifficulty.EASY,
                    title = stringResource(R.string.editor_math_easy),
                    description = stringResource(R.string.editor_math_easy_description),
                    onClick = { viewModel.setMathDifficulty(MathDifficulty.EASY) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModeOption(
                    selected = state.mathDifficulty == MathDifficulty.MEDIUM,
                    title = stringResource(R.string.editor_math_medium),
                    description = stringResource(R.string.editor_math_medium_description),
                    onClick = { viewModel.setMathDifficulty(MathDifficulty.MEDIUM) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ModeOption(
                    selected = state.mathDifficulty == MathDifficulty.HARD,
                    title = stringResource(R.string.editor_math_hard),
                    description = stringResource(R.string.editor_math_hard_description),
                    onClick = { viewModel.setMathDifficulty(MathDifficulty.HARD) }
                )

                Spacer(modifier = Modifier.height(18.dp))
                MinimalSliderField(
                    label = stringResource(R.string.editor_math_equations_label),
                    value = state.mathEquationCount,
                    valueRange = 3f..20f,
                    steps = 17,
                    valueLabel = stringResource(
                        R.string.editor_math_equations_value,
                        state.mathEquationCount
                    ),
                    onValueChange = viewModel::setMathEquationCount,
                    onEdit = { onEditNumber(NumericEditField.MATH_EQUATION_COUNT) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.editor_math_minimum_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }

            // Fricção progressiva só faz sentido na respiração: é o único modo
            // em que o tempo de pausa define o desafio. Em matemática e motivo
            // os "segundos extras" não teriam efeito nenhum.
            if (state.interventionType == InterventionType.BREATHING) {
                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_progressive_friction),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWarmWhite
                        )
                        Text(
                            text = stringResource(R.string.editor_progressive_friction_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMutedGrey
                        )
                    }
                    Switch(
                        checked = state.progressiveFrictionEnabled,
                        onCheckedChange = viewModel::setProgressiveFriction
                    )
                }

                if (state.progressiveFrictionEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MinimalSliderField(
                        label = stringResource(R.string.editor_progressive_seconds),
                        value = state.progressiveSecondsPerOpen,
                        valueRange = 1f..30f,
                        steps = 28,
                        valueLabel = "${state.progressiveSecondsPerOpen}s",
                        onValueChange = viewModel::setProgressiveSeconds
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SendaPrimaryButton(
                text = stringResource(R.string.editor_done),
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun GroupIdentityDialog(
    state: GroupEditorUiState,
    viewModel: GroupEditorViewModel,
    onDismiss: () -> Unit
) {
    var name by remember(state.name) { mutableStateOf(state.name) }
    var selectedColor by remember(state.colorHex) { mutableStateOf(state.colorHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                text = stringResource(R.string.editor_name_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.editor_name_label)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.editor_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMutedGrey
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    COLOR_OPTIONS.forEach { colorHex ->
                        val selected = colorHex == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex.toColorInt()))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) AccentSage else BorderMuted,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.setName(name.trim())
                    viewModel.setColor(selectedColor)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    text = stringResource(R.string.editor_done),
                    color = if (name.isNotBlank()) AccentSage else TextMutedGrey
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.editor_cancel),
                    color = TextMutedGrey
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinimalSliderField(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
    onEdit: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = TextWarmWhite,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onEdit != null) {
                            Modifier.clickable(onClick = onEdit)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentSage
                )
                if (onEdit != null) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = AccentSage,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        val fraction = ((value.toFloat() - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

        Slider(
            value = value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                activeTrackColor = AccentSage,
                inactiveTrackColor = BorderMuted
            ),
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(BorderMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(percent = 50))
                            .background(AccentSage)
                    )
                }
            },
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AccentSage)
                )
            }
        )
    }
}

@Composable
private fun interventionLabel(type: InterventionType): String = when (type) {
    InterventionType.BREATHING -> stringResource(R.string.editor_intervention_breathing)
    InterventionType.MATH -> stringResource(R.string.editor_intervention_math)
    InterventionType.REASON -> stringResource(R.string.editor_intervention_reason)
}

@Composable
private fun formatDaysLong(days: List<Int>): String {
    if (days.isEmpty()) return stringResource(R.string.editor_schedule_empty)
    val sorted = days.distinct().sorted()
    if (sorted.size >= 7) return stringResource(R.string.editor_week_all_days)
    if (sorted.size > 1 && sorted.zipWithNext().all { (first, second) -> second == first + 1 }) {
        val first = FULL_DAY_NAMES[sorted.first() - 1]
        val last = FULL_DAY_NAMES[sorted.last() - 1]
            .replaceFirstChar { it.lowercase() }
        return "$first a $last"
    }
    return sorted.joinToString(", ") { FULL_DAY_NAMES[it - 1] }
}

private fun scheduleTypeColor(type: ScheduleType): Color = when (type) {
    ScheduleType.LIMITED -> AccentSage
    ScheduleType.STRICT -> ChartWarm
    ScheduleType.BREAK -> ChartInactive
}

private fun segmentColor(type: ScheduleType?): Color = when (type) {
    ScheduleType.LIMITED -> AccentSage
    ScheduleType.STRICT -> ChartWarm
    ScheduleType.BREAK -> ChartInactive
    null -> ChartInactive
}

private val WEEK_DISPLAY_ORDER = listOf(2, 3, 4, 5, 6, 7, 1)

private val FULL_DAY_NAMES = listOf(
    "Domingo",
    "Segunda",
    "Terça",
    "Quarta",
    "Quinta",
    "Sexta",
    "Sábado"
)

private fun tomorrowStartMillis(): Long =
    LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
