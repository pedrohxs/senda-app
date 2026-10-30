package com.senda.app.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite

/**
 * Sobreposição de teste: ocupa a tela inteira, como o bloqueio de verdade, e
 * some sozinha. Se esta janela aparecer, a permissão e o desenho sobre outros
 * apps estão funcionando.
 */
@Composable
fun TestOverlayContent(
    durationMillis: Long,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(durationMillis) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMillis.toInt().coerceAtLeast(1),
                easing = LinearEasing
            )
        )
        onFinished()
    }
    val remainingSeconds by remember {
        androidx.compose.runtime.derivedStateOf {
            val remaining = ((1f - progress.value) * durationMillis / 1000f)
            remaining.toInt().coerceAtLeast(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, AccentSage.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(64.dp)
                        .clip(CircleShape)
                        .background(IconContainer),
                    contentAlignment = Alignment.Center
                ) {
                    SendaMark(size = 40.dp)
                }
                Text(
                    text = stringResource(R.string.protection_test_overlay_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWarmWhite,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.protection_test_overlay_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedGrey,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = AccentSage,
                    trackColor = BorderMuted,
                    drawStopIndicator = {}
                )
                Text(
                    text = stringResource(
                        R.string.protection_test_overlay_countdown,
                        remainingSeconds
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}
