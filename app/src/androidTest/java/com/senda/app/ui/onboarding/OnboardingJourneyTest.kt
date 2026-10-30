package com.senda.app.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.domain.setup.SetupGoal
import com.senda.app.ui.theme.SendaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnboardingJourneyTest {
    @get:Rule val compose = createComposeRule()
    @Test fun escolhaDoObjetivoPassaPorPermissoesERevisaoAntesDeCriarGrupo() {
        var selected: SetupGoal? = null
        compose.setContent { SendaTheme {
            OnboardingScreen(SendaPermissionState(true, true, true, false, false), {}, {}, {}, {}, {}, {}, { selected = it })
        } }
        capture("setup-goals.png")
        compose.onNodeWithText("Focar nos estudos").performClick()
        compose.onNodeWithText("Preparar proteção").performClick()
        compose.onNodeWithText("Administrador").assertDoesNotExist()
        compose.onNodeWithText("Ver proteções opcionais").performScrollTo().performClick()
        compose.onNodeWithText("Administrador").performScrollTo().assertIsDisplayed()
        capture("setup-permissions.png")
        assertNull(selected)
        compose.onNodeWithText("Revisar próximo passo").performClick()
        compose.onNodeWithText("20 min por dia • sessões de 3 min • espera de 15 min").assertIsDisplayed()
        capture("setup-review.png")
        assertNull(selected)
        compose.onNodeWithText("Criar primeiro grupo").performClick()
        assertEquals(SetupGoal.STUDY, selected)
    }
    @Test fun permissoesPendentesImpedemAvancarParaAtivacao() {
        compose.setContent { SendaTheme {
            OnboardingScreen(SendaPermissionState(false, false, false, false, false), {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Preparar proteção").performClick()
        compose.onNodeWithText("Revisar próximo passo").assertIsNotEnabled()
    }
    private fun capture(name: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
