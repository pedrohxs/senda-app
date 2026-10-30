package com.senda.app.ui.settings

import com.senda.app.core.DiagnosticsLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticsFacts(
    val appVersion: String,
    val appVersionCode: Int,
    val androidRelease: String,
    val androidSdk: Int,
    val device: String,
    val protectedItems: List<Pair<String, String>>,
    val databaseError: String?,
    val generatedAtMillis: Long
)

/**
 * Texto puro do relatório de diagnóstico: versionável, testável e exportável
 * pelo usuário sem depender de logcat.
 */
object DiagnosticsReport {

    fun format(facts: DiagnosticsFacts, events: List<DiagnosticsLog.Event>): String {
        val generatedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
            .format(Date(facts.generatedAtMillis))

        return buildString {
            appendLine("Senda — diagnóstico")
            appendLine("Gerado em: $generatedAt")
            appendLine("Versão: ${facts.appVersion} (${facts.appVersionCode})")
            appendLine("Android: ${facts.androidRelease} (API ${facts.androidSdk})")
            appendLine("Aparelho: ${facts.device}")
            appendLine("Banco de dados: ${facts.databaseError ?: "ok"}")
            appendLine()
            appendLine("Proteção")
            if (facts.protectedItems.isEmpty()) {
                appendLine("- sem informações")
            } else {
                facts.protectedItems.forEach { (label, value) ->
                    appendLine("- $label: $value")
                }
            }
            appendLine()
            appendLine("Eventos (mais recente primeiro, ${events.size} registrados)")
            if (events.isEmpty()) {
                appendLine("- nenhum evento")
            } else {
                events.forEach { event ->
                    appendLine(
                        "${DiagnosticsLog.formatTime(event.atMillis)} " +
                            "[${event.source}] ${event.message}"
                    )
                }
            }
        }
    }
}
