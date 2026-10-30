package com.senda.app.ui.settings

import com.senda.app.core.DiagnosticsLog
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private fun facts(databaseError: String? = null) = DiagnosticsFacts(
        appVersion = "0.1.11",
        appVersionCode = 12,
        androidRelease = "16",
        androidSdk = 36,
        device = "samsung SM-S921B",
        protectedItems = listOf(
            "Acessibilidade" to "conectada",
            "Sobreposição" to "permitido"
        ),
        databaseError = databaseError,
        generatedAtMillis = 1_700_000_000_000L
    )

    @Test
    fun relatorioTemCabecalhoVersaoEAparelho() {
        val text = DiagnosticsReport.format(facts(), emptyList())

        assertTrue(text.contains("Senda — diagnóstico"))
        assertTrue(text.contains("Versão: 0.1.11 (12)"))
        assertTrue(text.contains("Android: 16 (API 36)"))
        assertTrue(text.contains("Aparelho: samsung SM-S921B"))
        assertTrue(text.contains("Banco de dados: ok"))
    }

    @Test
    fun relatorioListaProtecaoEEventosNaOrdemRecebida() {
        val events = listOf(
            DiagnosticsLog.Event(1_700_000_002_000L, "overlay", "bloqueio já visível"),
            DiagnosticsLog.Event(1_700_000_001_000L, "acessibilidade", "bloqueio Instagram")
        )

        val text = DiagnosticsReport.format(facts(), events)

        assertTrue(text.contains("- Acessibilidade: conectada"))
        assertTrue(text.contains("- Sobreposição: permitido"))
        val firstIndex = text.indexOf("bloqueio já visível")
        val secondIndex = text.indexOf("bloqueio Instagram")
        assertTrue(firstIndex in 0 until secondIndex)
        assertTrue(text.contains("2 registrados"))
    }

    @Test
    fun relatorioMostraErroDoBancoQuandoExiste() {
        val text = DiagnosticsReport.format(facts(databaseError = "database disk image is malformed"), emptyList())

        assertTrue(text.contains("Banco de dados: database disk image is malformed"))
    }

    @Test
    fun semEventosMostraMensagemAmigavel() {
        val text = DiagnosticsReport.format(facts(), emptyList())

        assertTrue(text.contains("nenhum evento"))
    }
}
