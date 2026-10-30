package com.senda.app.domain.lifecycle

import org.junit.Assert.*
import org.junit.Test

class ProtectionHealthResolverTest {
    @Test fun permissoesComServicoRespondendoPermitemStatusAtivo() {
        assertEquals(ProtectionStatus.ACTIVE, health().status)
        assertTrue(health().monitoringAvailable)
    }
    @Test fun permissaoHabilitadaSemConexaoNaoSignificaProtecaoAtiva() {
        assertEquals(ProtectionStatus.ACCESSIBILITY_DISCONNECTED, health(connected = false).status)
        assertFalse(health(connected = false).monitoringAvailable)
    }
    @Test fun heartbeatAntigoDeOutroServicoNaoEscondeServicoParado() {
        assertEquals(ProtectionStatus.SERVICE_STOPPED, health(running = false).status)
    }
    @Test fun acessibilidadeDesativadaTemPrioridadeSobreDemaisFalhas() {
        assertEquals(ProtectionStatus.ACCESSIBILITY_DISABLED, health(accessibility = false, overlay = false, running = false).status)
    }
    @Test fun faltaDeSobreposicaoImpedeTelasDeBloqueio() {
        assertEquals(ProtectionStatus.OVERLAY_MISSING, health(overlay = false).status)
    }
    @Test fun respostaComMaisDeNoventaSegundosNaoComprovaFuncionamento() {
        assertEquals(ProtectionStatus.ACTIVE, health(last = 10_000L).status)
        assertEquals(ProtectionStatus.SERVICE_UNRESPONSIVE, health(last = 9_999L).status)
        assertEquals(ProtectionStatus.SERVICE_UNRESPONSIVE, health(last = 0L).status)
    }
    @Test fun heartbeatNoFuturoNaoGeraFalsoStatusSaudavel() {
        assertEquals(ProtectionStatus.SERVICE_UNRESPONSIVE, health(last = 100_001L).status)
    }
    @Test fun avisosDesativadosNaoSaoConfundidosComMonitoramentoParado() {
        assertEquals(ProtectionStatus.NOTIFICATIONS_DISABLED, health(notifications = false).status)
        assertTrue(health(notifications = false).monitoringAvailable)
    }
    @Test fun restricaoDeBateriaViraAvisoSemPrometerSobrevivenciaDoServico() {
        assertEquals(ProtectionStatus.BATTERY_RESTRICTED, health(battery = false).status)
        assertTrue(health(battery = false).monitoringAvailable)
    }
    private fun health(accessibility: Boolean = true, overlay: Boolean = true, connected: Boolean = true,
        running: Boolean = true, last: Long = 90_000L, notifications: Boolean = true, battery: Boolean = true) =
        ProtectionHealthResolver.resolve(accessibility, overlay, connected, running, last, 100_000L, notifications, battery)
}
