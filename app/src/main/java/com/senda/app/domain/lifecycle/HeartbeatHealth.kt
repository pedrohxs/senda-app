package com.senda.app.domain.lifecycle

/**
 * Avalia se o serviço de proteção ainda está vivo a partir do último
 * batimento gravado por ele.
 *
 * O batimento é gravado a cada ciclo do watchdog. Quando o sistema encerra o
 * serviço de primeiro plano, o batimento para de avançar e o worker periódico
 * consegue perceber a queda.
 */
object HeartbeatHealth {

    const val STALE_AFTER_MILLIS: Long = 10 * 60 * 1000L

    fun isStale(
        nowMillis: Long,
        lastHeartbeatMillis: Long
    ): Boolean {
        if (lastHeartbeatMillis <= 0L) return true

        val elapsed = nowMillis - lastHeartbeatMillis
        if (elapsed < 0L) return false

        return elapsed > STALE_AFTER_MILLIS
    }
}
