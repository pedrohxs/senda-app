package com.senda.app.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.senda.app.R
import com.senda.app.service.ActiveFocoSession
import com.senda.app.service.FocoSessionState
import com.senda.app.ui.components.AppIcon
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import java.util.Locale
import kotlin.math.roundToInt

class FocoBubbleController(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var host: BubbleHost? = null
    private var params: WindowManager.LayoutParams? = null

    fun show() {
        // Idempotente de propósito: recriar a janela enquanto a anterior ainda
        // existe fazia a bolha se multiplicar na tela.
        if (host != null) return
        if (!Settings.canDrawOverlays(context)) return

        val owner = BubbleLifecycleOwner()
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                SendaTheme {
                    BubbleContent(
                        onMove = ::moveBy,
                        onDragFinished = ::persistPosition,
                        onExpandedChange = ::ensureExpandedVisible
                    )
                }
            }
        }
        owner.start()

        val metrics = context.resources.displayMetrics
        val defaultX = (metrics.widthPixels - dp(76)).coerceAtLeast(dp(16))
        val defaultY = (metrics.heightPixels * 0.38f).roundToInt()
        val savedX = preferences.getInt(KEY_X, defaultX)
        val savedY = preferences.getInt(KEY_Y, defaultY)
        val margin = dp(8)
        val maxX = (metrics.widthPixels - dp(76) - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - dp(76) - margin).coerceAtLeast(margin)
        val x = savedX.coerceIn(margin, maxX)
        val y = savedY.coerceIn(margin, maxY)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        runCatching {
            windowManager.addView(view, layoutParams)
            host = BubbleHost(owner = owner, view = view)
            params = layoutParams
        }.onFailure {
            owner.destroy()
        }
    }

    fun remove() {
        val current = host ?: return

        val removed = runCatching { windowManager.removeViewImmediate(current.view) }
            .recoverCatching { windowManager.removeView(current.view) }
            .isSuccess
        runCatching {
            if (!removed) {
                (current.view.parent as? ViewGroup)?.removeView(current.view)
            }
        }

        // Só descarta a referência quando a janela realmente saiu: se continuar
        // presa, o próximo remove() tenta de novo em vez de deixar órfã na tela.
        if (removed || !current.view.isAttachedToWindow) {
            host = null
            params = null
            runCatching { current.view.disposeComposition() }
            current.owner.destroy()
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val currentParams = params ?: return
        val metrics = context.resources.displayMetrics
        val margin = dp(8)
        val maxX = (metrics.widthPixels - dp(44) - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - dp(44) - margin).coerceAtLeast(margin)

        currentParams.x = (currentParams.x + dx.roundToInt()).coerceIn(margin, maxX)
        currentParams.y = (currentParams.y + dy.roundToInt()).coerceIn(margin, maxY)
        host?.view?.let { view ->
            runCatching { windowManager.updateViewLayout(view, currentParams) }
        }
    }

    private fun persistPosition() {
        val currentParams = params ?: return
        preferences.edit()
            .putInt(KEY_X, currentParams.x)
            .putInt(KEY_Y, currentParams.y)
            .apply()
    }

    private fun ensureExpandedVisible(expanded: Boolean) {
        if (!expanded) return
        val currentParams = params ?: return
        val metrics = context.resources.displayMetrics
        val margin = dp(8)
        val expandedWidth = dp(172)
        val maxX = (metrics.widthPixels - expandedWidth - margin).coerceAtLeast(margin)
        if (currentParams.x > maxX) {
            currentParams.x = maxX
            host?.view?.let { view ->
                runCatching { windowManager.updateViewLayout(view, currentParams) }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private data class BubbleHost(
        val owner: BubbleLifecycleOwner,
        val view: ComposeView
    )

    private companion object {
        const val PREFERENCES_NAME = "senda_bubble"
        const val KEY_X = "bubble_x"
        const val KEY_Y = "bubble_y"
    }
}

@Composable
private fun BubbleContent(
    onMove: (Float, Float) -> Unit,
    onDragFinished: () -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    val session by FocoSessionState.session.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var dragged by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(3_000L)
            expanded = false
        }
    }

    val activeSession = session
    if (activeSession == null) return

    val progress = if (activeSession.durationSeconds > 0) {
        activeSession.remainingSeconds.toFloat() / activeSession.durationSeconds
    } else {
        0f
    }.coerceIn(0f, 1f)

    val modifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    if (!dragged) {
                        expanded = !expanded
                        onExpandedChange(expanded)
                    }
                    dragged = false
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragged = false },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragged = true
                    onMove(dragAmount.x, dragAmount.y)
                },
                onDragEnd = {
                    onDragFinished()
                },
                onDragCancel = {
                    onDragFinished()
                }
            )
        }

    if (expanded) {
        BubbleExpanded(
            session = activeSession,
            modifier = modifier.animateContentSize()
        )
    } else {
        BubbleCollapsed(
            progress = progress,
            modifier = modifier
        )
    }
}

@Composable
private fun BubbleCollapsed(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(CardSurface),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(42.dp)) {
            val strokeWidth = 2.2.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = BorderMuted,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = AccentSage,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(DarkCanvas),
            contentAlignment = Alignment.Center
        ) {
            SendaMark(size = 21.dp)
        }
    }
}

@Composable
private fun BubbleExpanded(
    session: ActiveFocoSession,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(14.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = CardSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderMuted)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(
                packageName = session.packageName,
                size = 32.dp,
                shape = RoundedCornerShape(12.dp)
            )
            Column(modifier = Modifier.widthIn(max = 110.dp)) {
                Text(
                    text = session.displayName,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRemaining(session.remainingSeconds),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = TextWarmWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.bubble_remaining_short),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = TextMutedGrey
                )
            }
        }
    }
}

private fun formatRemaining(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private class BubbleLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
