package com.senda.app.domain.session

import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.preferences.PersistedActiveSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionRestorerTest {

    @Test
    fun descartaSessaoDeOutroBoot() {
        val result = ActiveSessionRestorer.decide(
            session = session(),
            nowElapsedRealtime = 130_000L,
            currentBootCount = 8,
            foregroundPackage = PACKAGE
        )

        assertEquals(SessionRestoreDecision.Discard, result)
    }

    @Test
    fun restauraTempoAtivoSemInventarUsoDuranteAusenciaDoServico() {
        val result = ActiveSessionRestorer.decide(
            session = session(),
            nowElapsedRealtime = 130_000L,
            currentBootCount = 7,
            foregroundPackage = PACKAGE
        )

        val resume = result as SessionRestoreDecision.Resume
        assertEquals(200, resume.remainingSeconds)
        assertEquals(100, resume.usedSeconds)
    }

    @Test
    fun restauraTempoAtivoSemDescontarQuandoAppNaoEstaEmPrimeiroPlano() {
        val result = ActiveSessionRestorer.decide(
            session = session(),
            nowElapsedRealtime = 130_000L,
            currentBootCount = 7,
            foregroundPackage = "com.android.chrome"
        )

        val resume = result as SessionRestoreDecision.Resume
        assertEquals(200, resume.remainingSeconds)
        assertEquals(100, resume.usedSeconds)
    }

    @Test
    fun restauraJanelaDeRelogioPeloDeadline() {
        val result = ActiveSessionRestorer.decide(
            session = session(
                mode = SessionCountMode.WALL_CLOCK_WINDOW,
                deadlineElapsedRealtime = 130_000L
            ),
            nowElapsedRealtime = 100_000L,
            currentBootCount = 7,
            foregroundPackage = PACKAGE
        )

        val resume = result as SessionRestoreDecision.Resume
        assertEquals(30, resume.remainingSeconds)
        assertEquals(270, resume.usedSeconds)
    }

    @Test
    fun expiraSessaoQuandoDeadlineJaPassou() {
        val result = ActiveSessionRestorer.decide(
            session = session(
                mode = SessionCountMode.WALL_CLOCK_WINDOW,
                deadlineElapsedRealtime = 90_000L
            ),
            nowElapsedRealtime = 100_000L,
            currentBootCount = 7,
            foregroundPackage = PACKAGE
        )

        val expired = result as SessionRestoreDecision.Expired
        assertEquals(300, expired.usedSeconds)
    }

    @Test
    fun ausenciaLongaNaoExpiraTempoAtivoSalvo() {
        val result = ActiveSessionRestorer.decide(
            session = session(remainingSeconds = 10),
            nowElapsedRealtime = 130_000L,
            currentBootCount = 7,
            foregroundPackage = PACKAGE
        )

        val resume = result as SessionRestoreDecision.Resume
        assertEquals(10, resume.remainingSeconds)
    }

    @Test
    fun tempoAtivoExpiraSomenteComSaldoSalvoZerado() {
        val result = ActiveSessionRestorer.decide(session(remainingSeconds = 0), 130_000L, 7, PACKAGE)
        assertTrue(result is SessionRestoreDecision.Expired)
    }

    @Test
    fun preservaFracaoDeSegundoNaRestauracao() {
        val result = ActiveSessionRestorer.decide(
            session().copy(remainingMillis = 199_250L), 130_000L, 7, PACKAGE
        ) as SessionRestoreDecision.Resume
        assertEquals(200, result.remainingSeconds)
        assertEquals(199_250L, result.remainingMillis)
    }

    @Test
    fun janelaComMenosDeUmSegundoNaoExpiraAntesDoPrazo() {
        val result = ActiveSessionRestorer.decide(
            session(mode = SessionCountMode.WALL_CLOCK_WINDOW, deadlineElapsedRealtime = 130_100L),
            130_000L, 7, PACKAGE
        ) as SessionRestoreDecision.Resume
        assertEquals(1, result.remainingSeconds)
        assertEquals(100L, result.remainingMillis)
    }

    @Test
    fun descartaRelogioMonotonicoDeBootIncompativelMesmoSemMudancaDoBootCount() {
        assertEquals(SessionRestoreDecision.Discard,
            ActiveSessionRestorer.decide(session(), 50_000L, 7, PACKAGE))
    }

    @Test
    fun sessaoRestauradaNuncaExcedeADuracaoOriginal() {
        val result = ActiveSessionRestorer.decide(
            session = session(
                mode = SessionCountMode.WALL_CLOCK_WINDOW,
                deadlineElapsedRealtime = 999_000L
            ),
            nowElapsedRealtime = 100_000L,
            currentBootCount = 7,
            foregroundPackage = PACKAGE
        )

        val resume = result as SessionRestoreDecision.Resume
        assertTrue(resume.remainingSeconds <= 300)
    }

    private fun session(
        mode: SessionCountMode = SessionCountMode.ACTIVE_SCREEN_TIME,
        remainingSeconds: Int = 200,
        deadlineElapsedRealtime: Long = 400_000L
    ) = PersistedActiveSession(
        groupId = "group-1",
        packageName = PACKAGE,
        targetIdentifier = PACKAGE,
        isWebsite = false,
        mode = mode,
        durationSeconds = 300,
        remainingSeconds = remainingSeconds,
        deadlineElapsedRealtime = deadlineElapsedRealtime,
        cooldownMinutes = 10,
        savedAtElapsedRealtime = 100_000L,
        bootCount = 7
    )

    private companion object {
        const val PACKAGE = "com.instagram.android"
    }
}
