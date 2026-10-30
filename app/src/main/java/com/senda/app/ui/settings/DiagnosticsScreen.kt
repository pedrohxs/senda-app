package com.senda.app.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senda.app.R
import com.senda.app.core.DatabaseHealth
import com.senda.app.core.DiagnosticsLog
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.di.AppContainer
import com.senda.app.service.AppMonitor
import com.senda.app.ui.components.SendaGroupedCard
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val permissions = remember { container.permissions.current() }
    val databaseError by DatabaseHealth.error.collectAsStateWithLifecycle()
    var heartbeatAgeSeconds by remember { mutableStateOf<Long?>(null) }
    var events by remember { mutableStateOf(DiagnosticsLog.snapshot()) }
    var generatedAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val clockTamperSeconds = remember { container.protectionStore.clockTamperRemainingSeconds() }
    val clockGuardEnabled = remember { container.protectionStore.clockGuardEnabled }

    LaunchedEffect(Unit) {
        heartbeatAgeSeconds = runCatching {
            val last = container.heartbeatStore.last()
            if (last <= 0L) null else ((System.currentTimeMillis() - last) / 1_000L).coerceAtLeast(0L)
        }.getOrNull()
        events = DiagnosticsLog.snapshot()
        generatedAtMillis = System.currentTimeMillis()
    }

    val versionName = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName.orEmpty() to PackageInfoCompat.getLongVersionCode(info).toInt()
        }.getOrDefault("?" to 0)
    }

    val facts = DiagnosticsFacts(
        appVersion = versionName.first,
        appVersionCode = versionName.second,
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        androidSdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        protectedItems = protectionFacts(
            permissions = permissions,
            heartbeatAgeSeconds = heartbeatAgeSeconds,
            clockGuardEnabled = clockGuardEnabled,
            clockTamperSeconds = clockTamperSeconds
        ),
        databaseError = databaseError,
        generatedAtMillis = generatedAtMillis
    )
    val report = remember(facts, events) { DiagnosticsReport.format(facts, events) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(report.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                resources.getString(
                    if (written) R.string.diagnostics_export_done else R.string.diagnostics_export_failed
                ),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkCanvas) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.diagnostics_back),
                            tint = TextWarmWhite
                        )
                    }
                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextWarmWhite
                    )
                }
            }

            item {
                SendaSectionLabel(text = stringResource(R.string.diagnostics_summary))
                Spacer(modifier = Modifier.height(8.dp))
                SendaGroupedCard {
                    FactRow(
                        label = stringResource(R.string.diagnostics_label_version),
                        value = "${facts.appVersion} (${facts.appVersionCode})"
                    )
                    FactRow(
                        label = stringResource(R.string.diagnostics_label_android),
                        value = "${facts.androidRelease} (API ${facts.androidSdk})"
                    )
                    FactRow(
                        label = stringResource(R.string.diagnostics_label_device),
                        value = facts.device
                    )
                    FactRow(
                        label = stringResource(R.string.diagnostics_label_database),
                        value = databaseError ?: stringResource(R.string.diagnostics_value_ok)
                    )
                }
            }

            item {
                SendaSectionLabel(text = stringResource(R.string.diagnostics_protection))
                Spacer(modifier = Modifier.height(8.dp))
                SendaGroupedCard {
                    facts.protectedItems.forEach { (label, value) ->
                        FactRow(label = label, value = value)
                    }
                }
            }

            item {
                SendaPrimaryButton(
                    text = stringResource(R.string.diagnostics_export),
                    onClick = {
                        exportLauncher.launch(
                            "senda-diagnostico-${System.currentTimeMillis()}.txt"
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        DiagnosticsLog.clear()
                        events = DiagnosticsLog.snapshot()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.diagnostics_clear),
                        color = TextMutedGrey,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            item {
                SendaSectionLabel(text = stringResource(R.string.diagnostics_events))
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.diagnostics_events_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMutedGrey
                    )
                }
            } else {
                items(events) { event ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${DiagnosticsLog.formatTime(event.atMillis)} · ${event.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentSage
                        )
                        Text(
                            text = event.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWarmWhite,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMutedGrey,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextWarmWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun protectionFacts(
    permissions: SendaPermissionState,
    heartbeatAgeSeconds: Long?,
    clockGuardEnabled: Boolean,
    clockTamperSeconds: Long
): List<Pair<String, String>> {
    val yes = stringResource(R.string.diagnostics_value_active)
    val no = stringResource(R.string.diagnostics_value_inactive)
    val allowed = stringResource(R.string.diagnostics_value_allowed)
    val denied = stringResource(R.string.diagnostics_value_denied)
    val ignored = stringResource(R.string.diagnostics_value_ignored)
    val optimized = stringResource(R.string.diagnostics_value_optimized)

    val accessibility = when {
        !permissions.accessibilityEnabled -> no
        AppMonitor.accessibilityConnected -> stringResource(R.string.diagnostics_accessibility_connected)
        else -> stringResource(R.string.diagnostics_accessibility_disconnected)
    }

    val clock = when {
        !clockGuardEnabled -> stringResource(R.string.diagnostics_clock_disabled)
        clockTamperSeconds > 0L -> stringResource(
            R.string.diagnostics_clock_tampered,
            ((clockTamperSeconds + 59L) / 60L).toInt()
        )
        else -> stringResource(R.string.diagnostics_clock_ok)
    }

    return listOf(
        stringResource(R.string.diagnostics_accessibility) to accessibility,
        stringResource(R.string.diagnostics_service) to
            if (AppMonitor.protectionServiceRunning) yes else no,
        stringResource(R.string.diagnostics_heartbeat) to when {
            heartbeatAgeSeconds == null -> stringResource(R.string.diagnostics_heartbeat_never)
            else -> stringResource(R.string.diagnostics_heartbeat_seconds, heartbeatAgeSeconds)
        },
        stringResource(R.string.diagnostics_overlay) to
            if (permissions.overlayEnabled) allowed else denied,
        stringResource(R.string.diagnostics_notifications) to
            if (permissions.notificationsEnabled) allowed else denied,
        stringResource(R.string.diagnostics_battery) to
            if (permissions.batteryOptimizationIgnored) ignored else optimized,
        stringResource(R.string.diagnostics_admin) to
            if (permissions.deviceAdminEnabled) yes else no,
        stringResource(R.string.diagnostics_clock) to clock
    )
}
