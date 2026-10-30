package com.senda.app.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import kotlinx.coroutines.delay

@Composable
fun StrictBlockOverlay(
    block: InterventionDecision.Block,
    onDismiss: () -> Unit
) {
    val title = when (block.reason) {
        BlockReason.STRICT_SCHEDULE -> stringResource(R.string.strict_title_schedule)
        BlockReason.COOLDOWN -> stringResource(R.string.strict_title_cooldown)
        BlockReason.DAILY_LIMIT -> stringResource(R.string.strict_title_daily)
        BlockReason.OPEN_LIMIT -> stringResource(R.string.strict_title_opens)
        BlockReason.CLOCK_TAMPER -> stringResource(R.string.strict_title_clock)
        BlockReason.BLOCKED_DOMAIN -> stringResource(R.string.strict_title_site)
        BlockReason.BLOCKED_KEYWORD -> stringResource(R.string.strict_title_keyword)
    }

    val body = when (block.reason) {
        BlockReason.STRICT_SCHEDULE -> stringResource(
            R.string.strict_body_schedule,
            block.target.displayName
        )
        BlockReason.COOLDOWN -> stringResource(
            R.string.strict_body_cooldown,
            block.target.displayName
        )
        BlockReason.DAILY_LIMIT -> stringResource(
            R.string.strict_body_daily,
            block.target.displayName
        )
        BlockReason.OPEN_LIMIT -> stringResource(
            R.string.strict_body_opens,
            block.target.displayName
        )
        BlockReason.CLOCK_TAMPER -> stringResource(
            R.string.strict_body_clock,
            block.target.displayName
        )
        BlockReason.BLOCKED_DOMAIN -> stringResource(
            R.string.strict_body_site,
            block.target.displayName
        )
        BlockReason.BLOCKED_KEYWORD -> stringResource(
            R.string.strict_body_keyword,
            block.target.displayName
        )
    }

    var remainingSeconds by remember(block.cooldownRemainingSeconds) {
        mutableIntStateOf(block.cooldownRemainingSeconds.toInt().coerceAtLeast(0))
    }

    LaunchedEffect(block.cooldownRemainingSeconds) {
        while (remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds--
        }
    }

    OverlayScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OverlayHeader(
                label = stringResource(R.string.strict_label),
                groupName = block.groupName
            )

            Spacer(modifier = Modifier.weight(1f))

            LockBadge()

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextWarmWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = TextMutedGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (remainingSeconds > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = formatRemainingTime(remainingSeconds),
                    style = MaterialTheme.typography.displaySmall,
                    color = AccentSage,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.strict_cooldown_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val isPageBlock = block.reason == BlockReason.BLOCKED_DOMAIN ||
                block.reason == BlockReason.BLOCKED_KEYWORD

            PrimaryOverlayButton(
                text = stringResource(
                    if (isPageBlock) R.string.strict_leave_page else R.string.strict_back
                ),
                enabled = true,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun LockBadge() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(IconContainer),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val strokeWidth = 3.dp.toPx()
            val shackleRadius = size.width * 0.28f

            drawArc(
                color = AccentSage,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(
                    x = size.width / 2f - shackleRadius,
                    y = size.height * 0.10f
                ),
                size = Size(shackleRadius * 2f, shackleRadius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawRoundRect(
                color = AccentSage,
                topLeft = Offset(
                    x = size.width * 0.16f,
                    y = size.height * 0.46f
                ),
                size = Size(
                    width = size.width * 0.68f,
                    height = size.height * 0.46f
                ),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }
}
