package com.senda.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.data.preferences.SendaSettingsStore
import com.senda.app.data.repository.BackupRepository
import com.senda.app.data.repository.BackupRestoreSummary
import com.senda.app.di.AppContainer
import com.senda.app.domain.backup.BackupError
import com.senda.app.domain.backup.BackupFile
import com.senda.app.domain.backup.BackupReadResult
import com.senda.app.domain.security.ClockGuard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import com.senda.app.domain.lifecycle.ProtectionHealth
import com.senda.app.data.repository.ProtectionHealthRepository

data class SettingsUiState(
    val permissions: SendaPermissionState,
    val clockGuardEnabled: Boolean,
    val editDelayEnabled: Boolean,
    val guardianPinSet: Boolean,
    val clockTamperRemainingSeconds: Long,
    val protection: ProtectionHealth = ProtectionHealth(),
    val lastBackupAt: Long? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false
)

/** Arquivo escolhido pelo usuário, pronto para ser confirmado. */
sealed interface BackupImportPreview {
    data class Ready(val file: BackupFile) : BackupImportPreview
    data class Failed(val error: BackupError) : BackupImportPreview
}

sealed interface BackupFeedback {
    data object Exported : BackupFeedback
    data object ExportFailed : BackupFeedback
    data class Restored(val summary: BackupRestoreSummary) : BackupFeedback
    data object RestoreFailed : BackupFeedback
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val permissions: FocoPermissions,
    private val protectionStore: ProtectionStore,
    private val clockGuard: ClockGuard,
    private val protectionHealth: ProtectionHealthRepository,
    private val settingsStore: SendaSettingsStore,
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val attempts = MutableStateFlow(0)
    private val _uiState = MutableStateFlow(state(isLoading = true))
    val uiState: StateFlow<SettingsUiState> = attempts
        .flatMapLatest {
            combine(
                _uiState,
                protectionHealth.state,
                settingsStore.lastBackupAt
            ) { state, health, lastBackupAt ->
                state.copy(
                    permissions = permissions.current(),
                    protection = health,
                    lastBackupAt = lastBackupAt,
                    isLoading = false,
                    loadFailed = false
                )
            }.catch {
                emit(state().copy(isLoading = false, loadFailed = true))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _uiState.value)

    private val _importPreview = MutableStateFlow<BackupImportPreview?>(null)
    val importPreview: StateFlow<BackupImportPreview?> = _importPreview

    private val _feedback = MutableStateFlow<BackupFeedback?>(null)
    val feedback: StateFlow<BackupFeedback?> = _feedback

    fun refresh() {
        protectionHealth.refresh()
        clockGuard.check()
        _uiState.value = state().copy(lastBackupAt = _uiState.value.lastBackupAt)
    }

    /** Nova tentativa de leitura quando o carregamento inicial falhou. */
    fun retry() {
        _uiState.value = state(isLoading = true)
        attempts.value += 1
        refresh()
    }

    fun setClockGuardEnabled(enabled: Boolean) {
        protectionStore.setClockGuardEnabled(enabled)
        _uiState.value = state()
    }

    fun setEditDelayEnabled(enabled: Boolean) {
        protectionStore.setEditDelayEnabled(enabled)
        _uiState.value = state()
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _feedback.value = if (backupRepository.export(uri)) {
                BackupFeedback.Exported
            } else {
                BackupFeedback.ExportFailed
            }
        }
    }

    fun inspectBackup(uri: Uri) {
        viewModelScope.launch {
            _importPreview.value = when (val result = backupRepository.read(uri)) {
                is BackupReadResult.Success -> BackupImportPreview.Ready(result.file)
                is BackupReadResult.Failure -> BackupImportPreview.Failed(result.error)
            }
        }
    }

    fun cancelImport() {
        _importPreview.value = null
    }

    fun confirmImport() {
        val preview = _importPreview.value as? BackupImportPreview.Ready ?: return
        _importPreview.value = null

        viewModelScope.launch {
            _feedback.value = runCatching { backupRepository.restore(preview.file) }
                .fold(
                    onSuccess = { BackupFeedback.Restored(it) },
                    onFailure = { BackupFeedback.RestoreFailed }
                )
            refresh()
        }
    }

    fun consumeFeedback() {
        _feedback.value = null
    }

    private fun state(isLoading: Boolean = false): SettingsUiState = SettingsUiState(
        permissions = permissions.current(),
        clockGuardEnabled = protectionStore.clockGuardEnabled,
        editDelayEnabled = protectionStore.editDelayEnabled,
        guardianPinSet = protectionStore.guardianPinSet,
        clockTamperRemainingSeconds = protectionStore.clockTamperRemainingSeconds(),
        protection = protectionHealth.state.value,
        isLoading = isLoading
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    permissions = container.permissions,
                    protectionStore = container.protectionStore,
                    clockGuard = container.clockGuard,
                    protectionHealth = container.protectionHealth,
                    settingsStore = container.settingsStore,
                    backupRepository = container.backupRepository
                )
            }
        }
    }
}
