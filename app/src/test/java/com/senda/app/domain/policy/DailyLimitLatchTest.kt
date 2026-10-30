package com.senda.app.domain.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyLimitLatchTest {

    private val zone = ZoneId.systemDefault()

    private fun clockAt(date: LocalDate): Clock = Clock.fixed(
        date.atStartOfDay(zone).plusHours(9).toInstant(),
        zone
    )

    @Test
    fun grupoMarcadoContinuaBloqueadoNoMesmoDia() {
        val latch = DailyLimitLatch(clockAt(LocalDate.of(2026, 5, 10)))

        assertFalse(latch.isReached("group-1", 30))

        latch.markReached("group-1", 30)

        assertTrue(latch.isReached("group-1", 30))
        assertFalse(latch.isReached("group-2", 30))
    }

    @Test
    fun viradaDoDiaZeraOMarcado() {
        val today = LocalDate.of(2026, 5, 10)
        val latch = DailyLimitLatch(clockAt(today))
        latch.markReached("group-1", 30)
        assertTrue(latch.isReached("group-1", 30))

        val nextDay = DailyLimitLatch(clockAt(today.plusDays(1)))
        assertFalse(nextDay.isReached("group-1", 30))
    }

    @Test
    fun limiteAumentadoLiberaOBloqueioAntigo() {
        val latch = DailyLimitLatch(clockAt(LocalDate.of(2026, 5, 10)))
        latch.markReached("group-1", 30)

        assertFalse(latch.isReached("group-1", 45))
        assertFalse(latch.isReached("group-1", 30))
    }

    @Test
    fun limiteZeradoNaMarcacaoNaoConta() {
        val latch = DailyLimitLatch(clockAt(LocalDate.of(2026, 5, 10)))
        latch.markReached("group-1", 0)

        assertFalse(latch.isReached("group-1", 30))
    }

    @Test
    fun registroIgnoraGrupoVazio() {
        val latch = DailyLimitLatch(
            Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), zone)
        )
        latch.markReached("", 30)

        assertFalse(latch.isReached("", 0))
    }
}
