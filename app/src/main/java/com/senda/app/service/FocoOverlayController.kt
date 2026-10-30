package com.senda.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.ui.overlay.OverlayLifecycleOwner
import com.senda.app.ui.overlay.StrictBlockOverlay
import com.senda.app.ui.theme.SendaTheme

class FocoOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDismissBlock: (InterventionDecision.Block) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }
    private var audioFocusRequest: AudioFocusRequest? = null

    private var host: OverlayHost? = null

    /**
     * Assinatura do que está na tela. Abrir um app monitorado gera vários
     * eventos de acessibilidade, e cada um pedia um `showBlock` — que removia e
     * recriava a janela, fazendo o bloqueio piscar. Se o pedido é exatamente o
     * mesmo que já está visível, não mexe em nada.
     */
    private var shownSignature: String? = null

    var current: OverlayInfo? = null
        private set

    fun showBlock(block: InterventionDecision.Block): Boolean =
        show(
            info = OverlayInfo(
                targetPackage = block.target.packageName,
                groupId = block.groupId
            ),
            signature = InterventionPresentation.blockSignature(block)
        ) {
            StrictBlockOverlay(
                block = block,
                onDismiss = { finish { onDismissBlock(block) } }
            )
        }

    fun remove() {
        remove(restoreAudioFocus = true)
    }

    private fun remove(restoreAudioFocus: Boolean) {
        val currentHost = host
        host = null
        current = null
        shownSignature = null

        if (currentHost != null) {
            // removeViewImmediate é mais confiável quando a janela foi
            // reparentada pelo sistema (acontece em algumas ROMs ao abrir a
            // aba de notificações).
            val removed = runCatching {
                windowManager.removeViewImmediate(currentHost.view)
            }.recoverCatching {
                windowManager.removeView(currentHost.view)
            }.isSuccess

            runCatching {
                if (!removed) {
                    (currentHost.view.parent as? ViewGroup)?.removeView(currentHost.view)
                }
            }
            runCatching { currentHost.view.disposeComposition() }
            currentHost.owner.destroy()
        }

        if (restoreAudioFocus) restoreBackgroundAudio()
    }

    @Suppress("DEPRECATION")
    private fun show(
        info: OverlayInfo,
        signature: String? = null,
        content: @androidx.compose.runtime.Composable () -> Unit
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) return false

        // Mesmo bloqueio, mesma janela: não remove nem recria.
        if (signature != null &&
            signature == shownSignature &&
            host?.view?.isAttachedToWindow == true
        ) {
            return true
        }

        // Troca de overlay sem devolver o áudio entre um e outro.
        remove(restoreAudioFocus = false)
        muteBackgroundAudio()

        val owner = OverlayLifecycleOwner()
        val view = ComposeView(context).apply {
            // A View nativa já nasce preta. O Compose pode levar alguns frames
            // para compor; até lá o SurfaceFlinger mostra esta camada opaca.
            setBackgroundColor(Color.rgb(11, 12, 14))
        }

        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        owner.start()

        view.setContent {
            SendaTheme {
                content()
            }
        }

        // Algumas ROMs (One UI, por exemplo) descartam a janela de sobreposição
        // quando a aba de notificações abre. Sem isso o controlador continuaria
        // achando que existe um overlay na tela.
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                if (host?.view === v) {
                    host = null
                    current = null
                    shownSignature = null
                    restoreBackgroundAudio()
                }
            }
        })

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
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        return runCatching {
            windowManager.addView(view, params)
            host = OverlayHost(owner = owner, view = view)
            current = info
            shownSignature = signature
            true
        }.getOrElse {
            restoreBackgroundAudio()
            owner.destroy()
            runCatching { view.disposeComposition() }
            false
        }
    }

    private fun muteBackgroundAudio() {
        val manager = audioManager ?: return
        if (audioFocusRequest != null) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        )
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .setWillPauseWhenDucked(true)
            .build()

        val result = runCatching { manager.requestAudioFocus(request) }.getOrNull()
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun restoreBackgroundAudio() {
        val request = audioFocusRequest ?: return
        audioFocusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }

    private fun finish(action: () -> Unit) {
        mainHandler.post {
            remove()
            action()
        }
    }

    data class OverlayInfo(
        val targetPackage: String,
        val groupId: String
    )

    private data class OverlayHost(
        val owner: OverlayLifecycleOwner,
        val view: ComposeView
    )
}
