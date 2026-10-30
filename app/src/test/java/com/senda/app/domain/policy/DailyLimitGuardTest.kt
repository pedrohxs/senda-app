package com.senda.app.domain.policy

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLimitGuardTest {

    @Test
    fun limiteZeradoNuncaInterrompe() {
        val policy = policy(limitMinutes = 0, recordedSeconds = 10 * 60)

        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 10 * 60))
    }

    @Test
    fun abaixoDoLimiteNaoInterrompe() {
        val policy = policy(limitMinutes = 30, recordedSeconds = 20 * 60)

        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 5 * 60))
    }

    @Test
    fun sessaoEmAndamentoContaParaOLimite() {
        // 29 min gravados + 2 min de sessão estouram o limite de 30.
        val policy = policy(limitMinutes = 30, recordedSeconds = 29 * 60)

        assertTrue(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 2 * 60))
    }

    @Test
    fun limiteExatoInterrompe() {
        val policy = policy(limitMinutes = 30, recordedSeconds = 25 * 60)

        assertTrue(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 5 * 60))
    }

    @Test
    fun umSegundoAntesDoLimiteNaoInterrompe() {
        val policy = policy(limitMinutes = 30, recordedSeconds = 25 * 60)

        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 5 * 60 - 1))
    }

    @Test
    fun semUsoGravadoConsideraApenasASessao() {
        val policy = policy(limitMinutes = 5, recordedSeconds = 0)

        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 4 * 60))
        assertTrue(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 5 * 60))
    }

    @Test
    fun naoSomaNovamenteASessaoJaGravadaParcialmente() {
        val policy = policy(limitMinutes = 5, recordedSeconds = 269)
        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 90, recordedSessionSeconds = 60))
        assertTrue(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 90, recordedSessionSeconds = 60,
            recordedTotalSeconds = 270))
    }

    @Test
    fun sessaoInteiramentePersistidaContaUmaUnicaVez() {
        val policy = policy(limitMinutes = 5, recordedSeconds = 200)
        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 200, recordedSessionSeconds = 200))
    }

    @Test
    fun usaSaldoDaLeituraConsistenteEmVezDeSnapshotAntigo() {
        val policy = policy(limitMinutes = 5, recordedSeconds = 600)
        assertFalse(DailyLimitGuard.isExceeded(policy, sessionUsedSeconds = 10,
            recordedSessionSeconds = 5, recordedTotalSeconds = 100))
    }

    private fun policy(limitMinutes: Int, recordedSeconds: Int) = GroupPolicy(
        group = AppGroupEntity(
            id = "g1",
            name = "Teste",
            dailyLimitMinutes = limitMinutes
        ),
        items = emptyList(),
        schedules = emptyList(),
        usage = DailyUsageLogEntity(
            date = "2026-10-21",
            groupId = "g1",
            totalTimeUsedSeconds = recordedSeconds
        )
    )
}
