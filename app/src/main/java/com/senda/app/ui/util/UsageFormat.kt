package com.senda.app.ui.util

fun formatUsageSeconds(totalSeconds: Int): String {
    if (totalSeconds <= 0) return "0 min"
    if (totalSeconds < 60) return "<1 min"
    val minutes = totalSeconds / 60
    return if (minutes < 60) {
        "$minutes min"
    } else {
        "${minutes / 60}h ${minutes % 60}min"
    }
}

fun formatUsageMinutesShort(totalSeconds: Int): String {
    if (totalSeconds in 1 until 60) return "<1"
    return (totalSeconds / 60).toString()
}
