package com.senda.app.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import java.util.Locale

@Composable
fun OverlayScaffold(content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkCanvas
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            content = content
        )
    }
}

@Composable
fun OverlayHeader(
    label: String,
    groupName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextMutedGrey,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = groupName,
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChallengeTargetBadge(
    packageName: String,
    isWebsite: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderMuted)
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isWebsite) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(IconContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = AccentSage,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                AppIcon(
                    packageName = packageName,
                    size = 52.dp,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    }
}

@Composable
fun PrimaryOverlayButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentSage,
            contentColor = DarkCanvas,
            disabledContainerColor = BorderMuted,
            disabledContentColor = TextMutedGrey
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SecondaryOverlayButton(
    text: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = TextWarmWhite
        )
    }
}

fun formatRemainingTime(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3600
    val minutes = (safeSeconds % 3600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
