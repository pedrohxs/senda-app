package com.senda.app.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.senda.app.R
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.ui.components.SendaGroupedCard
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.components.SendaRow
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite

@Composable
internal fun OnboardingPermissionsScreen(
    state: SendaPermissionState, onAccessibility: () -> Unit, onOverlay: () -> Unit,
    onNotifications: () -> Unit, onDeviceAdmin: () -> Unit, onBatteryOptimization: () -> Unit,
    onAppDetails: () -> Unit, onContinue: () -> Unit, onBack: () -> Unit
) {
    var showOptional by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = DarkCanvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.setup_back), color = TextMutedGrey) }
                Text(stringResource(R.string.setup_step, 2), style = MaterialTheme.typography.labelMedium, color = TextMutedGrey)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.setup_permissions_title), style = MaterialTheme.typography.headlineLarge, color = TextWarmWhite)
                Text(stringResource(R.string.setup_permissions_description), style = MaterialTheme.typography.bodyMedium, color = TextMutedGrey)
                if (state.essentialGranted) ReadyBanner()
                SendaGroupedCard {
                    PermissionRow(Icons.Outlined.Accessibility, stringResource(R.string.onboarding_accessibility_title),
                        stringResource(R.string.onboarding_accessibility_description), state.accessibilityEnabled, onClick = onAccessibility)
                    PermissionRow(Icons.Outlined.Layers, stringResource(R.string.onboarding_overlay_title),
                        stringResource(R.string.onboarding_overlay_description), state.overlayEnabled, showDivider = true, onClick = onOverlay)
                    PermissionRow(Icons.Outlined.NotificationsNone, stringResource(R.string.onboarding_notifications_title),
                        stringResource(R.string.onboarding_notifications_description), state.notificationsEnabled, showDivider = true, onClick = onNotifications)
                }
                TextButton(onClick = { showOptional = !showOptional }) {
                    Text(stringResource(if (showOptional) R.string.setup_hide_optional else R.string.setup_optional), color = AccentSage)
                }
                if (showOptional) SendaGroupedCard {
                    PermissionRow(Icons.Outlined.BatteryStd, stringResource(R.string.onboarding_battery_title),
                        stringResource(R.string.onboarding_battery_description), state.batteryOptimizationIgnored, optional = true, onClick = onBatteryOptimization)
                    PermissionRow(Icons.Outlined.Security, stringResource(R.string.onboarding_admin_title),
                        stringResource(R.string.onboarding_admin_description), state.deviceAdminEnabled, showDivider = true, optional = true, onClick = onDeviceAdmin)
                }
                RestrictedSettingsHelp(onAppDetails)
                Text(stringResource(R.string.onboarding_footer), style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                SendaPrimaryButton(stringResource(R.string.setup_review), onContinue, enabled = state.essentialGranted)
            }
        }
    }
}
@Composable
private fun RestrictedSettingsHelp(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, BorderMuted)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCanvas),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = AccentSage,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_restricted_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextWarmWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.onboarding_restricted_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }
            Text(
                text = stringResource(R.string.settings_open_settings),
                style = MaterialTheme.typography.labelSmall,
                color = AccentSage
            )
        }
    }
}

@Composable
private fun ReadyBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AccentSage.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AccentSage.copy(alpha = 0.34f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AccentSage)
            )
            Text(
                text = stringResource(R.string.onboarding_all_ready),
                style = MaterialTheme.typography.labelLarge,
                color = AccentSage
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    showDivider: Boolean = false,
    optional: Boolean = false,
    onClick: () -> Unit
) {
    SendaRow(
        icon = icon,
        title = title,
        subtitle = if (optional && !enabled) {
            "$description • ${stringResource(R.string.onboarding_optional)}"
        } else {
            description
        },
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (enabled) AccentSage else TextMutedGrey)
                )
                Text(
                    text = stringResource(
                        if (enabled) {
                            R.string.onboarding_status_enabled
                        } else {
                            R.string.onboarding_status_pending
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) AccentSage else TextMutedGrey
                )
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF111312)
@Composable
private fun OnboardingPreview() {
    SendaTheme {
        OnboardingScreen(
            state = SendaPermissionState(
                accessibilityEnabled = true,
                overlayEnabled = true,
                notificationsEnabled = false,
                deviceAdminEnabled = false,
                batteryOptimizationIgnored = false
            ),
            onAccessibility = {},
            onOverlay = {},
            onNotifications = {},
            onDeviceAdmin = {},
            onBatteryOptimization = {},
            onAppDetails = {},
            onContinue = {}
        )
    }
}
