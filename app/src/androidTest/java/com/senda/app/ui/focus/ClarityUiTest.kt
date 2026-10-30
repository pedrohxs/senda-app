package com.senda.app.ui.focus

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.domain.lifecycle.ProtectionHealth
import com.senda.app.domain.lifecycle.ProtectionStatus
import com.senda.app.ui.components.ProtectionStatusCard
import com.senda.app.ui.focus.editor.GroupEditorUiState
import com.senda.app.ui.focus.editor.GroupRulesSummary
import com.senda.app.ui.focus.editor.ScheduleConflictNotice
import com.senda.app.ui.focus.editor.ScheduleDraft
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.SendaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class ClarityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inicioNaoMostraCardDeProtecao() {
        compose.setContent { SendaTheme { Surface(color = DarkCanvas) {
            FocusScreen(FocusUiState(isLoading = false, protection = ProtectionHealth(ProtectionStatus.ACCESSIBILITY_DISABLED)),
                {}, {}, {})
        } } }
        compose.onNodeWithText("A proteção precisa de atenção").assertDoesNotExist()
        compose.onNodeWithText("Conferir acessibilidade").assertDoesNotExist()
        compose.onNodeWithText("Proteção em funcionamento").assertDoesNotExist()
        screenshot("home-clean.png")
    }

    @Test fun permissaoSemConexaoNaoExibeStatusAtivo() {
        compose.setContent { SendaTheme { Surface(color = DarkCanvas) {
            ProtectionStatusCard(ProtectionHealth(ProtectionStatus.ACCESSIBILITY_DISCONNECTED), {})
        } } }
        compose.onNodeWithText("Proteção em funcionamento").assertDoesNotExist()
        compose.onNodeWithText("A permissão está habilitada", substring = true).assertIsDisplayed()
    }

    @Test fun resumoExplicaContagemIndividualEJanelaContinua() {
        compose.setContent { SendaTheme { Surface(color = DarkCanvas) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GroupRulesSummary(GroupEditorUiState(isNew = true, groupId = "preview", sharedLimitAcrossApps = false,
                    sessionCountMode = SessionCountMode.WALL_CLOCK_WINDOW, maxDailyOpens = 0))
            }
        } } }
        compose.onNodeWithText("Limite de 30 min por dia, separado para cada app.").assertIsDisplayed()
        compose.onNodeWithText("O cronômetro continua correndo", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ver detalhes das regras").performClick()
        compose.onNodeWithText("Sem limite diário de liberações.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Os apps compartilham a mesma sessão.", substring = true).assertDoesNotExist()
        screenshot("group-rules-summary.png")
    }

    @Test fun avisoDeSobreposicaoExplicaQuePausaVenceBloqueio() {
        compose.setContent { SendaTheme { Surface(color = DarkCanvas) {
            ScheduleConflictNotice(listOf(
                ScheduleDraft(listOf(2), ScheduleType.STRICT, 9, 0, 18, 0),
                ScheduleDraft(listOf(2), ScheduleType.BREAK, 12, 0, 13, 0)
            ))
        } } }
        compose.onNodeWithText("Há horários sobrepostos").assertIsDisplayed()
        compose.onNodeWithText("Seg, 12:00–13:00: prevalece", substring = true).assertIsDisplayed()
        screenshot("schedule-overlap-warning.png")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
