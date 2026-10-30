package com.senda.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.di.AppContainer
import com.senda.app.domain.lifecycle.ProtectionHealth
import com.senda.app.domain.lifecycle.ProtectionStatus
import com.senda.app.service.FocoForegroundService
import com.senda.app.ui.theme.*

@Composable
fun ProtectionStatusCard(health: ProtectionHealth, onAction: () -> Unit, showSettingsAction: Boolean = true) {
    val status = health.status
    val tint = when (status) {
        ProtectionStatus.ACTIVE -> AccentSage
        ProtectionStatus.CHECKING -> TextMutedGrey
        else -> ChartWarm
    }
    val title = when (status) {
        ProtectionStatus.CHECKING -> R.string.protection_checking
        ProtectionStatus.ACTIVE -> R.string.protection_active
        ProtectionStatus.NOTIFICATIONS_DISABLED, ProtectionStatus.BATTERY_RESTRICTED -> R.string.protection_attention
        else -> R.string.protection_interrupted
    }
    val description = when (status) {
        ProtectionStatus.CHECKING -> R.string.protection_checking_description
        ProtectionStatus.ACTIVE -> R.string.protection_active_description
        ProtectionStatus.ACCESSIBILITY_DISABLED -> R.string.protection_accessibility_disabled
        ProtectionStatus.ACCESSIBILITY_DISCONNECTED -> R.string.protection_accessibility_disconnected
        ProtectionStatus.OVERLAY_MISSING -> R.string.protection_overlay_missing
        ProtectionStatus.SERVICE_STOPPED -> R.string.protection_service_stopped
        ProtectionStatus.SERVICE_UNRESPONSIVE -> R.string.protection_service_unresponsive
        ProtectionStatus.NOTIFICATIONS_DISABLED -> R.string.protection_notifications_disabled
        ProtectionStatus.BATTERY_RESTRICTED -> R.string.protection_battery_restricted
    }
    val action = when (status) {
        ProtectionStatus.ACTIVE -> if (showSettingsAction) R.string.protection_view_settings else null
        ProtectionStatus.CHECKING -> null
        ProtectionStatus.ACCESSIBILITY_DISABLED, ProtectionStatus.ACCESSIBILITY_DISCONNECTED -> R.string.protection_fix_accessibility
        ProtectionStatus.OVERLAY_MISSING -> R.string.protection_fix_overlay
        ProtectionStatus.SERVICE_STOPPED, ProtectionStatus.SERVICE_UNRESPONSIVE -> R.string.protection_retry_service
        ProtectionStatus.NOTIFICATIONS_DISABLED -> R.string.protection_fix_notifications
        ProtectionStatus.BATTERY_RESTRICTED -> R.string.protection_fix_battery
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = CardSurface, border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = tint)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            if (action != null) {
                TextButton(onClick = onAction) { Text(stringResource(action), color = tint) }
            }
        }
    }
}

/** The same diagnosis opens the same repair action from Home and Settings. */
@Composable
fun protectionRepairAction(container: AppContainer, health: ProtectionHealth, onOpenSettings: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.protection_action_unavailable)
    return {
        runCatching {
            val intent = when (health.status) {
                ProtectionStatus.ACCESSIBILITY_DISABLED, ProtectionStatus.ACCESSIBILITY_DISCONNECTED -> container.permissions.accessibilityIntent()
                ProtectionStatus.OVERLAY_MISSING -> container.permissions.overlayIntent()
                ProtectionStatus.NOTIFICATIONS_DISABLED -> container.permissions.notificationIntent()
                ProtectionStatus.BATTERY_RESTRICTED -> container.permissions.batteryOptimizationIntent()
                else -> null
            }
            when {
                intent != null -> context.startActivity(intent)
                health.status == ProtectionStatus.SERVICE_STOPPED || health.status == ProtectionStatus.SERVICE_UNRESPONSIVE -> {
                    FocoForegroundService.ensureRunning(context)
                    container.protectionHealth.refresh()
                }
                else -> onOpenSettings()
            }
        }.onFailure { Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show() }
    }
}
