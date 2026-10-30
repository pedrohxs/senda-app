package com.senda.app.ui.usage

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.repository.DailyUsageBar
import com.senda.app.data.repository.DailyUsageDetail
import com.senda.app.data.repository.DayCoverage
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.DarkCanvas
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class UsageScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun semCoberturaNaoExibeMediaZeroComoResultadoConfirmado() {
        compose.setContent { SendaTheme { Surface(color = DarkCanvas) { UsageScreen(fixture()) } } }

        // Nenhum dia completo: a média do período é desconhecida ("—"), não zero.
        compose.onNodeWithText("Média anterior · —", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Histórico parcial")
            .performScrollTo()
            .assertIsDisplayed()
        screenshot("report-overview.png")

        compose.onNodeWithContentDescription("25/10", substring = true).performClick()
        compose.onNodeWithText("Cobertura desconhecida • fora da média e da sequência")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Sem registros neste dia")
            .performScrollTo()
            .assertIsDisplayed()
        screenshot("report-details.png")
    }

    @Test fun diaCompletoComUsoZeroExibeMediaConfirmada() {
        compose.setContent {
            SendaTheme {
                Surface(color = DarkCanvas) {
                    UsageScreen(fixture().copy(completeDays = 7))
                }
            }
        }

        // Com a semana inteira monitorada, a média existe e é 0 min — e o aviso
        // de histórico parcial desaparece.
        compose.onNodeWithText("Média anterior · 0 min", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Histórico parcial").assertDoesNotExist()
        compose.onNodeWithText("0 min").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun fixture(): UsageUiState {
        val today = LocalDate.of(2026, 10, 26)
        val week = (7 downTo 1).map { offset ->
            DailyUsageBar(
                date = today.minusDays(offset.toLong()).toString(),
                label = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[7 - offset],
                seconds = 0,
                isToday = false,
                // Um dia parcial basta para a tela mostrar o relatório em vez do estado vazio.
                coverage = if (offset == 7) DayCoverage.PARTIAL else DayCoverage.UNKNOWN
            )
        }
        return UsageUiState(
            isLoading = false,
            week = week,
            dayDetails = week.map { DailyUsageDetail(it.date, 0, emptyList(), it.coverage) }
        )
    }
}
