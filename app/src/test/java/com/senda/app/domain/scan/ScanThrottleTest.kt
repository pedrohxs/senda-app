package com.senda.app.domain.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanThrottleTest {

    @Test
    fun navegacaoRespeitaIntervaloCurto() {
        val throttle = ScanThrottle()

        assertTrue(throttle.allowNavigation(nowMillis = 1_000L))
        assertFalse(throttle.allowNavigation(nowMillis = 1_100L))
        assertTrue(throttle.allowNavigation(nowMillis = 1_400L))
    }

    @Test
    fun conteudoTemJanelaMaisFolgada() {
        val throttle = ScanThrottle()

        assertTrue(throttle.allowContent(nowMillis = 1_000L))
        assertFalse(throttle.allowContent(nowMillis = 1_500L))
        assertTrue(throttle.allowContent(nowMillis = 1_800L))
    }

    @Test
    fun navegacaoAdiaAVarreduraDeConteudo() {
        val throttle = ScanThrottle()

        assertTrue(throttle.allowNavigation(nowMillis = 1_000L))
        assertFalse(throttle.allowContent(nowMillis = 1_200L))
        assertTrue(throttle.allowContent(nowMillis = 1_800L))
    }

    @Test
    fun eventosDeConteudoNaoBloqueiamNavegacao() {
        val throttle = ScanThrottle()

        assertTrue(throttle.allowContent(nowMillis = 1_000L))
        assertTrue(throttle.allowNavigation(nowMillis = 1_400L))
    }

    @Test
    fun resetLiberaNovamente() {
        val throttle = ScanThrottle()

        assertTrue(throttle.allowNavigation(nowMillis = 1_000L))
        throttle.reset()

        assertTrue(throttle.allowNavigation(nowMillis = 1_050L))
    }
}
