package com.senda.app.domain.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatHealthTest {

    private val now = 1_700_000_000_000L

    @Test
    fun semBatimentoRegistradoEstaParado() {
        assertTrue(HeartbeatHealth.isStale(now, lastHeartbeatMillis = 0L))
    }

    @Test
    fun batimentoRecenteEstaSaudavel() {
        assertFalse(HeartbeatHealth.isStale(now, now - 2 * 60 * 1000L))
    }

    @Test
    fun limiteExatoAindaEstaSaudavel() {
        assertFalse(
            HeartbeatHealth.isStale(now, now - HeartbeatHealth.STALE_AFTER_MILLIS)
        )
    }

    @Test
    fun acimaDoLimiteEstaParado() {
        assertTrue(
            HeartbeatHealth.isStale(now, now - HeartbeatHealth.STALE_AFTER_MILLIS - 1L)
        )
    }

    @Test
    fun relogioVoltandoNaoDisparaAlerta() {
        assertFalse(HeartbeatHealth.isStale(now, now + 5 * 60 * 1000L))
    }
}
