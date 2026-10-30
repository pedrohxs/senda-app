package com.senda.app.service

import java.util.Locale

/**
 * Tempo restante no formato curto da notificação: `MM:SS` até uma hora e
 * `H:MM:SS` depois disso. Valores negativos viram zero.
 */
internal fun formatRemaining(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
