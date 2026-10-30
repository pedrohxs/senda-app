package com.senda.app.ui.usage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.repository.ChallengeReason
import com.senda.app.data.repository.DailyUsageBar
import com.senda.app.data.repository.DayCoverage
import com.senda.app.data.repository.GroupUsageSummary
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.components.SendaDashedButton
import com.senda.app.ui.components.SendaIconBadge
import com.senda.app.ui.components.SendaScreenState
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaStateHost
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartInactive
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import com.senda.app.ui.util.formatUsageSeconds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UsageScreen(
    state: UsageUiState,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onCurrentWeek: () -> Unit = {},
    onRetry: () -> Unit = {},
    onShareCsv: () -> Unit = {},
    onConfigureGroup: () -> Unit = {}
) {
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showReasons by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.week) {
        if (state.week.none { it.date == selectedDate }) selectedDate = null
    }

    val chartDays = state.chartDays()
    val todayDate = chartDays.lastOrNull()?.date
    val detail = state.dayDetails.firstOrNull { it.date == selectedDate }
    val groups = when {
        selectedDate != null -> detail?.groups.orEmpty().map {
            GroupUsageSummary(groupId = it.name, name = it.name, limitMinutes = 0, seconds = it.seconds)
        }
        state.isCurrentWeek -> state.todayGroups.map {
            GroupUsageSummary(groupId = it.name, name = it.name, limitMinutes = 0, seconds = it.seconds)
        }
        else -> state.groups
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp)
    ) {
        Text(
            text = stringResource(R.string.usage_title),
            style = MaterialTheme.typography.headlineLarge,
            color = TextWarmWhite
        )

        Spacer(modifier = Modifier.height(12.dp))

        WeekNavigator(
            state = state,
            label = if (state.isCurrentWeek) {
                stringResource(R.string.reports_week_current)
            } else {
                stringResource(
                    R.string.reports_week_range,
                    chartDays.firstOrNull()?.date?.let(::formatDayMonth) ?: "—",
                    chartDays.lastOrNull()?.date?.let(::formatDayMonth) ?: "—"
                )
            },
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onCurrentWeek = onCurrentWeek
        )

        Spacer(modifier = Modifier.height(16.dp))

        SendaStateHost(
            state = screenState(
                state = state,
                onRetry = onRetry,
                onConfigure = onConfigureGroup
            ),
            modifier = Modifier.fillMaxWidth(),
            minHeight = 260.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                DaySummaryCard(
                    state = state,
                    selectedDate = selectedDate,
                    todayDate = todayDate,
                    detail = detail,
                    groups = groups,
                    onClearSelection = { selectedDate = null }
                )

                WeeklyChartCard(
                    state = state,
                    chartDays = chartDays,
                    selectedDate = selectedDate,
                    todayDate = todayDate,
                    onDayClick = { date ->
                        selectedDate = if (date == todayDate || date == selectedDate) null else date
                    }
                )

                NumbersCard(state = state)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InsightCard(
                        icon = Icons.Rounded.Whatshot,
                        value = state.streakDays.toString(),
                        label = stringResource(R.string.reports_streak_card),
                        modifier = Modifier.weight(1f)
                    )
                    InsightCard(
                        icon = Icons.Rounded.Shield,
                        value = state.impulses.toString(),
                        label = stringResource(R.string.reports_impulses_card),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.reasons.isNotEmpty()) {
                    TextButton(onClick = { showReasons = !showReasons }) {
                        Text(
                            text = stringResource(
                                if (showReasons) R.string.reports_hide_reasons
                                else R.string.reports_show_reasons
                            ),
                            color = AccentSage
                        )
                    }
                    if (showReasons) {
                        Surface(color = CardSurface, shape = RoundedCornerShape(20.dp)) {
                            Column { state.reasons.forEach { ReasonRow(it) } }
                        }
                    }
                }

                TextButton(
                    onClick = onShareCsv,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        tint = AccentSage,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(R.string.reports_export_csv),
                        color = AccentSage
                    )
                }            }
        }

        Spacer(modifier = Modifier.height(104.dp))
    }
}

private fun screenState(
    state: UsageUiState,
    onRetry: () -> Unit,
    onConfigure: () -> Unit
): SendaScreenState = when {
    state.loadFailed -> SendaScreenState.error(
        messageRes = R.string.reports_error,
        onRetry = onRetry
    )
    state.isLoading -> SendaScreenState.loading()
    state.isEmpty -> SendaScreenState.empty(
        titleRes = R.string.reports_empty_title,
        messageRes = R.string.reports_empty_message,
        actionRes = R.string.reports_empty_action,
        onAction = onConfigure
    )
    else -> SendaScreenState.content()
}

@Composable
private fun WeekNavigator(
    state: UsageUiState,
    label: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onCurrentWeek: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousWeek, enabled = state.canGoBack) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.reports_week_previous),
                    tint = if (state.canGoBack) TextWarmWhite else TextMutedGrey
                )
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isCurrentWeek) AccentSage else TextWarmWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!state.isCurrentWeek) {
                TextButton(onClick = onCurrentWeek) {
                    Text(
                        text = stringResource(R.string.reports_today_button),
                        color = AccentSage,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            IconButton(onClick = onNextWeek, enabled = state.canGoForward) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.reports_week_next),
                    tint = if (state.canGoForward) TextWarmWhite else TextMutedGrey
                )
            }
        }
    }
}

@Composable
private fun DaySummaryCard(
    state: UsageUiState,
    selectedDate: String?,
    todayDate: String?,
    detail: com.senda.app.data.repository.DailyUsageDetail?,
    groups: List<GroupUsageSummary>,
    onClearSelection: () -> Unit
) {
    val periodSeconds = state.week.sumOf { it.seconds }
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        selectedDate != null -> formatDayTitle(selectedDate)
                        state.isCurrentWeek -> stringResource(R.string.reports_today_title)
                        else -> stringResource(R.string.reports_week_short, state.week.firstOrNull()?.let {
                            formatDayMonth(it.date)
                        } ?: "—")
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWarmWhite
                )
                if (selectedDate != null) {
                    TextButton(onClick = onClearSelection) {
                        Text(
                            text = stringResource(R.string.reports_today_button),
                            color = AccentSage
                        )
                    }
                }
            }
            Text(
                text = when {
                    selectedDate != null -> detail?.let { formatUsageSeconds(it.totalSeconds) } ?: "—"
                    state.isCurrentWeek -> formatUsageSeconds(state.todaySeconds)
                    else -> formatUsageSeconds(periodSeconds)
                },
                style = MaterialTheme.typography.displayLarge,
                color = AccentSage
            )
            Text(
                text = when {
                    selectedDate != null -> coverageLabel(detail?.coverage ?: DayCoverage.UNKNOWN)
                    state.isCurrentWeek -> stringResource(R.string.reports_monitored_today)
                    else -> stringResource(R.string.reports_coverage_summary, state.completeDays)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
            if (groups.isEmpty()) {
                Text(
                    text = if (selectedDate == null) {
                        stringResource(R.string.reports_no_usage_today)
                    } else {
                        stringResource(R.string.reports_no_usage_day)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedGrey
                )
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderMuted)
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.name,
                            modifier = Modifier.weight(1f),
                            color = TextWarmWhite,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatUsageSeconds(group.seconds),
                            color = TextWarmWhite,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyChartCard(
    state: UsageUiState,
    chartDays: List<DailyUsageBar>,
    selectedDate: String?,
    todayDate: String?,
    onDayClick: (String) -> Unit
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (state.isCurrentWeek) {
                    stringResource(R.string.reports_chart_title)
                } else {
                    stringResource(
                        R.string.reports_chart_week,
                        state.week.firstOrNull()?.let { formatDayMonth(it.date) } ?: "—"
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                color = TextWarmWhite
            )
            WeeklyPillChart(chartDays, selectedDate ?: todayDate) { date ->
                onDayClick(date)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(
                        R.string.reports_previous_average,
                        if (state.completeDays == 0) "—" else formatUsageSeconds(state.weekAverageSeconds)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
                state.trendPercent?.let {
                    Text(
                        text = trendText(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (it <= 0) AccentSage else ChartWarm
                    )
                }
            }
            if (state.completeDays < 7) {
                Text(
                    text = stringResource(R.string.reports_partial_history),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}

@Composable
private fun NumbersCard(state: UsageUiState) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.reports_coverage_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextWarmWhite
            )

            MetricRow(
                label = stringResource(R.string.reports_metric_average_session),
                value = if (state.completeDays == 0 || state.totalOpens == 0) {
                    "—"
                } else {
                    formatUsageSeconds(state.averageSessionSeconds)
                }
            )
            MetricRow(
                label = stringResource(R.string.reports_metric_opens_day),
                value = if (state.completeDays == 0) {
                    "—"
                } else {
                    state.opensPerDay.toString()
                },
                detail = stringResource(R.string.reports_metric_opens_total, state.totalOpens)
            )

            HorizontalDivider(color = BorderMuted)

            Text(
                text = if (state.completeDays == 0) {
                    stringResource(R.string.reports_coverage_none)
                } else {
                    stringResource(R.string.reports_coverage_summary, state.completeDays)
                },
                style = MaterialTheme.typography.bodySmall,
                color = AccentSage
            )
            Text(
                text = stringResource(R.string.reports_coverage_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, detail: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWarmWhite
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = AccentSage
        )
    }
}

@Composable
private fun coverageLabel(coverage: DayCoverage): String = stringResource(
    when (coverage) {
        DayCoverage.COMPLETE -> R.string.reports_coverage_complete
        DayCoverage.PARTIAL -> R.string.reports_coverage_partial
        DayCoverage.UNKNOWN -> R.string.reports_coverage_unknown
    }
)

@Composable
private fun trendText(trendPercent: Int): String = when {
    trendPercent > 0 -> stringResource(R.string.reports_trend_up, trendPercent)
    trendPercent < 0 -> stringResource(R.string.reports_trend_down, -trendPercent)
    else -> stringResource(R.string.reports_trend_same)
}

@Composable
private fun WeeklyPillChart(
    week: List<DailyUsageBar>,
    selectedDate: String?,
    onDayClick: (String) -> Unit
) {
    val maxSeconds = week.maxOfOrNull { it.seconds }?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        week.forEach { day ->
            val isSelected = day.date == selectedDate
            val fraction = day.seconds.toFloat() / maxSeconds
            val color = when {
                day.seconds == 0 -> ChartInactive
                isSelected -> ChartWarm
                day.isToday -> ChartWarm
                day.coverage != DayCoverage.COMPLETE -> ChartWarm
                else -> AccentSage
            }
            val description = "${formatDayTitle(day.date)}: ${formatUsageSeconds(day.seconds)}. " +
                coverageLabel(day.coverage)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) CardSurface else Color.Transparent)
                    .semantics { contentDescription = description; selected = isSelected }
                    .clickable { onDayClick(day.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(ChartInactive),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(percent = 50))
                            .background(color)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected || day.isToday) TextWarmWhite else TextMutedGrey
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(AccentSage)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonRow(reason: ChallengeReason) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = reason.packageName, size = 34.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reason.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = TextWarmWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.reports_reason_meta,
                    reason.displayName,
                    formatReasonDate(reason.createdAt)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

private val reasonDateFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val dayTitleFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale.forLanguageTag("pt-BR"))
private val dayMonthFormatter = DateTimeFormatter.ofPattern("dd/MM")

private fun formatReasonDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(reasonDateFormatter)

private fun formatDayTitle(date: String): String =
    runCatching { LocalDate.parse(date).format(dayTitleFormatter) }
        .getOrDefault(date)

private fun formatDayMonth(date: String): String =
    runCatching { LocalDate.parse(date).format(dayMonthFormatter) }
        .getOrDefault(date)

@Composable
private fun InsightCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SendaIconBadge(icon = icon, tint = AccentSage, size = 38.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = TextWarmWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111312)
@Composable
private fun UsageScreenPreview() {
    SendaTheme {
        UsageScreen(
            state = UsageUiState(
                isLoading = false,
                todaySeconds = 51 * 60,
                week = listOf(
                    DailyUsageBar("1", "s", 20 * 60, false),
                    DailyUsageBar("2", "t", 35 * 60, false),
                    DailyUsageBar("3", "q", 15 * 60, false),
                    DailyUsageBar("4", "q", 50 * 60, false),
                    DailyUsageBar("5", "s", 10 * 60, false),
                    DailyUsageBar("6", "s", 0, false),
                    DailyUsageBar("7", "d", 51 * 60, true)
                ),
                streakDays = 4,
                impulses = 7,
                weekAverageSeconds = 42 * 60,
                trendPercent = -18,
                groups = listOf(
                    GroupUsageSummary("1", "Redes sociais", 30, 72 * 60),
                    GroupUsageSummary("2", "Bloqueio noturno", 0, 0)
                ),
                totalOpens = 42,
                averageSessionSeconds = 8 * 60,
                opensPerDay = 6,
                completeDays = 5,
                reasons = listOf(
                    ChallengeReason(
                        packageName = "com.instagram.android",
                        displayName = "Instagram",
                        reason = "Responder uma mensagem rápida",
                        createdAt = System.currentTimeMillis()
                    )
                )
            )
        )
    }
}
