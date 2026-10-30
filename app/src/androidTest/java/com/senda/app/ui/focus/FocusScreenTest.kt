package com.senda.app.ui.focus

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.domain.policy.GroupStatus
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.SendaTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

class FocusScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun cartaoDeSessaoAtivaMostraTempoRestanteEAlvo() {
        val state = fixture().copy(
            activeSession = ActiveSessionUi(
                groupName = "Redes sociais",
                targetLabel = "Instagram",
                remainingSeconds = 272
            )
        )
        setContent(state)

        compose.onNodeWithText("Sessão ativa").assertIsDisplayed()
        compose.onNodeWithText("4:32").assertIsDisplayed()
        compose.onNodeWithText("Instagram • Redes sociais").assertIsDisplayed()
        compose.onNodeWithText("Encerrar sessão").assertIsDisplayed()
        screenshot("home-sessao.png")
    }

    @Test
    fun proximoBloqueioMostraQuantoFalta() {
        val now = System.currentTimeMillis()
        val state = fixture().copy(
            nowMillis = now,
            nextBlock = NextBlockUi(
                groupName = "Bloqueio noturno",
                windowName = null,
                type = ScheduleType.STRICT,
                startMillis = now + (2 * 60 + 5) * 60_000L,
                endMillis = now + (3 * 60) * 60_000L,
                isNow = false
            )
        )
        setContent(state)

        compose.onNodeWithText("Próximo bloqueio").assertIsDisplayed()
        compose.onNodeWithText("Em 2 h 05 min").assertIsDisplayed()
        compose.onNodeWithText("Bloqueio noturno • Bloqueio Rígido").assertIsDisplayed()
    }

    @Test
    fun grupoComLimiteMostraProgressoDoDia() {
        setContent(fixture())

        compose.onNodeWithText("14 / 30 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun semGruposMostraEstadoVazioComAcao() {
        setContent(FocusUiState(isLoading = false))

        compose.onNodeWithText("Nenhum grupo ainda").assertIsDisplayed()
        compose.onNodeWithText("Criar meu primeiro grupo").assertIsDisplayed()
        compose.onNodeWithText("Nenhum bloqueio agendado nos próximos dias.").assertIsDisplayed()
        screenshot("home-vazio.png")
    }

    @Test
    fun falhaDeLeituraMostraErroComNovaTentativa() {
        setContent(FocusUiState(isLoading = false, loadFailed = true))

        compose.onNodeWithText("Não foi possível ler os grupos.").assertIsDisplayed()
        compose.onNodeWithText("Tentar de novo").assertIsDisplayed()
    }

    private fun setContent(state: FocusUiState) {
        compose.setContent {
            SendaTheme {
                Surface(color = DarkCanvas) {
                    FocusScreen(
                        state = state,
                        onCreateGroup = {},
                        onEditGroup = {},
                        onOpenSettings = {}
                    )
                }
            }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fixture() = FocusUiState(
        isLoading = false,
        groups = listOf(
            GroupCardUi(
                id = "1",
                name = "Redes sociais",
                colorHex = "#A1B976",
                status = GroupStatus.LIMITED,
                blockReason = null,
                usedSeconds = 14 * 60,
                dailyLimitMinutes = 30,
                appPackages = listOf("com.instagram.android"),
                hasSchedules = true
            )
        ),
        totalUsedSeconds = 14 * 60,
        totalImpulses = 2,
        totalDailyLimitMinutes = 30,
        nowMillis = System.currentTimeMillis()
    )
}
