package com.senda.app.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.service.AppMonitor
import com.senda.app.service.ChallengeIntervention
import com.senda.app.service.ChallengeRequestStore
import com.senda.app.service.FocoForegroundService
import com.senda.app.ui.theme.SendaTheme

class ChallengeActivity : ComponentActivity() {
    private var requestId: String? by mutableStateOf(null)
    private var audioFocus: AudioFocusRequest? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableTransitions()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(11, 12, 14))
        )
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        if (currentRequest() == null) {
            leaveForHome()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ChallengeIntervention.store.request.collect { request ->
                    when {
                        request == null -> leaveForHome()
                        request.id != requestId -> Unit // onNewIntent adopts the replacement.
                        request.status == ChallengeRequestStore.Status.ACCEPTED -> openTarget()
                    }
                }
            }
        }
        setContent {
            val request by ChallengeIntervention.store.request.collectAsStateWithLifecycle()
            SendaTheme {
                BackHandler { abort() }
                request?.let { pending ->
                    key(pending.id) {
                        Box(Modifier.fillMaxSize().imePadding()) {
                            ChallengeOverlay(
                                challenge = pending.challenge,
                                onAccept = { reason ->
                                    if (ChallengeIntervention.store.submit(pending.id)) {
                                        runCatching {
                                            FocoForegroundService.completeChallenge(this@ChallengeActivity, pending.id, reason)
                                        }.onFailure { abort() }
                                    }
                                },
                                onAbort = ::abort,
                                onAlternative = ::openAlternative
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        leaving = false
        if (currentRequest() == null) leaveForHome()
    }

    override fun onStart() {
        super.onStart()
        visibleRequestId = requestId
    }

    override fun onResume() {
        super.onResume()
        // A native challenge is foreground; the target must not accrue screen time.
        AppMonitor.onForegroundPackage(packageName)
        if (currentRequest() == null && !leaving) leaveForHome()
        if (!leaving && audioFocus == null) {
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }
                .build()
            if (runCatching { getSystemService(AudioManager::class.java).requestAudioFocus(focus) }
                    .getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) audioFocus = focus
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ownsInputFocus = hasFocus
    }

    override fun onStop() {
        super.onStop()
        ownsInputFocus = false
        audioFocus?.let { focus ->
            runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(focus) }
        }
        audioFocus = null
        if (visibleRequestId == requestId) visibleRequestId = null
        // A transition or Recents can stop this Activity without cancelling the challenge.
        // Keep its task and Compose state; only explicit actions finish it.
    }

    private fun currentRequest() = ChallengeIntervention.store.request.value
        ?.takeIf { it.id == requestId }

    private fun discardRequest() {
        val removed = ChallengeIntervention.store.clear(requestId) ?: return
        if (removed.status != ChallengeRequestStore.Status.ACCEPTED) {
            runCatching { FocoForegroundService.recordChallengeAborted(this, removed.challenge.usageKey) }
        }
    }

    private fun abort() {
        if (leaving) return
        discardRequest()
        leaveForHome()
    }

    private fun leaveForHome() {
        if (leaving) return
        leaving = true
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
        finishAndRemoveTask()
    }

    private fun openTarget() {
        val pending = currentRequest() ?: return
        if (leaving || pending.status != ChallengeRequestStore.Status.ACCEPTED) return
        launchPackage(pending.challenge.target.packageName)
    }

    private fun openAlternative() {
        if (leaving) return
        val target = currentRequest()?.challenge?.alternativeAppPackage
        discardRequest()
        launchPackage(target)
    }

    private fun launchPackage(target: String?) {
        val launch = target?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            ChallengeIntervention.store.clear(requestId)
            leaveForHome()
            return
        }
        leaving = true
        ChallengeIntervention.store.clear(requestId)
        runCatching {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }.onFailure {
            leaving = false
            leaveForHome()
        }
        finishAndRemoveTask()
    }

    @Suppress("DEPRECATION")
    private fun disableTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        var ownsInputFocus: Boolean = false
            private set
        private var visibleRequestId: String? = null
        private var lastLaunchId: String? = null
        private var lastLaunchAt = 0L
        private const val EXTRA_REQUEST_ID = "challenge_request_id"

        /** Tempo para a Activity abrir sem que o mesmo evento peça outra. */
        private const val LAUNCH_GRACE_MS = 1_500L

        /** Called directly from accessibility; no coroutine or service collection in this path. */
        fun show(context: Context, challenge: InterventionDecision.Challenge): Boolean {
            val previous = ChallengeIntervention.store.request.value
            val request = ChallengeIntervention.store.begin(challenge)
            val now = android.os.SystemClock.elapsedRealtime()

            // Já está na tela: repetir o evento não pode recriar a Activity.
            if (visibleRequestId == request.id) return true

            // O startActivity anterior ainda está subindo. No S24 o primeiro
            // frame do Compose pode passar de 1 s no processo frio; com a janela
            // curta (350 ms) o mesmo evento pedia outra abertura e a tela
            // piscava (sumia e voltava).
            if (previous?.id == request.id &&
                lastLaunchId == request.id &&
                now - lastLaunchAt < LAUNCH_GRACE_MS
            ) {
                return true
            }

            lastLaunchId = request.id
            lastLaunchAt = now
            com.senda.app.core.DiagnosticsLog.add(
                "desafio",
                "abrindo ${challenge.target.packageName} ${challenge.interventionType.name}"
            )
            return runCatching {
                context.startActivity(Intent(context, ChallengeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(EXTRA_REQUEST_ID, request.id))
                true
            }.getOrElse {
                com.senda.app.core.DiagnosticsLog.add("desafio", "startActivity falhou")
                ChallengeIntervention.store.clear(request.id)
                lastLaunchId = null
                lastLaunchAt = 0L
                false
            }
        }
    }
}
