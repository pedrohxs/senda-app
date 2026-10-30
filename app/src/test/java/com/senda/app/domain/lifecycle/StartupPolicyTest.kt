package com.senda.app.domain.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPolicyTest {

    @Test
    fun iniciaServicosComAcessibilidadeAtiva() {
        assertTrue(StartupPolicy.shouldStartServices(accessibilityEnabled = true))
    }

    @Test
    fun naoIniciaServicosSemAcessibilidade() {
        assertFalse(StartupPolicy.shouldStartServices(accessibilityEnabled = false))
    }
}
