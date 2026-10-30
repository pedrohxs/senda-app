package com.senda.app.ui.usage

import com.senda.app.data.repository.DailyUsageBar
import com.senda.app.data.repository.DailyUsageDetail
import com.senda.app.data.repository.DayCoverage
import com.senda.app.data.repository.GroupUsageSummary
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReportsCsvTest {

    private val generatedAt: Long = LocalDateTime.of(2026, 10, 30, 12, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun semanaSemDadosNaoGeraArquivo() {
        assertNull(ReportsCsv.build(UsageUiState(isLoading = false), generatedAt))
    }

    @Test
    fun cabecalhoTemPeriodoECobertura() {
        val csv = ReportsCsv.build(state(), generatedAt).orEmpty()

        assertTrue(csv.contains("Senda;relatório semanal"))
        // O gráfico usa os seis dias fechados + hoje.
        assertTrue(csv.contains("Período;20/10/2026 a 26/10/2026"))
        assertTrue(csv.contains("Dias completos;7 de 7"))
        assertTrue(csv.contains("Gerado em;30/10/2026 12:00"))
        assertTrue(csv.contains("Dia;Minutos monitorados;Aberturas;Cobertura"))
    }

    @Test
    fun diaCompletoTemMinutosAberturasECobertura() {
        val csv = ReportsCsv.build(state(), generatedAt).orEmpty()

        assertTrue(csv.contains("25/10/2026;30;4;completo"))
    }

    @Test
    fun diaEmAndamentoEntraComoIncompleto() {
        val csv = ReportsCsv.build(state(), generatedAt).orEmpty()

        assertTrue(csv.contains("26/10/2026;10;0;incompleto"))
    }

    @Test
    fun resumoTrazMediaDeSessaoEAberturasPorDia() {
        val csv = ReportsCsv.build(state(), generatedAt).orEmpty()

        assertTrue(csv.contains("Tempo médio por sessão (min);7,5"))
        assertTrue(csv.contains("Aberturas por dia (dias completos);4"))
        assertTrue(csv.contains("Aberturas no período;28"))
    }

    @Test
    fun nomeDeGrupoComPontoEVirgulaEhEscapado() {
        val csv = ReportsCsv.build(
            state().copy(groups = listOf(GroupUsageSummary("1", "Redes; sociais", 30, 7200))),
            generatedAt
        ).orEmpty()

        assertTrue(csv.contains("\"Redes; sociais\";120;30"))
    }

    private fun state(): UsageUiState {
        val week = (7 downTo 1).map { offset ->
            DailyUsageBar(
                date = "2026-10-${26 - offset}",
                label = "dia",
                seconds = 1800,
                isToday = false,
                coverage = DayCoverage.COMPLETE
            )
        }
        return UsageUiState(
            isLoading = false,
            todaySeconds = 600,
            week = week,
            dayDetails = week.map {
                DailyUsageDetail(it.date, 1800, emptyList(), DayCoverage.COMPLETE, opens = 4)
            },
            completeDays = 7,
            weekAverageSeconds = 1800,
            averageSessionSeconds = 450,
            opensPerDay = 4,
            totalOpens = 28,
            impulses = 3
        )
    }
}
