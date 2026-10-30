package com.senda.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Vibrações curtas para ações que mudam algo de verdade.
 *
 * - [confirm] para o que foi concluído (salvar, autorizar, criar);
 * - [reject] para o que apaga ou desfaz proteção;
 * - [tick] para toques de navegação e seleção.
 *
 * O Android respeita a preferência de vibração do sistema: se o usuário
 * desligou o retorno tátil, nada acontece.
 */
class SendaHaptics(private val view: View) {

    fun confirm() {
        perform(
            preferred = HapticFeedbackConstants.CONFIRM,
            fallback = HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun reject() {
        perform(
            preferred = HapticFeedbackConstants.REJECT,
            fallback = HapticFeedbackConstants.LONG_PRESS
        )
    }

    fun tick() {
        perform(
            preferred = HapticFeedbackConstants.CONTEXT_CLICK,
            fallback = HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    private fun perform(preferred: Int, fallback: Int) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) preferred else fallback
        runCatching { view.performHapticFeedback(constant) }
    }
}

@Composable
fun rememberSendaHaptics(): SendaHaptics {
    val view = LocalView.current
    return remember(view) { SendaHaptics(view) }
}
