package com.senda.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.senda.app.R
import com.senda.app.di.AppContainer
import com.senda.app.service.FocoForegroundService
import com.senda.app.service.ProtectionCheck
import com.senda.app.service.ProtectionCheckKind
import com.senda.app.service.ProtectionFix
import com.senda.app.service.ProtectionSelfTest
import com.senda.app.service.ProtectionTestReport
import com.senda.app.ui.components.SendaLoadingState
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.ChartWarm
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import com.senda.app.ui.util.rememberSendaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Testa a proteção de verdade: permissões, serviço, batimento e uma
 * sobreposição real de 3 segundos. Cada item reprovado traz o atalho que
 * resolve — e, ao voltar da configuração do Android, a lista se atualiza.
 */
@Composable
fun ProtectionTestRoute(
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val haptics = rememberSendaHaptics()
    val selfTest = remember { container.protectionSelfTest }

    var report by remember { mutableStateOf<ProtectionTestReport?>(null) }
    var running by remember { mutableStateOf(true) }
    var hasRun by remember { mutableStateOf(false) }

    suspend fun runFullTest() {
        running = true
        val overlay = selfTest.overlay(ProtectionSelfTest.OVERLAY_MILLIS)
        val checks = selfTest.checks()
        val overlayIndex = checks.indexOfFirst { it.kind == ProtectionCheckKind.OVERLAY_PERMISSION }
        val ordered = if (overlayIndex >= 0) {
            checks.toMutableList().apply { add(overlayIndex + 1, overlay) }
        } else {
            checks + overlay
        }
        report = ProtectionTestReport(
            checks = ordered,
            overlayMillis = ProtectionSelfTest.OVERLAY_MILLIS
        )
        running = false
        hasRun = true
        if (report?.allPassed == true) haptics.confirm() else haptics.reject()
    }

    suspend fun refreshChecks() {
        val previous = report ?: return
        val overlay = previous.checks.firstOrNull { it.kind == ProtectionCheckKind.OVERLAY_WINDOW }
        val checks = selfTest.checks()
        val overlayIndex = checks.indexOfFirst { it.kind == ProtectionCheckKind.OVERLAY_PERMISSION }
        val ordered = if (overlay != null && overlayIndex >= 0) {
            checks.toMutableList().apply { add(overlayIndex + 1, overlay) }
        } else {
            checks
        }
        report = previous.copy(checks = ordered)
    }

    LaunchedEffect(Unit) { runFullTest() }

    // Ao voltar de uma configuração do Android, reavalia sem repetir a sobreposição.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasRun && !running) scope.launch { refreshChecks() }
    }

    fun applyFix(check: ProtectionCheck) {
        haptics.tick()
        val opened = runCatching {
            when (check.fix) {
                ProtectionFix.ACCESSIBILITY -> context.startActivity(container.permissions.accessibilityIntent())
                ProtectionFix.OVERLAY -> context.startActivity(container.permissions.overlayIntent())
                ProtectionFix.NOTIFICATIONS -> context.startActivity(container.permissions.notificationIntent())
                ProtectionFix.BATTERY -> context.startActivity(container.permissions.batteryOptimizationIntent())
                ProtectionFix.SERVICE, ProtectionFix.RETRY -> {
                    FocoForegroundService.ensureRunning(context)
                    container.protectionHealth.refresh()
                }
            }
        }.isSuccess
        if (!opened) {
            Toast.makeText(
                context,
                resources.getString(R.string.protection_action_unavailable),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        scope.launch {
            delay(900L)
            refreshChecks()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp)
    ) {
        SettingsPageHeader(
            title = stringResource(R.string.protection_test_title),
            onBack = onBack
        )

        if (running) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SendaLoadingState(label = stringResource(R.string.protection_test_running))
            }
        } else {
            val current = report
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.protection_test_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMutedGrey
                    )
                }

                item {
                    val failures = current?.failures.orEmpty()
                    val allPassed = current?.allPassed == true
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = if (allPassed) {
                            AccentSage.copy(alpha = 0.10f)
                        } else {
                            ChartWarm.copy(alpha = 0.10f)
                        },
                        border = BorderStroke(
                            1.dp,
                            (if (allPassed) AccentSage else ChartWarm).copy(alpha = 0.40f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (allPassed) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (allPassed) AccentSage else ChartWarm,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (allPassed) {
                                    stringResource(R.string.protection_test_all_ok)
                                } else {
                                    pluralStringResource(
                                        R.plurals.protection_test_failures,
                                        failures.size,
                                        failures.size
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextWarmWhite
                            )
                        }
                    }
                }

                items(current?.checks.orEmpty(), key = { it.kind.name }) { check ->
                    ProtectionCheckRow(check = check, onFix = { applyFix(check) })
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SendaPrimaryButton(
                        text = stringResource(R.string.protection_test_again),
                        onClick = { scope.launch { runFullTest() } }
                    )
                    TextButton(
                        onClick = { scope.launch { refreshChecks() } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.protection_test_recheck),
                            color = TextMutedGrey,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionCheckRow(check: ProtectionCheck, onFix: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = when {
                    check.passed -> Icons.Rounded.CheckCircle
                    check.required -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.Info
                },
                contentDescription = null,
                tint = when {
                    check.passed -> AccentSage
                    check.required -> ChartWarm
                    else -> TextMutedGrey
                },
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(22.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(check.kind.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWarmWhite
                )
                if (!check.required) {
                    Text(
                        text = stringResource(R.string.protection_test_optional),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMutedGrey
                    )
                }
            }

            if (!check.passed) {
                TextButton(onClick = onFix) {
                    Text(
                        text = stringResource(R.string.protection_test_fix),
                        color = AccentSage,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private fun ProtectionCheckKind.labelRes(): Int = when (this) {
    ProtectionCheckKind.ACCESSIBILITY -> R.string.protection_test_check_accessibility
    ProtectionCheckKind.SERVICE -> R.string.protection_test_check_service
    ProtectionCheckKind.HEARTBEAT -> R.string.protection_test_check_heartbeat
    ProtectionCheckKind.OVERLAY_PERMISSION -> R.string.protection_test_check_overlay_permission
    ProtectionCheckKind.OVERLAY_WINDOW -> R.string.protection_test_check_overlay_window
    ProtectionCheckKind.NOTIFICATIONS -> R.string.protection_test_check_notifications
    ProtectionCheckKind.BATTERY -> R.string.protection_test_check_battery
}
