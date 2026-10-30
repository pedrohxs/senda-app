package com.senda.app.ui.focus.editor

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.ui.theme.SendaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CustomLimitRulesUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun novoHorarioOfereceApenasLimiteEBloqueio() {
        compose.setContent { SendaTheme { ScheduleDialog(null, {}, onConfirm = {}) } }
        compose.onNodeWithText("Modo Limite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bloqueio Rígido").assertIsDisplayed()
        compose.onNodeWithText("Pausa").assertDoesNotExist()
    }
    @Test fun configuraTempoDiarioSessaoEEsperaSomenteParaAJanela() {
        var result: LimitRules? = null
        compose.setContent { SendaTheme { CustomLimitRulesDialog(LimitRules(), onDismiss = {}, onConfirm = { result = it }) } }
        compose.onNodeWithText("Tempo diário máximo (min)").performTextReplacement("90")
        compose.onNodeWithText("Duração máxima da sessão (min)").performTextReplacement("15")
        compose.onNodeWithText("Espera entre sessões (min)").performScrollTo().performTextReplacement("3")
        capture("custom-limit-form.png")
        compose.onNodeWithText("Aplicar a este limite").performClick()
        assertEquals(90, result?.dailyLimitMinutes); assertEquals(15, result?.sessionLimitMinutes)
        assertEquals(3, result?.cooldownMinutes)
    }
    @Test fun campoInvalidoNaoPermiteAplicarValorAntigoSilenciosamente() {
        compose.setContent { SendaTheme { CustomLimitRulesDialog(LimitRules(), onDismiss = {}, onConfirm = {}) } }
        compose.onNodeWithText("Tempo diário máximo (min)").performTextReplacement("")
        compose.onNodeWithText("Aplicar a este limite").assertIsNotEnabled()
        compose.onNodeWithText("Tempo diário máximo (min)").performTextReplacement("60")
        compose.onNodeWithText("Aplicar a este limite").assertIsEnabled()
    }
    @Test fun duasJanelasPersonalizadasSobrepostasNaoPodemSerSalvas() {
        val first = ScheduleDraft(listOf(7, 1), ScheduleType.LIMITED, 0, 0, 0, 0, name = "Fim de semana", customRules = LimitRules())
        val second = first.copy(ruleId = "another", name = "Outro")
        compose.setContent { SendaTheme {
            ScheduleDialog(first, {}, onConfirm = {}, otherSchedules = listOf(second))
        } }
        compose.onNodeWithText("Dois limites personalizados se sobrepõem.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salvar").performScrollTo().assertIsNotEnabled()
    }
    private fun capture(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), name).outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
