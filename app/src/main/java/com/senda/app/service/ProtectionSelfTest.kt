package com.senda.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.preferences.ServiceHeartbeatStore
import com.senda.app.ui.overlay.OverlayLifecycleOwner
import com.senda.app.ui.overlay.TestOverlayContent
import com.senda.app.ui.theme.SendaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** O que o teste de proteção confere. */
enum class ProtectionCheckKind {
    ACCESSIBILITY, SERVICE, HEARTBEAT, OVERLAY_PERMISSION, OVERLAY_WINDOW, NOTIFICATIONS, BATTERY
}

/** Ação que resolve (ou reavalia) o item reprovado. */
enum class ProtectionFix { ACCESSIBILITY, SERVICE, RETRY, OVERLAY, NOTIFICATIONS, BATTERY }

data class ProtectionCheck(
    val kind: ProtectionCheckKind,
    val passed: Boolean,
    val fix: ProtectionFix,
    val required: Boolean = true
)

data class ProtectionTestReport(
    val checks: List<ProtectionCheck>,
    val overlayMillis: Long
) {
    val allPassed: Boolean get() = checks.all { it.passed }
    val failures: List<ProtectionCheck> get() = checks.filter { !it.passed }
}

/**
 * Teste de proteção feito com o que existe de verdade no aparelho: as mesmas
 * permissões, o mesmo serviço e a mesma janela de sobreposição do bloqueio.
 *
 * O teste não simula nada. Ele cria uma janela `TYPE_APPLICATION_OVERLAY` de
 * tela cheia e a remove depois de [OVERLAY_MILLIS]; se essa janela não aparecer,
 * o bloqueio também não apareceria em um app de verdade.
 */
class ProtectionSelfTest(
    private val context: Context,
    private val permissions: FocoPermissions,
    private val heartbeats: ServiceHeartbeatStore
) {

    suspend fun checks(): List<ProtectionCheck> {
        val permission = permissions.current()
        val heartbeat = runCatching { heartbeats.last() }.getOrDefault(0L)
        val heartbeatFresh = heartbeat > 0L &&
            System.currentTimeMillis() - heartbeat in 0L..HEARTBEAT_FRESH_MILLIS

        return listOf(
            ProtectionCheck(
                kind = ProtectionCheckKind.ACCESSIBILITY,
                passed = permission.accessibilityEnabled && AppMonitor.accessibilityConnected,
                fix = ProtectionFix.ACCESSIBILITY
            ),
            ProtectionCheck(
                kind = ProtectionCheckKind.SERVICE,
                passed = AppMonitor.protectionServiceRunning,
                fix = ProtectionFix.SERVICE
            ),
            ProtectionCheck(
                kind = ProtectionCheckKind.HEARTBEAT,
                passed = heartbeatFresh,
                fix = ProtectionFix.RETRY
            ),
            ProtectionCheck(
                kind = ProtectionCheckKind.OVERLAY_PERMISSION,
                passed = permission.overlayEnabled,
                fix = ProtectionFix.OVERLAY
            ),
            ProtectionCheck(
                kind = ProtectionCheckKind.NOTIFICATIONS,
                passed = permission.notificationsEnabled,
                fix = ProtectionFix.NOTIFICATIONS,
                required = false
            ),
            ProtectionCheck(
                kind = ProtectionCheckKind.BATTERY,
                passed = permission.batteryOptimizationIgnored,
                fix = ProtectionFix.BATTERY,
                required = false
            )
        )
    }

    /** Mostra a sobreposição por [durationMillis] e devolve o resultado real. */
    suspend fun overlay(durationMillis: Long = OVERLAY_MILLIS): ProtectionCheck =
        withContext(Dispatchers.Main) {
            val shown = if (Settings.canDrawOverlays(context)) {
                runCatching { showWindow(durationMillis) }.getOrDefault(false)
            } else {
                false
            }
            ProtectionCheck(
                kind = ProtectionCheckKind.OVERLAY_WINDOW,
                passed = shown,
                fix = ProtectionFix.OVERLAY
            )
        }

    private suspend fun showWindow(durationMillis: Long): Boolean {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            setBackgroundColor(Color.rgb(17, 19, 18))
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                SendaTheme {
                    TestOverlayContent(durationMillis = durationMillis, onFinished = {})
                }
            }
        }
        owner.start()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            dimAmount = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val added = runCatching {
            windowManager.addView(view, params)
            true
        }.getOrElse { false }

        if (!added) {
            owner.destroy()
            runCatching { view.disposeComposition() }
            return false
        }

        // A própria composição remove a janela ao terminar a animação; o atraso
        // aqui é apenas a rede de segurança para o caso de a animação não rodar.
        delay(durationMillis + REMOVAL_GRACE_MILLIS)
        runCatching { windowManager.removeViewImmediate(view) }
            .recoverCatching { windowManager.removeView(view) }
        runCatching { view.disposeComposition() }
        owner.destroy()
        return true
    }

    companion object {
        const val OVERLAY_MILLIS: Long = 3_000L
        private const val REMOVAL_GRACE_MILLIS: Long = 400L
        private const val HEARTBEAT_FRESH_MILLIS: Long = 90_000L
    }
}
