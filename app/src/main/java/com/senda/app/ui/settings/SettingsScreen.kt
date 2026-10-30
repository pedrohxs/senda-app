package com.senda.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senda.app.R
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.data.preferences.GuardMode
import com.senda.app.di.AppContainer
import com.senda.app.domain.backup.BackupError
import com.senda.app.service.ProtecaoAdmin
import com.senda.app.ui.components.SendaGroupedCard
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaScreenState
import com.senda.app.ui.components.SendaSectionLabel
import com.senda.app.ui.components.SendaRow
import com.senda.app.ui.components.SendaStateHost
import com.senda.app.ui.security.CriticalActionGuardDialog
import com.senda.app.ui.security.GuardianPinDialog
import com.senda.app.ui.security.PinDialogMode
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.IconTint
import com.senda.app.ui.theme.SendaTheme
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import com.senda.app.ui.util.rememberSendaHaptics

private data class GuardRequest(
    val target: String,
    val title: String,
    val description: String,
    val onAuthorized: () -> Unit
)

/** Telas internas de Ajustes; todas voltam para a lista principal. */
private enum class SettingsPage { MAIN, ABOUT, DIAGNOSTICS, PROTECTION_TEST }

private val BACKUP_FILE_NAME_FORMAT =
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(java.time.ZoneId.systemDefault())

private fun defaultBackupFileName(): String =
    "senda-backup-${BACKUP_FILE_NAME_FORMAT.format(java.time.Instant.now())}.json"

private fun formatBackupTimestamp(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(millis))

private fun formatBackupDate(iso: String): String = runCatching {
    formatBackupTimestamp(java.time.Instant.parse(iso).toEpochMilli())
}.getOrElse { iso.ifBlank { "—" } }

private fun BackupError.messageRes(): Int = when (this) {
    BackupError.EMPTY -> R.string.settings_backup_error_empty
    BackupError.INVALID_JSON -> R.string.settings_backup_error_invalid
    BackupError.NOT_A_BACKUP -> R.string.settings_backup_error_not_backup
    BackupError.NEWER_VERSION -> R.string.settings_backup_error_newer
    BackupError.TOO_LARGE -> R.string.settings_backup_error_too_large
    BackupError.IO -> R.string.settings_backup_error_io
}

@Composable
fun SettingsRoute(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = rememberSendaHaptics()
    val protectionStore = container.protectionStore
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.MAIN.name) }
    val page = runCatching { SettingsPage.valueOf(pageName) }.getOrDefault(SettingsPage.MAIN)
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var guardRequest by remember { mutableStateOf<GuardRequest?>(null) }
    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    val databaseIssue by com.senda.app.core.DatabaseHealth.error.collectAsStateWithLifecycle()
    val appVersion = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName.orEmpty() to PackageInfoCompat.getLongVersionCode(info).toInt()
        }.getOrDefault("" to 0)
    }

    when (page) {
        SettingsPage.ABOUT -> {
            AboutRoute(
                versionName = appVersion.first,
                versionCode = appVersion.second,
                documents = container.appDocuments,
                onBack = { pageName = SettingsPage.MAIN.name },
                onOpenDiagnostics = { pageName = SettingsPage.DIAGNOSTICS.name },
                onOpenProtectionTest = { pageName = SettingsPage.PROTECTION_TEST.name }
            )
            return
        }

        SettingsPage.DIAGNOSTICS -> {
            DiagnosticsRoute(
                container = container,
                onBack = { pageName = SettingsPage.ABOUT.name }
            )
            return
        }

        SettingsPage.PROTECTION_TEST -> {
            ProtectionTestRoute(
                container = container,
                onBack = { pageName = SettingsPage.ABOUT.name }
            )
            return
        }

        SettingsPage.MAIN -> Unit
    }
    val backupPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val backupFeedback by viewModel.feedback.collectAsStateWithLifecycle()
    var showPinRestorePrompt by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.inspectBackup(uri)
    }

    LaunchedEffect(backupFeedback) {
        val feedback = backupFeedback ?: return@LaunchedEffect
        val message = when (feedback) {
            BackupFeedback.Exported ->
                resources.getString(R.string.settings_backup_export_done)
            BackupFeedback.ExportFailed ->
                resources.getString(R.string.settings_backup_export_failed)
            BackupFeedback.RestoreFailed ->
                resources.getString(R.string.settings_backup_restore_failed)
            is BackupFeedback.Restored ->
                resources.getString(
                    R.string.settings_backup_restored,
                    resources.getQuantityString(
                        R.plurals.settings_backup_groups,
                        feedback.summary.groups,
                        feedback.summary.groups
                    ),
                    resources.getQuantityString(
                        R.plurals.settings_backup_days,
                        feedback.summary.usageDays,
                        feedback.summary.usageDays
                    )
                )
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        if (feedback is BackupFeedback.Restored && feedback.summary.pinWasConfigured) {
            showPinRestorePrompt = true
        }
        viewModel.consumeFeedback()
    }

    val clockOffTitle = stringResource(R.string.guard_action_clock_title)
    val clockOffMessage = stringResource(R.string.guard_action_clock_message)
    val delayOffTitle = stringResource(R.string.guard_action_delay_title)
    val delayOffMessage = stringResource(R.string.guard_action_delay_message)
    val adminOffTitle = stringResource(R.string.guard_action_admin_title)
    val adminOffMessage = stringResource(R.string.guard_action_admin_message)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    fun requestGuarded(
        target: String,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        if (protectionStore.guardMode() == GuardMode.NONE) {
            action()
        } else {
            guardRequest = GuardRequest(target, title, description, action)
        }
    }

    SettingsScreen(
        state = state,
        onRepairProtection = com.senda.app.ui.components.protectionRepairAction(container, state.protection) { viewModel.refresh() },
        versionName = appVersion.first,
        versionCode = appVersion.second,
        onRetryLoad = viewModel::retry,
        onOpenAbout = { pageName = SettingsPage.ABOUT.name },
        onAccessibility = {
            context.startActivity(container.permissions.accessibilityIntent())
        },
        onOverlay = {
            context.startActivity(container.permissions.overlayIntent())
        },
        onNotifications = {
            context.startActivity(container.permissions.notificationIntent())
        },
        onActivateDeviceAdmin = {
            val intent = container.permissions.deviceAdminIntent()
            val opened = runCatching {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(container.permissions.deviceAdminSettingsIntent())
                }
            }.recoverCatching {
                context.startActivity(container.permissions.deviceAdminSettingsIntent())
            }.isSuccess
            if (!opened) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.device_admin_unavailable),
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        onRequestDeactivateDeviceAdmin = {
            requestGuarded(
                target = "device_admin",
                title = adminOffTitle,
                description = adminOffMessage
            ) {
                showDeactivateDialog = true
            }
        },
        onBatteryOptimization = {
            context.startActivity(container.permissions.batteryOptimizationIntent())
        },
        onClockGuardChange = { enabled ->
            if (enabled) {
                viewModel.setClockGuardEnabled(true)
            } else {
                requestGuarded(
                    target = "settings:clock_guard",
                    title = clockOffTitle,
                    description = clockOffMessage
                ) {
                    viewModel.setClockGuardEnabled(false)
                }
            }
        },
        onEditDelayChange = { enabled ->
            if (enabled) {
                viewModel.setEditDelayEnabled(true)
            } else {
                requestGuarded(
                    target = "settings:edit_delay",
                    title = delayOffTitle,
                    description = delayOffMessage
                ) {
                    viewModel.setEditDelayEnabled(false)
                }
            }
        },
        onSetGuardianPin = { pinDialogMode = PinDialogMode.SET },
        onChangeGuardianPin = { pinDialogMode = PinDialogMode.CHANGE },
        onRemoveGuardianPin = { pinDialogMode = PinDialogMode.REMOVE },
        onRecoverGuardianPin = { showRecoveryDialog = true },
        onAppDetails = {
            context.startActivity(container.permissions.appDetailsIntent())
        },
        lastBackupAt = state.lastBackupAt,
        onExportBackup = { exportBackupLauncher.launch(defaultBackupFileName()) },
        onImportBackup = {
            importBackupLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream")
            )
        },
        databaseIssue = databaseIssue != null,
        onOpenDiagnostics = { pageName = SettingsPage.DIAGNOSTICS.name }
    )

    guardRequest?.let { request ->
        CriticalActionGuardDialog(
            protectionStore = protectionStore,
            target = request.target,
            title = request.title,
            description = request.description,
            onDismiss = { guardRequest = null },
            onAuthorized = {
                guardRequest = null
                request.onAuthorized()
            }
        )
    }

    pinDialogMode?.let { mode ->
        GuardianPinDialog(
            mode = mode,
            protectionStore = protectionStore,
            onDismiss = { pinDialogMode = null },
            onChanged = {
                pinDialogMode = null
                viewModel.refresh()
            }
        )
    }

    if (showRecoveryDialog) {
        com.senda.app.ui.security.PinRecoveryDialog(
            protectionStore = protectionStore,
            onDismiss = { showRecoveryDialog = false },
            onRecovered = {
                showRecoveryDialog = false
                viewModel.refresh()
            }
        )
    }

    when (val preview = backupPreview) {
        is BackupImportPreview.Ready -> {
            val summary = preview.file.summary
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                containerColor = CardSurface,
                titleContentColor = TextWarmWhite,
                textContentColor = TextMutedGrey,
                title = {
                    Text(stringResource(R.string.settings_backup_restore_dialog_title))
                },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.settings_backup_restore_from,
                                formatBackupDate(summary.exportedAt)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = listOf(
                                pluralStringResource(
                                    R.plurals.settings_backup_groups,
                                    summary.groups,
                                    summary.groups
                                ),
                                pluralStringResource(
                                    R.plurals.settings_backup_targets,
                                    summary.targets,
                                    summary.targets
                                ),
                                pluralStringResource(
                                    R.plurals.settings_backup_days,
                                    summary.usageDays,
                                    summary.usageDays
                                )
                            ).joinToString(" · ")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_backup_restore_warning))
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmImport) {
                        Text(
                            text = stringResource(R.string.settings_backup_restore_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelImport) {
                        Text(
                            text = stringResource(R.string.settings_cancel),
                            color = TextMutedGrey
                        )
                    }
                }
            )
        }

        is BackupImportPreview.Failed -> {
            AlertDialog(
                onDismissRequest = viewModel::cancelImport,
                containerColor = CardSurface,
                titleContentColor = TextWarmWhite,
                textContentColor = TextMutedGrey,
                title = {
                    Text(stringResource(R.string.settings_backup_import_title))
                },
                text = {
                    Text(stringResource(preview.error.messageRes()))
                },
                confirmButton = {
                    TextButton(onClick = viewModel::cancelImport) {
                        Text(
                            text = stringResource(R.string.settings_backup_error_dismiss),
                            color = AccentSage
                        )
                    }
                }
            )
        }

        null -> Unit
    }

    if (showPinRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showPinRestorePrompt = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = {
                Text(stringResource(R.string.settings_backup_pin_title))
            },
            text = {
                Text(stringResource(R.string.settings_backup_pin_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPinRestorePrompt = false
                        pinDialogMode = PinDialogMode.SET
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_pin_now),
                        color = AccentSage
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinRestorePrompt = false }) {
                    Text(
                        text = stringResource(R.string.settings_backup_pin_later),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            containerColor = CardSurface,
            titleContentColor = TextWarmWhite,
            textContentColor = TextMutedGrey,
            title = {
                Text(stringResource(R.string.settings_deactivate_admin_title))
            },
            text = {
                Text(stringResource(R.string.settings_deactivate_admin_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.reject()
                        runCatching { ProtecaoAdmin.deactivate(context) }
                        viewModel.refresh()
                        showDeactivateDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_deactivate_admin_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text(
                        text = stringResource(R.string.settings_cancel),
                        color = TextMutedGrey
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    versionName: String,
    onAccessibility: () -> Unit,
    onOverlay: () -> Unit,
    onNotifications: () -> Unit,
    onActivateDeviceAdmin: () -> Unit,
    onRequestDeactivateDeviceAdmin: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onClockGuardChange: (Boolean) -> Unit,
    onEditDelayChange: (Boolean) -> Unit,
    onSetGuardianPin: () -> Unit,
    onChangeGuardianPin: () -> Unit,
    onRemoveGuardianPin: () -> Unit,
    onRecoverGuardianPin: () -> Unit,
    onAppDetails: () -> Unit,
    onRepairProtection: () -> Unit = {},
    lastBackupAt: Long? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    databaseIssue: Boolean = false,
    onOpenDiagnostics: () -> Unit = {},
    versionCode: Int = 0,
    onRetryLoad: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val permissions = state.permissions
    val batteryOptimizationIgnored = permissions.batteryOptimizationIgnored
    val clockTamperMinutes = ((state.clockTamperRemainingSeconds + 59L) / 60L).toInt()
    val screenState = when {
        state.isLoading -> SendaScreenState.loading()
        state.loadFailed -> SendaScreenState.error(
            messageRes = R.string.settings_load_error,
            onRetry = onRetryLoad
        )
        else -> SendaScreenState.content()
    }

    SendaStateHost(
        state = screenState,
        modifier = Modifier.fillMaxSize(),
        minHeight = 280.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWarmWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMutedGrey
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CardSurface),
                contentAlignment = Alignment.Center
            ) {
                SendaMark(size = 28.dp)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        com.senda.app.ui.components.ProtectionStatusCard(state.protection, onRepairProtection, showSettingsAction = false)

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.settings_section_blindado))
        Spacer(modifier = Modifier.height(8.dp))

        SendaGroupedCard {
            SendaRow(
                icon = Icons.Outlined.Security,
                title = stringResource(R.string.settings_device_admin_title),
                subtitle = stringResource(R.string.settings_device_admin_description),
                trailing = {
                    Switch(
                        checked = permissions.deviceAdminEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                onActivateDeviceAdmin()
                            } else {
                                onRequestDeactivateDeviceAdmin()
                            }
                        }
                    )
                },
                showChevron = false
            )
            SendaRow(
                icon = Icons.Outlined.AccessTime,
                title = stringResource(R.string.settings_clock_guard_title),
                subtitle = stringResource(R.string.settings_clock_guard_description),
                showDivider = true,
                trailing = {
                    Switch(
                        checked = state.clockGuardEnabled,
                        onCheckedChange = onClockGuardChange
                    )
                },
                showChevron = false
            )
            SendaRow(
                icon = Icons.Outlined.Timer,
                title = stringResource(R.string.settings_edit_delay_title),
                subtitle = stringResource(R.string.settings_edit_delay_description),
                showDivider = true,
                trailing = {
                    Switch(
                        checked = state.editDelayEnabled,
                        onCheckedChange = onEditDelayChange
                    )
                },
                showChevron = false
            )
            SendaRow(
                icon = Icons.Outlined.Key,
                title = stringResource(R.string.settings_guardian_pin_title),
                subtitle = stringResource(R.string.settings_guardian_pin_description),
                trailingText = stringResource(
                    if (state.guardianPinSet) {
                        R.string.settings_guardian_pin_defined
                    } else {
                        R.string.settings_guardian_pin_not_defined
                    }
                ),
                showDivider = true,
                onClick = {
                    if (state.guardianPinSet) {
                        onChangeGuardianPin()
                    } else {
                        onSetGuardianPin()
                    }
                }
            )
        }

        if (state.guardianPinSet) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onChangeGuardianPin) {
                    Text(
                        text = stringResource(R.string.settings_guardian_pin_change),
                        color = AccentSage,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                TextButton(onClick = onRemoveGuardianPin) {
                    Text(
                        text = stringResource(R.string.settings_guardian_pin_remove),
                        color = TextMutedGrey,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                TextButton(onClick = onRecoverGuardianPin) {
                    Text(
                        text = stringResource(R.string.pin_recovery_action),
                        color = TextMutedGrey,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (clockTamperMinutes > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            TamperWarning(minutes = clockTamperMinutes)
        }

        if (databaseIssue) {
            Spacer(modifier = Modifier.height(12.dp))
            DatabaseWarning(onClick = onOpenDiagnostics)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.settings_section_permissions))
        Spacer(modifier = Modifier.height(8.dp))

        SendaGroupedCard {
            PermissionRow(
                icon = Icons.Outlined.Accessibility,
                title = stringResource(R.string.onboarding_accessibility_title),
                subtitle = stringResource(R.string.onboarding_accessibility_description),
                enabled = permissions.accessibilityEnabled,
                onClick = onAccessibility
            )
            PermissionRow(
                icon = Icons.Outlined.Layers,
                title = stringResource(R.string.onboarding_overlay_title),
                subtitle = stringResource(R.string.onboarding_overlay_description),
                enabled = permissions.overlayEnabled,
                showDivider = true,
                onClick = onOverlay
            )
            PermissionRow(
                icon = Icons.Outlined.NotificationsNone,
                title = stringResource(R.string.onboarding_notifications_title),
                subtitle = stringResource(R.string.onboarding_notifications_description),
                enabled = permissions.notificationsEnabled,
                showDivider = true,
                onClick = onNotifications
            )
            SendaRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = stringResource(R.string.settings_restricted_help_title),
                subtitle = stringResource(R.string.settings_restricted_help_description),
                showDivider = true,
                onClick = onAppDetails
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.settings_section_backup))
        Spacer(modifier = Modifier.height(8.dp))

        SendaGroupedCard {
            SendaRow(
                icon = Icons.Outlined.Backup,
                title = stringResource(R.string.settings_backup_export_title),
                subtitle = stringResource(R.string.settings_backup_export_description),
                onClick = onExportBackup
            )
            SendaRow(
                icon = Icons.Outlined.Restore,
                title = stringResource(R.string.settings_backup_import_title),
                subtitle = stringResource(R.string.settings_backup_import_description),
                showDivider = true,
                onClick = onImportBackup
            )
            SendaRow(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.settings_backup_last_title),
                trailingText = lastBackupAt?.let { formatBackupTimestamp(it) }
                    ?: stringResource(R.string.settings_backup_never),
                showDivider = true,
                showChevron = false
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_backup_pin_note),
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedGrey,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.settings_section_system))
        Spacer(modifier = Modifier.height(8.dp))

        SendaGroupedCard {
            SendaRow(
                icon = Icons.Outlined.BatteryStd,
                title = stringResource(R.string.settings_battery_title),
                subtitle = stringResource(R.string.settings_battery_description),
                trailingText = stringResource(
                    if (batteryOptimizationIgnored) {
                        R.string.settings_battery_status_ignored
                    } else {
                        R.string.settings_battery_status_optimized
                    }
                ),
                onClick = onBatteryOptimization
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SendaSectionLabel(text = stringResource(R.string.settings_section_about))
        Spacer(modifier = Modifier.height(8.dp))

        SendaGroupedCard {
            SendaRow(
                icon = Icons.Outlined.Info,
                title = stringResource(R.string.settings_about_row_title),
                subtitle = if (versionName.isBlank()) {
                    stringResource(R.string.settings_about_row_subtitle)
                } else {
                    stringResource(R.string.about_version, versionName, versionCode)
                },
                onClick = onOpenAbout
            )
            SendaRow(
                icon = Icons.Outlined.BugReport,
                title = stringResource(R.string.settings_diagnostics_title),
                subtitle = stringResource(R.string.settings_diagnostics_description),
                showDivider = true,
                onClick = onOpenDiagnostics
            )
        }

        Spacer(modifier = Modifier.height(104.dp))
    }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    showDivider: Boolean = false,
    onClick: () -> Unit
) {
    SendaRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        showDivider = showDivider,
        iconTint = if (enabled) IconTint else TextMutedGrey,
        onClick = onClick,
        showChevron = true,
        trailing = { PermissionStatus(enabled = enabled) }
    )
}

@Composable
private fun PermissionStatus(enabled: Boolean) {
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

@Composable
private fun TamperWarning(minutes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Text(
            text = stringResource(R.string.settings_clock_tamper_warning, minutes),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun DatabaseWarning(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.settings_database_warning_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_database_warning_message),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedGrey
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF111312)
@Composable
private fun SettingsScreenPreview() {
    SendaTheme {
        SettingsScreen(
            state = SettingsUiState(
                permissions = SendaPermissionState(
                    accessibilityEnabled = true,
                    overlayEnabled = true,
                    notificationsEnabled = true,
                    deviceAdminEnabled = true,
                    batteryOptimizationIgnored = true
                ),
                clockGuardEnabled = true,
                editDelayEnabled = true,
                guardianPinSet = false,
                clockTamperRemainingSeconds = 0L
            ),
            versionName = "0.1.0",
            onAccessibility = {},
            onOverlay = {},
            onNotifications = {},
            onActivateDeviceAdmin = {},
            onRequestDeactivateDeviceAdmin = {},
            onBatteryOptimization = {},
            onClockGuardChange = {},
            onEditDelayChange = {},
            onSetGuardianPin = {},
            onChangeGuardianPin = {},
            onRemoveGuardianPin = {},
            onRecoverGuardianPin = {},
            onAppDetails = {}
        )
    }
}
