package com.senda.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite

/**
 * Fases possíveis de uma tela que depende de dados.
 *
 * Todas as telas principais usam as mesmas quatro fases para que carregar,
 * vazio e erro tenham sempre o mesmo desenho e a mesma promessa: quando algo
 * falha existe um botão para tentar de novo, e o texto explica o que houve.
 */
enum class SendaScreenPhase { LOADING, CONTENT, EMPTY, ERROR }

data class SendaScreenState(
    val phase: SendaScreenPhase = SendaScreenPhase.CONTENT,
    @StringRes val emptyTitleRes: Int = R.string.state_empty_title,
    @StringRes val emptyMessageRes: Int = R.string.state_empty_message,
    @StringRes val emptyActionRes: Int? = null,
    val onEmptyAction: (() -> Unit)? = null,
    val emptyIcon: ImageVector = Icons.Outlined.Shield,
    @StringRes val errorRes: Int = R.string.state_error_message,
    val errorDetail: String? = null,
    val onRetry: (() -> Unit)? = null
) {
    val canRetry: Boolean get() = phase == SendaScreenPhase.ERROR && onRetry != null

    companion object {
        fun loading() = SendaScreenState(phase = SendaScreenPhase.LOADING)
        fun content() = SendaScreenState(phase = SendaScreenPhase.CONTENT)

        fun empty(
            @StringRes titleRes: Int = R.string.state_empty_title,
            @StringRes messageRes: Int = R.string.state_empty_message,
            @StringRes actionRes: Int? = null,
            icon: ImageVector = Icons.Outlined.Shield,
            onAction: (() -> Unit)? = null
        ) = SendaScreenState(
            phase = SendaScreenPhase.EMPTY,
            emptyTitleRes = titleRes,
            emptyMessageRes = messageRes,
            emptyActionRes = actionRes,
            onEmptyAction = onAction,
            emptyIcon = icon
        )

        fun error(
            @StringRes messageRes: Int = R.string.state_error_message,
            detail: String? = null,
            onRetry: (() -> Unit)? = null
        ) = SendaScreenState(
            phase = SendaScreenPhase.ERROR,
            errorRes = messageRes,
            errorDetail = detail,
            onRetry = onRetry
        )
    }
}

/**
 * Desenha carregando, vazio ou erro no lugar do conteúdo, mantendo a mesma
 * transição em todas as telas. Em [SendaScreenPhase.CONTENT] apenas executa
 * [content].
 */
@Composable
fun SendaStateHost(
    state: SendaScreenState,
    modifier: Modifier = Modifier,
    minHeight: Dp? = null,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it.phase },
        transitionSpec = {
            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
        },
        label = "screenState",
        modifier = modifier
    ) { current ->
        when (current.phase) {
            SendaScreenPhase.LOADING -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (minHeight != null) Modifier.height(minHeight) else Modifier.fillMaxSize()),
                contentAlignment = Alignment.Center
            ) {
                SendaLoadingState()
            }

            SendaScreenPhase.EMPTY -> SendaEmptyCard(
                title = stringResource(current.emptyTitleRes),
                message = stringResource(current.emptyMessageRes),
                icon = current.emptyIcon,
                actionLabel = current.emptyActionRes?.let { stringResource(it) },
                onAction = current.onEmptyAction
            )

            SendaScreenPhase.ERROR -> SendaErrorCard(
                message = stringResource(current.errorRes),
                detail = current.errorDetail,
                onRetry = current.onRetry
            )

            SendaScreenPhase.CONTENT -> content()
        }
    }
}

@Composable
fun SendaLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = AccentSage)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

@Composable
fun SendaEmptyCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Shield,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SendaStateIllustration(icon = icon, tint = AccentSage)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextWarmWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, color = AccentSage)
                }
            }
        }
    }
}

@Composable
fun SendaErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, ChartWarm.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SendaStateIllustration(
                icon = Icons.Outlined.CloudOff,
                tint = ChartWarm
            )
            Text(
                text = stringResource(R.string.state_error_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextWarmWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMutedGrey,
                textAlign = TextAlign.Center
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey,
                    textAlign = TextAlign.Center
                )
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.state_error_retry), color = AccentSage)
                }
            }
        }
    }
}

/** Ícone sobre um anel tracejado: ilustração leve, sem peso de imagem. */
@Composable
fun SendaStateIllustration(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AccentSage,
    size: Dp = 84.dp
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            val radius = this.size.minDimension / 2f
            drawCircle(
                color = BorderMuted,
                radius = radius - stroke,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 5.dp.toPx())
                    )
                )
            )
            drawCircle(color = tint.copy(alpha = 0.10f), radius = radius * 0.58f)
            drawCircle(
                color = tint.copy(alpha = 0.28f),
                radius = radius * 0.58f,
                style = Stroke(width = stroke)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.30f)
        )
    }
}
