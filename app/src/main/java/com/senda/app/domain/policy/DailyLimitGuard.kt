package com.senda.app.domain.policy

/**
 * Soma apenas a parcela da sessão que ainda não está no total persistido.
 * Os dois valores gravados devem vir da mesma leitura transacional.
 */
object DailyLimitGuard {

    fun isExceeded(
        policy: GroupPolicy,
        sessionUsedSeconds: Int,
        recordedSessionSeconds: Int = 0,
        recordedTotalSeconds: Int = policy.usage?.totalTimeUsedSeconds ?: 0
    ): Boolean {
        val limitSeconds = policy.group.dailyLimitMinutes * 60L
        if (limitSeconds <= 0L) return false

        val pendingSeconds = (sessionUsedSeconds - recordedSessionSeconds).coerceAtLeast(0)
        return recordedTotalSeconds.toLong() + pendingSeconds >= limitSeconds
    }
}
