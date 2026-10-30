package com.senda.app.data.repository

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.ReportDayEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UsageStatsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val days = mutableListOf<ReportDayEntity>()
    private val goals = mutableListOf<DailyGoalEntity>()
    private val logs = mutableListOf<DailyUsageLogEntity>()
    private val groups = listOf(AppGroupEntity(id = "g", name = "Redes", dailyLimitMinutes = 1))

    @Test fun mediaIncluiDiasMonitoradosSemUso() {
        (1..7).forEach { complete(it) }
        logs += log(1, 3600)
        val stats = calculate()
        assertEquals(3600 / 7, stats.weekAverageSeconds)
        assertEquals(7, stats.completeDays)
        assertEquals(6, stats.week.count { it.seconds == 0 })
    }

    @Test fun diaAtualNaoContaminaMediaComparacaoOuSequencia() {
        complete(1); complete(2)
        logs += log(0, 9999)
        val stats = calculate()
        assertEquals(9999, stats.todaySeconds)
        assertEquals(0, stats.weekAverageSeconds)
        assertEquals(2, stats.streakDays)
        assertEquals(today.minusDays(1).toString(), stats.week.last().date)
    }

    @Test fun diaIncompletoFicaVisivelMasForaDaMedia() {
        complete(1); complete(2)
        days[0] = days[0].copy(coveredMillis = 30_000L)
        logs += log(1, 9000); logs += log(2, 120)
        val stats = calculate()
        assertEquals(120, stats.weekAverageSeconds)
        assertEquals(1, stats.completeDays)
        assertEquals(9000, stats.week.last().seconds)
        assertEquals(DayCoverage.PARTIAL, stats.week.last().coverage)
        assertEquals(0, stats.streakDays)
    }

    @Test fun dadosLegadosNaoInventamDiasCompletosOuMetas() {
        logs += log(1, 300)
        val stats = calculate()
        assertEquals(0, stats.weekAverageSeconds)
        assertEquals(0, stats.completeDays)
        assertEquals(0, stats.streakDays)
        assertEquals(300, stats.week.last().seconds)
        assertFalse(stats.dayDetails.last().goalsKnown)
        assertEquals(DayCoverage.UNKNOWN, stats.week.last().coverage)
    }

    @Test fun comparaDoisPeriodosDeSeteDiasComMesmaCobertura() {
        (1..14).forEach { complete(it); logs += log(it, if (it <= 7) 600 else 1200) }
        val stats = calculate()
        assertEquals(600, stats.weekAverageSeconds)
        assertEquals(1200, stats.previousWeekAverageSeconds)
        assertEquals(-50, stats.trendPercent)
    }

    @Test fun periodoIncompletoNaoGeraPercentualEnganoso() {
        (1..13).forEach { complete(it); logs += log(it, 600) }
        assertNull(calculate().trendPercent)
        assertEquals(TrendUnavailableReason.INSUFFICIENT_COVERAGE, calculate().trendUnavailableReason)
    }

    @Test fun periodoAnteriorZeradoNaoDividePorZero() {
        (1..14).forEach { complete(it) }
        logs += log(1, 60)
        assertNull(calculate().trendPercent)
        assertEquals(TrendUnavailableReason.PREVIOUS_ZERO, calculate().trendUnavailableReason)
    }

    @Test fun limiteAtualNaoReescreveSucessoDoPassado() {
        complete(1, limit = 600)
        logs += log(1, 120)
        // The current group limit is only 60 seconds; yesterday's goal was 600.
        assertEquals(1, calculate().streakDays)
        assertEquals(600, calculate().dayDetails.last().goals.single().limitSeconds)
    }

    @Test fun aumentarLimiteAtualNaoTransformaFalhaAntigaEmSucesso() {
        complete(1, limit = 60)
        logs += log(1, 120)
        val stats = UsageStatsCalculator.calculate(today, groups.map { it.copy(dailyLimitMinutes = 240) },
            logs, emptyList(), days, goals)
        assertEquals(0, stats.streakDays)
    }

    @Test fun cadaAplicativoComLimiteIndependentePrecisaCumprirSuaMeta() {
        complete(1)
        goals.clear()
        val date = today.minusDays(1).toString()
        val a = UsageKey.forTarget("g", "a")
        val b = UsageKey.forTarget("g", "b")
        goals += DailyGoalEntity(date, a, "g", "Redes", "App A", 60, false)
        goals += DailyGoalEntity(date, b, "g", "Redes", "App B", 60, false)
        logs += log(1, 70, a); logs += log(1, 10, b)
        assertEquals(0, calculate().streakDays)
        assertEquals(listOf(70, 10), calculate().dayDetails.last().goals.map { it.usedSeconds })
    }

    @Test fun excederUmGrupoNaoPodeSerCompensadoPeloOutro() {
        complete(1, limit = 60)
        goals += DailyGoalEntity(today.minusDays(1).toString(), "other", "other", "Outros", "Outros", 600, true)
        logs += log(1, 100)
        assertEquals(0, calculate().streakDays)
    }

    @Test fun sequenciaNaoTemCorteArtificialEmTrintaDias() {
        (1..40).forEach { complete(it) }
        assertEquals(40, calculate().streakDays)
    }

    @Test fun diaSemMetaRegistradaNaoViraVitoria() {
        complete(1)
        goals.clear()
        assertEquals(0, calculate().streakDays)
    }

    @Test fun diaSemLimiteDiarioNaoViraVitoriaComMetaZero() {
        complete(1, limit = 0)
        assertEquals(0, calculate().streakDays)
        assertTrue(calculate().dayDetails.last().goals.isEmpty())
    }

    @Test fun mudancaDeDivisaoDoLimiteImpedeAvaliarSequenciaDoDia() {
        complete(1)
        days[0] = days[0].copy(goalsReliable = false)
        assertEquals(0, calculate().streakDays)
        assertFalse(calculate().dayDetails.last().goalsKnown)
    }

    @Test fun meiaNoiteIncluiDiaRecemConcluido() {
        complete(0); complete(1)
        assertEquals(1, calculate().streakDays)
        assertEquals(2, UsageStatsCalculator.calculate(today.plusDays(1), groups, logs, emptyList(), days, goals).streakDays)
    }

    @Test fun grupoRemovidoMantemDetalhamentoETotalDoPeriodo() {
        complete(1); logs += log(1, 120)
        val stats = UsageStatsCalculator.calculate(today, emptyList(), logs, emptyList(), days, goals)
        assertEquals("Redes", stats.groupSummaries.single().name)
        assertTrue(stats.groupSummaries.single().isArchived)
        assertEquals(120, stats.dayDetails.last().groups.single().seconds)
    }

    @Test fun metasDeJanelasDiferentesNaoSomamConsumoUmasDasOutras() {
        complete(1, limit = 60)
        val scope = UsageKey.forLimit("g", "weekend")
        goals += DailyGoalEntity(today.minusDays(1).toString(), scope, "g", "Redes", "Fim de semana", 120, true)
        logs += log(1, 30); logs += log(1, 90, scope)
        val stats = calculate()
        assertEquals(1, stats.streakDays)
        assertEquals(listOf(30, 90), stats.dayDetails.last().goals.map { it.usedSeconds })
        assertEquals(120, stats.groupSummaries.single().seconds)
        assertTrue(stats.groupSummaries.single().hasCustomLimits)
    }

    @Test fun usoDeHojeTemGruposSemMisturarHistorico() {
        logs += log(0, 120)
        logs += log(0, 45, UsageKey.forLimit("g", "custom"))
        logs += log(1, 900)
        val stats = calculate()
        assertEquals(165, stats.todaySeconds)
        assertEquals("Redes", stats.todayGroups.single().name)
        assertEquals(165, stats.todayGroups.single().seconds)
        assertEquals(900, stats.groupSummaries.single().seconds)
        val tomorrow = UsageStatsCalculator.calculate(today.plusDays(1), groups, logs, emptyList(), days, goals)
        assertEquals(0, tomorrow.todaySeconds)
        assertTrue(tomorrow.todayGroups.isEmpty())
    }

    private fun complete(offset: Int, limit: Int = 600) {
        val date = today.minusDays(offset.toLong()).toString()
        days += ReportDayEntity(date, 86_400_000L, 86_400_000L, true, "America/Fortaleza")
        goals += DailyGoalEntity(date, "g", "g", "Redes", "Redes", limit, true)
    }
    private fun log(offset: Int, seconds: Int, key: String = "g") = DailyUsageLogEntity(
        date = today.minusDays(offset.toLong()).toString(), groupId = key, totalTimeUsedSeconds = seconds)
    private fun calculate() = UsageStatsCalculator.calculate(today, groups, logs, emptyList(), days, goals)
}
