package com.senda.app.ui.focus

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.GroupStatus
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.components.SendaDashedButton
import com.senda.app.ui.components.SendaIconBadge
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaScreenState
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaStateHost
import com.senda.app.ui.components.SendaStatusPill
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.IconTint
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import com.senda.app.ui.util.formatUsageSeconds
import com.senda.app.ui.util.rememberSendaHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val HOUR_MINUTE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_NAMES = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

@Composable
fun FocusScreen(
    state: FocusUiState,
    onCreateGroup: () -> Unit,
    onEditGroup: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onRepairProtection: () -> Unit = onOpenSettings,
    onEndSession: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var confirmEndSession by remember { mutableStateOf(false) }
    val haptics = rememberSendaHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.focus_greeting),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.focus_home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWarmWhite
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSurface),
                contentAlignment = Alignment.Center
            ) {
                SendaMark(size = 30.dp)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        ScreenTimeHero(
            usedSeconds = state.totalUsedSeconds,
            dailyLimitMinutes = state.totalDailyLimitMinutes,
            showDailyProgress = state.showDailyProgress
        )

        state.activeSession?.let { session ->
            Spacer(modifier = Modifier.height(14.dp))
            ActiveSessionCard(
                session = session,
                onEnd = {
                    haptics.tick()
                    confirmEndSession = true
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        NextBlockCard(nextBlock = state.nextBlock, nowMillis = state.nowMillis)

        Spacer(modifier = Modifier.height(26.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SendaSectionLabel(
                text = stringResource(R.string.focus_section_groups),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.focus_summary_impulses_short, state.totalImpulses),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        SendaStateHost(
            state = groupsScreenState(state = state, onCreateGroup = onCreateGroup, onRetry = onRetry),
            modifier = Modifier.fillMaxWidth(),
            minHeight = 180.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderMuted)
            ) {
                Column {
                    state.groups.forEachIndexed { index, group ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp),
                                color = BorderMuted
                            )
                        }
                        GroupRow(
                            group = group,
                            onClick = { onEditGroup(group.id) }
                        )
                    }
                }
            }
        }

        if (state.groups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            SendaDashedButton(
                text = stringResource(R.string.focus_add_group),
                onClick = onCreateGroup
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Spacer(modifier = Modifier.height(104.dp))
    }

    if (confirmEndSession) {
        AlertDialog(
            onDismissRequest = { confirmEndSession = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = { Text(stringResource(R.string.home_session_end_title)) },
            text = { Text(stringResource(R.string.home_session_end_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.reject()
                        confirmEndSession = false
                        onEndSession()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.home_session_end_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndSession = false }) {
                    Text(
                        text = stringResource(R.string.settings_cancel),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }
}

private fun groupsScreenState(
    state: FocusUiState,
    onCreateGroup: () -> Unit,
    onRetry: () -> Unit
): SendaScreenState = when {
    state.loadFailed -> SendaScreenState.error(
        messageRes = R.string.home_error,
        onRetry = onRetry
    )
    state.isLoading && state.groups.isEmpty() -> SendaScreenState.loading()
    state.groups.isEmpty() -> SendaScreenState.empty(
        titleRes = R.string.home_empty_title,
        messageRes = R.string.home_empty_message,
        actionRes = R.string.home_empty_action,
        onAction = onCreateGroup
    )
    else -> SendaScreenState.content()
}

@Composable
private fun ActiveSessionCard(
    session: ActiveSessionUi,
    onEnd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = AccentSage.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AccentSage.copy(alpha = 0.40f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SendaIconBadge(
                    icon = Icons.Outlined.Timer,
                    tint = AccentSage,
                    size = 38.dp
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_session_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentSage
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatRemaining(session.remainingSeconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextWarmWhite
                    )
                }
                TextButton(onClick = onEnd) {
                    Text(
                        text = stringResource(R.string.home_session_end),
                        color = TextMutedGrey,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.home_session_meta,
                    session.targetLabel,
                    session.groupName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NextBlockCard(nextBlock: NextBlockUi?, nowMillis: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SendaIconBadge(
                    icon = Icons.Outlined.Schedule,
                    tint = if (nextBlock == null) TextMutedGrey else IconTint,
                    size = 38.dp
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_next_block_title),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedGrey
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    when {
                        nextBlock == null -> Text(
                            text = stringResource(R.string.home_next_block_none),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMutedGrey
                        )

                        nextBlock.isNow -> Text(
                            text = stringResource(R.string.home_next_block_now),
                            style = MaterialTheme.typography.titleMedium,
                            color = ChartWarm
                        )

                        else -> Text(
                            text = stringResource(
                                R.string.home_next_block_in,
                                formatDuration(nextBlock.startMillis - nowMillis)
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWarmWhite
                        )
                    }
                }
            }

            if (nextBlock != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.home_next_block_meta,
                        nextBlock.groupName,
                        scheduleTypeLabel(nextBlock.type)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (nextBlock.isNow) {
                        stringResource(
                            R.string.home_next_block_meta,
                            formatHourMinute(nextBlock.endMillis),
                            stringResource(R.string.rules_next_day)
                        )
                    } else {
                        formatNextBlockDate(nextBlock.startMillis, nowMillis)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}

@Composable
private fun ScreenTimeHero(
    usedSeconds: Int,
    dailyLimitMinutes: Int,
    showDailyProgress: Boolean
) {
    val limitSeconds = dailyLimitMinutes * 60
    val progress = if (limitSeconds > 0) {
        (usedSeconds.toFloat() / limitSeconds).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.focus_screen_time_today),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatUsageSeconds(usedSeconds),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextWarmWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (showDailyProgress) {
                            stringResource(R.string.focus_of_daily_limit, dailyLimitMinutes)
                        } else {
                            stringResource(R.string.focus_limits_separate)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedGrey
                    )
                }
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(AccentSage.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    SendaMark(size = 36.dp)
                }
            }

            if (showDailyProgress) {
                Spacer(modifier = Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape),
                    color = AccentSage,
                    trackColor = IconContainer,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.focus_progress_percent,
                        (progress * 100).roundToInt()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupCardUi,
    onClick: () -> Unit
) {
    val nowMillis = System.currentTimeMillis()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(IconContainer),
                contentAlignment = Alignment.Center
            ) {
                val packageName = group.appPackages.firstOrNull()
                if (packageName != null) {
                    AppIcon(
                        packageName = packageName,
                        size = 28.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    SendaMark(size = 24.dp)
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWarmWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = groupSubtitle(group, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.size(8.dp))

            SendaStatusPill(
                text = stringResource(statusLabel(group, nowMillis)),
                color = statusColor(group, nowMillis),
                container = IconContainer
            )

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextMutedGrey,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
            )
        }

        if (group.dailyLimitMinutes > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            val usedProgress = (group.usedSeconds.toFloat() /
                (group.dailyLimitMinutes * 60f)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { usedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = if (usedProgress >= 1f) ChartWarm else AccentSage,
                trackColor = IconContainer,
                drawStopIndicator = {}
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.home_group_usage,
                    group.usedSeconds / 60,
                    group.dailyLimitMinutes
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextMutedGrey
            )
        }
    }
}

@Composable
private fun groupSubtitle(group: GroupCardUi, nowMillis: Long): String {
    val pausedUntil = group.pausedUntilMillis
    if (pausedUntil != null && pausedUntil > nowMillis) {
        return stringResource(R.string.focus_group_subtitle_paused, formatHourMinute(pausedUntil))
    }

    if (group.status == GroupStatus.FREE) return stringResource(
        if (group.hasSchedules) R.string.focus_free_now else R.string.focus_no_schedule)

    val limit = group.dailyLimitMinutes
    if (group.activeRuleName != null) return group.activeRuleName + " • " +
        if (limit == 0) stringResource(R.string.editor_no_limit)
        else if (group.sharedLimitAcrossApps) stringResource(R.string.focus_group_subtitle_limit, limit)
        else stringResource(R.string.focus_group_limit_individual, limit)
    return when {
        limit > 0 && !group.sharedLimitAcrossApps -> stringResource(R.string.focus_group_limit_individual, limit)
        limit > 0 && group.hasSchedules -> stringResource(
            R.string.focus_group_subtitle_limit_scheduled,
            limit
        )
        limit > 0 -> stringResource(R.string.focus_group_subtitle_limit, limit)
        group.hasSchedules -> stringResource(R.string.focus_scheduled_rules)
        else -> stringResource(R.string.focus_always_active)
    }
}

private fun isPaused(group: GroupCardUi, nowMillis: Long): Boolean =
    group.pausedUntilMillis?.let { it > nowMillis } == true

private fun statusLabel(group: GroupCardUi, nowMillis: Long): Int {
    if (isPaused(group, nowMillis)) return R.string.focus_status_paused

    return when {
        group.status == GroupStatus.FREE -> R.string.focus_status_free
        group.status == GroupStatus.LIMITED -> R.string.focus_status_limited
        group.status == GroupStatus.PARTIAL -> R.string.focus_status_partial
        group.status == GroupStatus.STRICT -> R.string.focus_status_strict
        group.blockReason == BlockReason.COOLDOWN -> R.string.focus_status_cooldown
        group.blockReason == BlockReason.DAILY_LIMIT -> R.string.focus_status_daily_limit
        group.blockReason == BlockReason.OPEN_LIMIT -> R.string.focus_status_open_limit
        group.blockReason == BlockReason.CLOCK_TAMPER -> R.string.focus_status_clock
        else -> R.string.focus_status_blocked
    }
}

private fun statusColor(group: GroupCardUi, nowMillis: Long) = when {
    isPaused(group, nowMillis) -> TextMutedGrey
    group.status == GroupStatus.FREE -> AccentSage
    group.status == GroupStatus.LIMITED -> IconTint
    group.status == GroupStatus.STRICT -> ChartWarm
    group.status == GroupStatus.PARTIAL -> ChartWarm
    else -> TextMutedGrey
}

@Composable
private fun scheduleTypeLabel(type: ScheduleType): String = stringResource(
    when (type) {
        ScheduleType.LIMITED -> R.string.schedule_type_limited
        ScheduleType.STRICT -> R.string.schedule_type_strict
        ScheduleType.BREAK -> R.string.schedule_type_break
    }
)

private fun formatHourMinute(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(HOUR_MINUTE_FORMATTER)

@Composable
private fun formatNextBlockDate(startMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val time = start.format(HOUR_MINUTE_FORMATTER)
    return when {
        start.toLocalDate() == today -> stringResource(R.string.home_next_block_today, time)
        start.toLocalDate() == today.plusDays(1) -> stringResource(R.string.home_next_block_tomorrow, time)
        else -> stringResource(
            R.string.home_next_block_day,
            DAY_NAMES[start.dayOfWeek.value - 1],
            time
        )
    }
}

/** Duração curta e legível: "2 h 05 min", "45 min", "menos de 1 min". */
@Composable
private fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    return when {
        totalMinutes < 1L -> stringResource(R.string.home_duration_less_minute)
        totalMinutes < 60L -> stringResource(R.string.home_duration_minutes, totalMinutes.toInt())
        else -> {
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            if (minutes == 0L) {
                stringResource(R.string.home_duration_hours, hours.toInt())
            } else {
                stringResource(R.string.home_duration_hours_minutes, hours.toInt(), minutes.toInt())
            }
        }
    }
}

@Composable
private fun formatRemaining(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3600) {
        stringResource(R.string.home_duration_hours_minutes, safe / 3600, (safe % 3600) / 60)
    } else {
        stringResource(R.string.home_remaining_clock, safe / 60, safe % 60)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111312)
@Composable
private fun FocusScreenPreview() {
    SendaTheme {
        FocusScreen(
            state = FocusUiState(
                isLoading = false,
                groups = listOf(
                    GroupCardUi(
                        id = "1",
                        name = "Redes sociais",
                        colorHex = "#A1B976",
                        status = GroupStatus.LIMITED,
                        blockReason = null,
                        usedSeconds = 14 * 60,
                        dailyLimitMinutes = 30,
                        appPackages = listOf("com.instagram.android"),
                        hasSchedules = true
                    ),
                    GroupCardUi(
                        id = "2",
                        name = "Bloqueio noturno",
                        colorHex = "#E0B878",
                        status = GroupStatus.STRICT,
                        blockReason = null,
                        usedSeconds = 0,
                        dailyLimitMinutes = 0,
                        appPackages = emptyList(),
                        hasSchedules = true
                    )
                ),
                totalUsedSeconds = 14 * 60,
                totalImpulses = 3,
                totalDailyLimitMinutes = 30,
                activeSession = ActiveSessionUi(
                    groupName = "Redes sociais",
                    targetLabel = "Instagram",
                    remainingSeconds = 272
                ),
                nextBlock = NextBlockUi(
                    groupName = "Bloqueio noturno",
                    windowName = null,
                    type = ScheduleType.STRICT,
                    startMillis = System.currentTimeMillis() + 3 * 3_600_000L,
                    endMillis = System.currentTimeMillis() + 4 * 3_600_000L,
                    isNow = false
                )
            ),
            onCreateGroup = {},
            onEditGroup = {},
            onOpenSettings = {}
        )
    }
}
