package com.senda.app.devtools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.domain.policy.InterventionTarget
import com.senda.app.ui.overlay.ChallengeOverlay
import com.senda.app.ui.overlay.StrictBlockOverlay
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.SendaTheme

/**
 * Abre uma sobreposição de intervenção isolada, sem precisar de sessão ativa.
 * Serve para validar o layout dos desafios em build debug:
 *
 * adb shell am start -n com.senda.app/.devtools.OverlayPreviewActivity \
 *     --es type math --ei equations 3 --es difficulty HARD
 */
class OverlayPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty().lowercase()
        val equations = intent.getIntExtra(EXTRA_EQUATIONS, 3)
        val difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)
            ?.let { value -> runCatching { MathDifficulty.valueOf(value.uppercase()) }.getOrNull() }
            ?: MathDifficulty.MEDIUM

        val target = InterventionTarget(
            packageName = "com.instagram.android",
            identifier = "com.instagram.android",
            displayName = "Instagram",
            isWebsite = false
        )

        setContent {
            SendaTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkCanvas)
                ) {
                    when (type) {
                        "block" -> StrictBlockOverlay(
                            block = InterventionDecision.Block(
                                target = target,
                                groupId = "preview",
                                groupName = "Redes sociais",
                                reason = BlockReason.DAILY_LIMIT
                            ),
                            onDismiss = { finish() }
                        )

                        else -> ChallengeOverlay(
                            challenge = InterventionDecision.Challenge(
                                target = target,
                                groupId = "preview",
                                groupName = "Redes sociais",
                                interventionType = when (type) {
                                    "breathing" -> InterventionType.BREATHING
                                    "reason" -> InterventionType.REASON
                                    else -> InterventionType.MATH
                                },
                                frictionSeconds = 20,
                                sessionLimitMinutes = 5,
                                cooldownMinutes = 10,
                                countMode = SessionCountMode.ACTIVE_SCREEN_TIME,
                                alternativeAppPackage = null,
                                usageKey = "preview",
                                mathDifficulty = difficulty,
                                mathEquationCount = equations
                            ),
                            onAccept = { finish() },
                            onAbort = { finish() },
                            onAlternative = { finish() }
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_EQUATIONS = "equations"
        const val EXTRA_DIFFICULTY = "difficulty"
    }
}
