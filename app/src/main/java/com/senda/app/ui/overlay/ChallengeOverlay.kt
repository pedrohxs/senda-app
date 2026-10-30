package com.senda.app.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.senda.app.R
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.domain.challenge.MathEquation
import com.senda.app.domain.challenge.MathEquationGenerator
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.IconContainer
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import kotlinx.coroutines.delay

@Composable
fun ChallengeOverlay(
    challenge: InterventionDecision.Challenge,
    onAccept: (String?) -> Unit,
    onAbort: () -> Unit,
    onAlternative: () -> Unit
) {
    OverlayScaffold {
        when (challenge.interventionType) {
            InterventionType.BREATHING -> BreathingChallenge(
                challenge = challenge,
                onAccept = { onAccept(null) },
                onAbort = onAbort,
                onAlternative = onAlternative
            )
            InterventionType.MATH -> MathChallenge(
                challenge = challenge,
                onAccept = { onAccept(null) },
                onAbort = onAbort,
                onAlternative = onAlternative
            )
            InterventionType.REASON -> ReasonChallenge(
                challenge = challenge,
                onAccept = { reason -> onAccept(reason) },
                onAbort = onAbort,
                onAlternative = onAlternative
            )
        }
    }
}

@Composable
private fun BreathingChallenge(
    challenge: InterventionDecision.Challenge,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onAlternative: () -> Unit
) {
    val requiredCycles = ((challenge.frictionSeconds + 9) / 10).coerceAtLeast(1)
    val scale = remember { Animatable(0.65f) }
    var phase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var cyclesDone by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(requiredCycles, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (true) {
            phase = BreathingPhase.INHALE
            scale.animateTo(1f, animationSpec = tween(4_000, easing = FastOutSlowInEasing))
            phase = BreathingPhase.HOLD
            delay(2_000)
            phase = BreathingPhase.EXHALE
            scale.animateTo(0.65f, animationSpec = tween(4_000, easing = FastOutSlowInEasing))
            cyclesDone++
        }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OverlayHeader(
            label = stringResource(R.string.challenge_label),
            groupName = challenge.groupName
        )

        Spacer(modifier = Modifier.height(14.dp))
        ChallengeTargetBadge(
            packageName = challenge.target.packageName,
            isWebsite = challenge.target.isWebsite
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.size(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            ) {
                drawCircle(color = IconContainer)
                drawCircle(
                    color = AccentSage.copy(alpha = 0.35f),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = phaseLabel(phase),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWarmWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.challenge_cycle_progress,
                        minOf(cyclesDone + 1, requiredCycles),
                        requiredCycles
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.challenge_breathing_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextWarmWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.challenge_question, challenge.target.displayName),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        ChallengeActions(
            challenge = challenge,
            enabled = cyclesDone >= requiredCycles,
            onAccept = onAccept,
            onAbort = onAbort,
            onAlternative = onAlternative
        )
    }
}

/**
 * Tela de matemática no estilo "teclado próprio": conta em destaque, campo de
 * resposta arredondado e teclado numérico fixo. Nada de teclado do sistema —
 * ele empurrava o layout, escondia a conta e deixava a resposta em um campo
 * pequeno no meio de uma tela quase vazia.
 */
@Composable
private fun MathChallenge(
    challenge: InterventionDecision.Challenge,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onAlternative: () -> Unit
) {
    val required = challenge.mathEquationCount.coerceAtLeast(3)
    var solved by remember(challenge.mathEquationCount, challenge.mathDifficulty) {
        mutableIntStateOf(0)
    }
    var equation by remember(challenge.mathEquationCount, challenge.mathDifficulty) {
        mutableStateOf(MathEquationGenerator.generate(challenge.mathDifficulty))
    }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val completed = solved >= required
    val wrongMessage = stringResource(
        R.string.challenge_math_wrong_changed,
        solved,
        required
    )

    fun typeDigit(digit: Int) {
        if (completed) return
        if (answer.length >= MAX_MATH_ANSWER_DIGITS) return
        answer = if (answer == "0") "$digit" else answer + digit
        wrong = false
    }

    fun eraseDigit() {
        if (completed || answer.isEmpty()) return
        answer = answer.dropLast(1)
        wrong = false
    }

    fun submitAnswer() {
        if (completed || answer.isEmpty()) return
        if (answer.toIntOrNull() == equation.answer) {
            solved += 1
            answer = ""
            wrong = false
            if (solved < required) {
                equation = MathEquationGenerator.generate(challenge.mathDifficulty)
            }
        } else {
            wrong = true
            answer = ""
            equation = MathEquationGenerator.generate(challenge.mathDifficulty)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAbort) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.challenge_leave_and_focus),
                    tint = TextWarmWhite
                )
            }
            Text(
                text = stringResource(
                    R.string.challenge_math_progress_short,
                    minOf(solved + 1, required),
                    required
                ),
                style = MaterialTheme.typography.titleMedium,
                color = TextMutedGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Text(
            text = challenge.target.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedGrey,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(0.7f))

        if (completed) {
            Text(
                text = stringResource(R.string.challenge_math_completed),
                style = MaterialTheme.typography.headlineSmall,
                color = AccentSage,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MathExpression(
                equation = equation,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            MathAnswerBox(answer = answer, isWrong = wrong)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (wrong) {
                    wrongMessage
                } else {
                    stringResource(R.string.challenge_math_keypad_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (wrong) MaterialTheme.colorScheme.error else TextMutedGrey,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            )
        }

        Spacer(modifier = Modifier.weight(0.5f))

        if (completed) {
            ChallengeActions(
                challenge = challenge,
                enabled = true,
                onAccept = onAccept,
                onAbort = onAbort,
                onAlternative = onAlternative
            )
        } else {
            MathKeypad(
                modifier = Modifier.weight(2.6f),
                onDigit = ::typeDigit,
                onBackspace = ::eraseDigit,
                onConfirm = ::submitAnswer,
                confirmEnabled = answer.isNotEmpty()
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecondaryOverlayButton(
                text = stringResource(R.string.challenge_leave_and_focus),
                onClick = onAbort
            )
        }
    }
}

@Composable
private fun MathExpression(equation: MathEquation, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        // As contas difíceis passam de 20 caracteres e, em telas estreitas,
        // quebravam deixando o "=" sozinho na segunda linha. O tamanho se
        // ajusta à largura disponível para a conta caber inteira.
        val label = mathExpressionLabel(equation)
        val fontSize = (maxWidth.value / (label.length * MATH_CHAR_WIDTH_EM))
            .coerceIn(MATH_EXPRESSION_MIN_SP, MATH_EXPRESSION_MAX_SP)

        Text(
            text = label,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.15f).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = TextWarmWhite,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MathAnswerBox(answer: String, isWrong: Boolean) {
    val borderColor = when {
        isWrong -> MaterialTheme.colorScheme.error
        answer.isEmpty() -> AccentSage.copy(alpha = 0.45f)
        else -> AccentSage
    }
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(shape)
            .background(CardSurface)
            .border(width = 2.dp, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = answer,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 6.sp
            ),
            color = TextWarmWhite,
            maxLines = 1
        )
    }
}

@Composable
private fun MathKeypad(
    modifier: Modifier = Modifier,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MATH_KEY_GAP)
    ) {
        MATH_KEY_ROWS.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MATH_KEY_GAP)
            ) {
                row.forEach { digit ->
                    MathKey(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onDigit(digit) }
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextWarmWhite
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(MATH_KEY_GAP)
        ) {
            MathKey(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onBackspace
            ) {
                Icon(
                    imageVector = Icons.Rounded.Backspace,
                    contentDescription = stringResource(R.string.challenge_math_backspace),
                    tint = TextWarmWhite,
                    modifier = Modifier.size(26.dp)
                )
            }
            MathKey(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { onDigit(0) }
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextWarmWhite
                )
            }
            MathKey(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = onConfirm,
                enabled = confirmEnabled,
                containerColor = if (confirmEnabled) AccentSage else IconContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.challenge_math_confirm),
                    tint = if (confirmEnabled) DarkCanvas else TextMutedGrey,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun MathKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = IconContainer,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun mathExpressionLabel(equation: MathEquation): String =
    // Espaço inquebrável: o "=" nunca fica sozinho na linha de baixo.
    "${equation.text.replace("*", "×")}\u00A0="

@Composable
private fun ReasonChallenge(
    challenge: InterventionDecision.Challenge,
    onAccept: (String) -> Unit,
    onAbort: () -> Unit,
    onAlternative: () -> Unit
) {
    val reasonResources = remember {
        listOf(
            R.string.challenge_reason_message,
            R.string.challenge_reason_work,
            R.string.challenge_reason_specific
        )
    }
    val reasonLabels = reasonResources.map { stringResource(it) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var customReason by remember { mutableStateOf("") }
    val effectiveReason = customReason.trim().ifEmpty {
        selectedIndex?.let { reasonLabels[it] }.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OverlayHeader(
            label = stringResource(R.string.challenge_label),
            groupName = challenge.groupName
        )

        Spacer(modifier = Modifier.height(14.dp))
        ChallengeTargetBadge(
            packageName = challenge.target.packageName,
            isWebsite = challenge.target.isWebsite
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.challenge_reason_title),
            style = MaterialTheme.typography.headlineMedium,
            color = TextWarmWhite,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.challenge_question, challenge.target.displayName),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMutedGrey,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            reasonLabels.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedIndex == index,
                    onClick = {
                        selectedIndex = index
                        customReason = ""
                    },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customReason,
            onValueChange = { value ->
                customReason = value
                if (value.isNotBlank()) selectedIndex = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.challenge_reason_custom)) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        ChallengeActions(
            challenge = challenge,
            enabled = effectiveReason.isNotBlank(),
            onAccept = { onAccept(effectiveReason) },
            onAbort = onAbort,
            onAlternative = onAlternative
        )
    }
}

@Composable
private fun ChallengeActions(
    challenge: InterventionDecision.Challenge,
    enabled: Boolean,
    onAccept: () -> Unit,
    onAbort: () -> Unit,
    onAlternative: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryOverlayButton(
            text = stringResource(
                R.string.challenge_open_with_limit,
                challenge.sessionLimitMinutes
            ),
            enabled = enabled,
            onClick = onAccept
        )
        Spacer(modifier = Modifier.height(8.dp))
        SecondaryOverlayButton(
            text = stringResource(R.string.challenge_leave_and_focus),
            onClick = onAbort
        )
        challenge.alternativeAppPackage?.let { packageName ->
            TextButton(
                onClick = onAlternative,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(
                    packageName = packageName,
                    size = 24.dp,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.challenge_alternative_app),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextWarmWhite
                )
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: BreathingPhase): String = when (phase) {
    BreathingPhase.INHALE -> stringResource(R.string.challenge_phase_inhale)
    BreathingPhase.HOLD -> stringResource(R.string.challenge_phase_hold)
    BreathingPhase.EXHALE -> stringResource(R.string.challenge_phase_exhale)
}

private enum class BreathingPhase {
    INHALE,
    HOLD,
    EXHALE
}

private val MATH_KEY_ROWS = listOf(
    listOf(7, 8, 9),
    listOf(4, 5, 6),
    listOf(1, 2, 3)
)

private val MATH_KEY_GAP = 10.dp

private const val MAX_MATH_ANSWER_DIGITS = 5

/** Largura média de um caractere da conta, em "em". Serve para o auto-ajuste. */
private const val MATH_CHAR_WIDTH_EM = 0.42f
private const val MATH_EXPRESSION_MIN_SP = 24f
private const val MATH_EXPRESSION_MAX_SP = 42f
