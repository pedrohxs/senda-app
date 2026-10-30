package com.senda.app.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockDriftDetectorTest {

    private val anchor = ClockDriftDetector.Anchor(
        wallMillis = 1_000_000L,
        elapsedRealtime = 500_000L,
        bootCount = 7
    )

    @Test
    fun relogioNormalNaoDisparaTrapaca() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_060_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7
        )

        assertFalse(result.tampered)
        assertEquals(
            ClockDriftDetector.Anchor(1_060_000L, 560_000L, 7),
            result.nextAnchor
        )
    }

    @Test
    fun saltoParaFrenteDisparaTrapaca() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_600_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7
        )

        assertTrue(result.tampered)
    }

    @Test
    fun saltoParaTrasDisparaTrapaca() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 400_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7
        )

        assertTrue(result.tampered)
    }

    @Test
    fun derivaPequenaNaoDisparaTrapaca() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_001_000L,
            nowElapsedRealtime = 500_500L,
            bootCount = 7
        )

        assertFalse(result.tampered)
        assertNull(result.nextAnchor)
    }

    @Test
    fun relogioVoltadoAposRebootDisparaTrapaca() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = anchor.wallMillis - 30 * 60_000L,
            nowElapsedRealtime = 20_000L,
            bootCount = 8
        )

        assertTrue(result.tampered)
        assertEquals(
            ClockDriftDetector.Anchor(
                anchor.wallMillis - 30 * 60_000L,
                20_000L,
                8,
                0
            ),
            result.nextAnchor
        )
    }

    @Test
    fun pequenaCorrecaoDeHorarioAposRebootEhAceita() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = anchor.wallMillis - 3 * 60_000L,
            nowElapsedRealtime = 20_000L,
            bootCount = 8
        )

        assertFalse(result.tampered)
    }

    @Test
    fun rebootComRelogioNormalRecriaAncora() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_500_000L,
            nowElapsedRealtime = 20_000L,
            bootCount = 8
        )

        assertFalse(result.tampered)
        assertEquals(
            ClockDriftDetector.Anchor(1_500_000L, 20_000L, 8),
            result.nextAnchor
        )
    }

    @Test
    fun mudancaDeFusoNaoDisparaTrapaca() {
        val threeHoursInSeconds = 3 * 60 * 60

        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_000_000L + 60_000L + threeHoursInSeconds * 1_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7,
            nowZoneOffsetSeconds = threeHoursInSeconds
        )

        assertFalse(result.tampered)
        assertTrue(result.zoneChanged)
        // O fuso novo precisa entrar na âncora na hora.
        assertEquals(threeHoursInSeconds, result.nextAnchor?.zoneOffsetSeconds)
    }

    @Test
    fun viagemParaFusoAtrasNaoDisparaTrapaca() {
        val minusThreeHours = -3 * 60 * 60

        val result = ClockDriftDetector.evaluate(
            anchor = anchor.copy(zoneOffsetSeconds = 0),
            nowWallMillis = 1_000_000L + 60_000L + minusThreeHours * 1_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7,
            nowZoneOffsetSeconds = minusThreeHours
        )

        assertFalse(result.tampered)
        assertTrue(result.zoneChanged)
        assertEquals(minusThreeHours, result.nextAnchor?.zoneOffsetSeconds)
    }

    @Test
    fun relogioAdiantadoManualmenteAindaDisparaMesmoComFusoIgual() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_000_000L + 600_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7,
            nowZoneOffsetSeconds = 0
        )

        assertTrue(result.tampered)
    }

    @Test
    fun fusoDiferenteComRelogioAdiantadoTambemDispara() {
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_000_000L + 600_000L + 3_600_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7,
            nowZoneOffsetSeconds = 3_600
        )

        assertTrue(result.tampered)
    }

    @Test
    fun rebootEmOutroFusoNaoDisparaTrapaca() {
        val plusOneHour = 3_600

        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_000_000L + plusOneHour * 1_000L,
            nowElapsedRealtime = 20_000L,
            bootCount = 8,
            nowZoneOffsetSeconds = plusOneHour
        )

        assertFalse(result.tampered)
        assertTrue(result.zoneChanged)
        assertEquals(plusOneHour, result.nextAnchor?.zoneOffsetSeconds)
    }

    @Test
    fun derivaGrandeEntreAncorasAindaDispara() {
        // Duas horas de relógio contra 1 minuto de tempo monotônico, sem mudar fuso.
        val result = ClockDriftDetector.evaluate(
            anchor = anchor,
            nowWallMillis = 1_000_000L + 2 * 60 * 60_000L,
            nowElapsedRealtime = 560_000L,
            bootCount = 7,
            nowZoneOffsetSeconds = 0
        )

        assertTrue(result.tampered)
    }
}
