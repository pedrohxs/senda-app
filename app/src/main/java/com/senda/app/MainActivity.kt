package com.senda.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.AccentSage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.domain.backup.BackupReadResult
import com.senda.app.service.FocoForegroundService
import com.senda.app.ui.SendaMainShell
import com.senda.app.ui.onboarding.OnboardingScreen
import com.senda.app.ui.theme.SendaTheme

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        com.senda.app.service.AppMonitor.onForegroundPackage(packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocoForegroundService.ensureRunning(this)
        enableEdgeToEdge()
        setContent {
            SendaTheme {
                SendaApp()
            }
        }
    }
}

@Composable
private fun SendaApp() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val application = context.applicationContext as SendaApplication
    val container = remember(application) { application.container }
    val permissions = remember(application) { FocoPermissions(application) }
    val setupCompleted by container.settingsStore.setupCompleted.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var permissionState by remember {
        mutableStateOf(permissions.current())
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(!permissions.current().essentialGranted)
    }
    var starterGoalName by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val current = permissions.current()
        permissionState = current
        container.protectionHealth.refresh()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionState = permissions.current()
    }

    LaunchedEffect(setupCompleted, showOnboarding, permissionState) {
        // Instalações antigas, com permissões já concedidas, migram sem repetir o setup.
        // Sem as permissões essenciais o onboarding continua de pé (inclusive
        // depois de restaurar um backup numa instalação limpa).
        if (setupCompleted == false &&
            permissionState.essentialGranted &&
            (!showOnboarding || container.groupRepository.hasGroups())
        ) {
            container.settingsStore.completeSetup()
        }
    }

    // Cópia automática diária e oferta de restauração numa instalação limpa.
    var pendingRestore by remember { mutableStateOf<BackupReadResult.Success?>(null) }
    var restoreInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val lastAuto = container.settingsStore.lastAutoBackupAt.first()
            val now = System.currentTimeMillis()
            if (lastAuto == null || now - lastAuto >= AUTO_BACKUP_MIN_INTERVAL_MS) {
                container.backupRepository.exportAutoBackup()
            }
        }

        if (setupCompleted == false) {
            pendingRestore = container.backupRepository.latestAutoBackup() as? BackupReadResult.Success
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (setupCompleted == null) {
            Surface(color = DarkCanvas, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentSage) }
            }
        } else if (showOnboarding && setupCompleted != true) {
        OnboardingScreen(
            state = permissionState,
            onAccessibility = {
                context.startActivity(permissions.accessibilityIntent())
            },
            onOverlay = {
                context.startActivity(permissions.overlayIntent())
            },
            onNotifications = {
                val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED

                if (needsRuntimePermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(permissions.notificationIntent())
                }
            },
            onDeviceAdmin = {
                val intent = permissions.deviceAdminIntent()
                val opened = runCatching {
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(permissions.deviceAdminSettingsIntent())
                    }
                }.recoverCatching {
                    context.startActivity(permissions.deviceAdminSettingsIntent())
                }.isSuccess
                if (!opened) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.device_admin_unavailable),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onAppDetails = {
                context.startActivity(permissions.appDetailsIntent())
            },
            onBatteryOptimization = {
                context.startActivity(permissions.batteryOptimizationIntent())
            },
            onContinue = {
                if (permissionState.essentialGranted) {
                    starterGoalName = it.name
                    showOnboarding = false
                    scope.launch { container.settingsStore.completeSetup() }
                }
            }
        )
        } else {
            SendaMainShell(container = container, starterGoal = starterGoalName?.let { com.senda.app.domain.setup.SetupGoal.valueOf(it) })
        }

        val restoreCandidate = pendingRestore
        if (restoreCandidate != null) {
            val summary = restoreCandidate.file.summary
            AlertDialog(
                onDismissRequest = { if (!restoreInProgress) pendingRestore = null },
                containerColor = com.senda.app.ui.theme.CardSurface,
                titleContentColor = com.senda.app.ui.theme.TextWarmWhite,
                textContentColor = com.senda.app.ui.theme.TextMutedGrey,
                title = { Text(resources.getString(R.string.restore_offer_title)) },
                text = {
                    Text(
                        resources.getString(
                            R.string.restore_offer_message,
                            summary.groups,
                            summary.usageDays
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = !restoreInProgress,
                        onClick = {
                            restoreInProgress = true
                            scope.launch {
                                val result = runCatching {
                                    container.backupRepository.restore(restoreCandidate.file)
                                }.isSuccess
                                restoreInProgress = false
                                pendingRestore = null
                                Toast.makeText(
                                    context,
                                    resources.getString(
                                        if (result) R.string.restore_offer_done else R.string.restore_offer_failed
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Text(
                            text = resources.getString(
                                if (restoreInProgress) R.string.restore_offer_running
                                else R.string.restore_offer_confirm
                            ),
                            color = AccentSage
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !restoreInProgress,
                        onClick = { pendingRestore = null }
                    ) {
                        Text(
                            text = resources.getString(R.string.restore_offer_later),
                            color = com.senda.app.ui.theme.TextMutedGrey
                        )
                    }
                }
            )
        }
    }
}

/** Intervalo mínimo entre cópias automáticas (uma por dia na prática). */
private const val AUTO_BACKUP_MIN_INTERVAL_MS = 20L * 60L * 60L * 1_000L
