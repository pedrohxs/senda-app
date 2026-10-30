package com.senda.app.ui.focus.editor

import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.SessionCountMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupEditorDraftTest {

    private fun state() = GroupEditorUiState(
        isNew = false,
        groupId = "grupo-1"
    ).let { it.copy(baseline = it.toDraft()) }

    @Test
    fun estadoInicialNaoTemAlteracoes() {
        assertFalse(state().hasUnsavedChanges)
    }

    @Test
    fun renomearMarcaComoAlterado() {
        val alterado = state().copy(name = "Novo nome")

        assertTrue(alterado.hasUnsavedChanges)
    }

    @Test
    fun voltarAoNomeOriginalLimpaAAlteracao() {
        val alterado = state()
            .copy(name = "Novo nome")
            .copy(name = "")

        assertFalse(alterado.hasUnsavedChanges)
    }

    @Test
    fun adicionarAppMarcaComoAlterado() {
        val alterado = state().copy(selectedApps = mapOf("com.instagram.android" to "Instagram"))

        assertTrue(alterado.hasUnsavedChanges)
    }

    @Test
    fun trocarApenasACorMarcaComoAlterado() {
        val alterado = state().copy(colorHex = "#FF0000")

        assertTrue(alterado.hasUnsavedChanges)
    }

    @Test
    fun adicionarAgendamentoMarcaComoAlterado() {
        val alterado = state().copy(
            schedules = listOf(
                ScheduleDraft(
                    daysOfWeek = listOf(1, 2),
                    scheduleType = ScheduleType.LIMITED,
                    startHour = 8,
                    startMinute = 0,
                    endHour = 12,
                    endMinute = 0
                )
            )
        )

        assertTrue(alterado.hasUnsavedChanges)
    }

    @Test
    fun limiteQueVaiEVoltaNaoDeixaAlteracao() {
        val alterado = state()
            .copy(dailyLimitMinutes = 60)
            .copy(dailyLimitMinutes = 30)

        assertFalse(alterado.hasUnsavedChanges)
    }

    @Test
    fun trocarTipoDeDesafioMarcaComoAlterado() {
        val alterado = state().copy(interventionType = InterventionType.MATH)

        assertTrue(alterado.hasUnsavedChanges)
    }

    @Test
    fun pausaTemporariaNaoContaComoAlteracaoPendente() {
        val pausado = state().copy(temporaryDisableUntil = System.currentTimeMillis() + 3_600_000L)

        assertFalse(pausado.hasUnsavedChanges)
        assertFalse(pausado.hasRuleChanges)
    }

    @Test
    fun nomeECorNaoExigemAutorizacaoParaAlterarRegras() {
        val alterado = state().copy(name = "Redes sociais", colorHex = "#FFFFFF")
        assertTrue(alterado.hasUnsavedChanges)
        assertFalse(alterado.hasRuleChanges)
    }

    @Test
    fun todosOsCamposDeRegrasContinuamProtegidosMesmoJuntoComMudancaVisual() {
        val original = state().copy(name = "Nome novo", colorHex = "#FFFFFF")
        val alteracoes = listOf(
            original.copy(selectedApps = mapOf("test.app" to "App")),
            original.copy(dailyLimitMinutes = 60),
            original.copy(sessionLimitMinutes = 10),
            original.copy(cooldownMinutes = 0),
            original.copy(maxDailyOpens = 50),
            original.copy(sharedLimitAcrossApps = false),
            original.copy(sessionCountMode = SessionCountMode.WALL_CLOCK_WINDOW),
            original.copy(interventionType = InterventionType.MATH),
            original.copy(breathingSeconds = 5),
            original.copy(mathDifficulty = MathDifficulty.HARD),
            original.copy(mathEquationCount = 5),
            original.copy(progressiveFrictionEnabled = false),
            original.copy(progressiveSecondsPerOpen = 10),
            original.copy(alternativeAppPackage = "test.app"),
            original.copy(schedules = listOf(ScheduleDraft(listOf(2), ScheduleType.BREAK, 9, 0, 10, 0)))
        )
        alteracoes.forEach { assertTrue("Mudança de regra deve continuar protegida: $it", it.hasRuleChanges) }
    }

    @Test
    fun desfazerMudancaDeRegraPermiteSalvarSomenteAparencia() {
        val alterado = state().copy(dailyLimitMinutes = 60, name = "Novo nome")
            .copy(dailyLimitMinutes = 30)
        assertTrue(alterado.hasUnsavedChanges)
        assertFalse(alterado.hasRuleChanges)
    }

    @Test
    fun semBaselineNaoConsideraEdicaoComoSomenteVisual() {
        assertTrue(GroupEditorUiState(isNew = false, groupId = "grupo-1").hasRuleChanges)
    }

    @Test
    fun semBaselineNaoBloqueiaOFechamento() {
        val semBaseline = GroupEditorUiState(isNew = true, groupId = "novo")

        assertFalse(semBaseline.hasUnsavedChanges)
    }
}
