package com.senda.app.domain.session

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SessionUsageCounterTest {
    private val zone = ZoneId.of("America/Fortaleza")
    private val epoch = Instant.parse("2026-10-26T15:00:00Z").toEpochMilli()

    @Test
    fun atrasoDoLoopContaTempoRealSemDependerDoNumeroDeTicks() {
        val counter = counter()
        counter.advance(7_500L, epoch + 7_500L, true)
        assertEquals(52_500L, counter.remainingMillis)
        assertEquals(7, counter.secondsOn("2026-10-26"))
    }

    @Test
    fun eventosRepetidosNoMesmoInstanteNaoConsomemTempo() {
        val counter = counter()
        repeat(10) { counter.advance(0L, epoch, true) }
        assertEquals(60_000L, counter.remainingMillis)
    }

    @Test
    fun pausasCurtasPreservamFracoesDeSegundo() {
        val counter = counter()
        counter.advance(500L, epoch + 500L, false)
        counter.advance(10_500L, epoch + 10_500L, true)
        counter.advance(11_000L, epoch + 11_000L, false)
        assertEquals(59_000L, counter.remainingMillis)
        assertEquals(1, counter.secondsOn("2026-10-26"))
    }

    @Test
    fun telaApagadaOuAppAusenteNaoConsomeTempoQuandoPausado() {
        val counter = counter(counting = false)
        counter.advance(120_000L, epoch + 120_000L, false)
        assertEquals(60_000L, counter.remainingMillis)
        assertEquals(emptyMap<String, Long>(), counter.usageMillisByDate)
    }

    @Test
    fun separaUsoNaViradaDoDiaInclusiveEmJanelaContinua() {
        val midnight = Instant.parse("2026-10-27T03:00:00Z").toEpochMilli()
        val counter = counter(start = midnight - 10_000L)
        counter.advance(30_000L, midnight + 20_000L, true)
        assertEquals(10, counter.secondsOn("2026-10-26"))
        assertEquals(20, counter.secondsOn("2026-10-27"))
    }

    @Test
    fun expirarLimitaUsoAoSaldoRestante() {
        val counter = counter()
        counter.advance(90_000L, epoch + 90_000L, true)
        assertEquals(0L, counter.remainingMillis)
        assertEquals(60, counter.secondsOn("2026-10-26"))
    }

    @Test
    fun retomarCheckpointPreservaUsoEFracoesSemSomarGapDesconhecido() {
        val first = counter()
        first.advance(1_250L, epoch + 1_250L, true)
        val restored = SessionUsageCounter(first.remainingMillis, 1_250L, epoch + 1_250L,
            false, first.usageMillisByDate, zone)
        restored.advance(100_000L, epoch + 100_000L, true)
        restored.advance(100_750L, epoch + 100_750L, true)
        assertEquals(58_000L, restored.remainingMillis)
        assertEquals(2, restored.secondsOn("2026-10-26"))
    }

    @Test
    fun restauracaoDeJanelaContinuaAcrescentaSomenteIntervaloPosteriorAoCheckpoint() {
        val first = counter()
        first.advance(10_000L, epoch + 10_000L, true)
        val restored = SessionUsageCounter(first.remainingMillis, 10_000L, epoch + 10_000L,
            true, first.usageMillisByDate, zone)
        restored.advance(30_000L, epoch + 30_000L, true)
        assertEquals(30_000L, restored.remainingMillis)
        assertEquals(30, restored.secondsOn("2026-10-26"))
    }

    @Test
    fun ajusteDoRelogioCivilNaoAlteraDuracaoConsumida() {
        val counter = counter()
        counter.advance(5_000L, epoch - 3_600_000L, true)
        assertEquals(55_000L, counter.remainingMillis)
    }

    private fun counter(start: Long = epoch, counting: Boolean = true) =
        SessionUsageCounter(60_000L, 0L, start, counting, zone = zone)
}
