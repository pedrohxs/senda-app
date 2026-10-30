package com.senda.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionClockTest {

    @Test
    fun mostraMinutosESegundosAbaixoDeUmaHora() {
        assertEquals("05:00", formatRemaining(300))
        assertEquals("00:09", formatRemaining(9))
    }

    @Test
    fun mostraHorasQuandoPassaDeSessentaMinutos() {
        assertEquals("1:05:00", formatRemaining(3_900))
        assertEquals("2:00:01", formatRemaining(7_201))
    }

    @Test
    fun tempoNegativoViraZero() {
        assertEquals("00:00", formatRemaining(-30))
    }
}
