package com.senda.app.ui.usage

import com.senda.app.data.repository.DailyUsageBar
import com.senda.app.data.repository.DayCoverage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Monta o CSV do relatório semanal.
 *
 * Usa ponto e vírgula porque é o separador que o Excel em português espera, e
 * escapa aspas dos nomes de grupo para o arquivo não quebrar colunas.
 */
object ReportsCsv {

    private const val SEPARATOR = ";"
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun build(state: UsageUiState, generatedAtMillis: Long = System.currentTimeMillis()): String? {
        if (state.isEmpty) return null

        val zone = ZoneId.systemDefault()
        val days = state.chartDays()
        val first = days.firstOrNull()?.date?.let(::parseDate) ?: LocalDate.now()
        val last = days.lastOrNull()?.date?.let(::parseDate) ?: LocalDate.now()

        val lines = mutableListOf<List<String>>()
        lines.add(listOf("Senda", "relatório semanal"))
        lines.add(listOf("Período", "${first.format(DATE_FORMATTER)} a ${last.format(DATE_FORMATTER)}"))
        lines.add(
            listOf(
                "Gerado em",
                DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(generatedAtMillis).atZone(zone))
            )
        )
        lines.add(listOf("Dias completos", "${state.completeDays} de 7"))
        lines.add(emptyList())

        lines.add(listOf("Dia", "Minutos monitorados", "Aberturas", "Cobertura"))
        days.forEach { day ->
            lines.add(
                listOf(
                    parseDate(day.date).format(DATE_FORMATTER),
                    minutes(day.seconds),
                    day.opens(state).toString(),
                    coverageLabel(day.coverage)
                )
            )
        }
        lines.add(emptyList())

        lines.add(listOf("Resumo", "Valor"))
        lines.add(listOf("Tempo médio por sessão (min)", minutes(state.averageSessionSeconds)))
        lines.add(listOf("Aberturas por dia (dias completos)", state.opensPerDay.toString()))
        lines.add(listOf("Aberturas no período", state.totalOpens.toString()))
        lines.add(listOf("Média diária (min)", minutes(state.weekAverageSeconds)))
        lines.add(listOf("Pausas nos desafios", state.impulses.toString()))

        if (state.groups.isNotEmpty()) {
            lines.add(emptyList())
            lines.add(listOf("Grupo", "Minutos no período", "Limite diário (min)"))
            state.groups.forEach { group ->
                lines.add(
                    listOf(
                        group.name,
                        minutes(group.seconds),
                        if (group.limitMinutes > 0) group.limitMinutes.toString() else "sem limite"
                    )
                )
            }
        }

        // BOM no começo: é o que faz o Excel em português reconhecer os acentos.
        return "\uFEFF" + lines.joinToString(separator = "\r\n") { row ->
            row.joinToString(separator = SEPARATOR) { escape(it) }
        } + "\r\n"
    }

    private fun DailyUsageBar.opens(state: UsageUiState): Int =
        state.dayDetails.firstOrNull { it.date == date }?.opens ?: 0

    private fun coverageLabel(coverage: DayCoverage): String = when (coverage) {
        DayCoverage.COMPLETE -> "completo"
        DayCoverage.PARTIAL -> "incompleto"
        DayCoverage.UNKNOWN -> "desconhecido"
    }

    /** Minutos com uma casa quando faz sentido; inteiro quando é exato. */
    private fun minutes(seconds: Int): String {
        if (seconds <= 0) return "0"
        val remainder = seconds % 60
        if (remainder == 0) return (seconds / 60).toString()
        return String.format(java.util.Locale.US, "%.1f", seconds / 60.0).replace('.', ',')
    }

    private fun parseDate(raw: String): LocalDate =
        runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.now())

    private fun escape(value: String): String {
        val needsQuotes = value.contains(SEPARATOR) || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
