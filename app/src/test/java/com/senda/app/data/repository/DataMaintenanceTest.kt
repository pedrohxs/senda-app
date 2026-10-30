package com.senda.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DataMaintenanceTest {

    @Test
    fun cutoffFicaUmAnoAntesDeHoje() {
        val today = LocalDate.of(2026, 10, 30)

        assertEquals(
            "2025-10-30",
            DataMaintenance.cutoffDate(today, DataMaintenance.DEFAULT_RETENTION_DAYS)
        )
    }

    @Test
    fun cutoffLidaComAnoBissexto() {
        val today = LocalDate.of(2028, 2, 29)

        // 365 dias antes de 29/02/2028 caem em 01/03/2027 (o intervalo inclui o
        // próprio 29/02, então não é "um ano civil").
        assertEquals(
            "2027-03-01",
            DataMaintenance.cutoffDate(today, DataMaintenance.DEFAULT_RETENTION_DAYS)
        )
    }

    @Test
    fun retencaoInvalidaNaoApagaTudo() {
        val today = LocalDate.of(2026, 10, 30)

        // Retenção 0 ou negativa é protegida: nunca gera um cutoff no futuro.
        assertEquals("2026-10-29", DataMaintenance.cutoffDate(today, 1))
        assertEquals("2026-10-29", DataMaintenance.cutoffDate(today, -5))
    }
}
