package com.senda.app.ui.focus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.MainActivity
import com.senda.app.data.preferences.SendaSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SetupRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun configuracaoConcluidaPermiteDiagnosticarPermissoesAusentesNaTelaInicial() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SendaSettingsStore(context).completeSetup()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10_000L) {
                compose.onAllNodesWithText("Seu uso monitorado").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Uso monitorado hoje").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Uso monitorado hoje").assertIsDisplayed()
            compose.onNodeWithText("Bem-vindo ao Senda").assertDoesNotExist()
        }
    }
}
