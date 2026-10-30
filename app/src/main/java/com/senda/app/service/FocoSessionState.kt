package com.senda.app.service

import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.domain.policy.InterventionDecision
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlockedSiteEvent(
    val packageName: String,
    val url: String,
    val domain: String
)

object AppMonitor {

    @Volatile var protectionServiceRunning: Boolean = false
        private set

    fun setProtectionServiceRunning(running: Boolean) { protectionServiceRunning = running }

    @Volatile var accessibilityConnected: Boolean = false
        private set

    fun setAccessibilityConnected(connected: Boolean) { accessibilityConnected = connected }

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private val _monitoredAppOpened = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val monitoredAppOpened: SharedFlow<String> = _monitoredAppOpened.asSharedFlow()

    private val _sensitiveAppOpened = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val sensitiveAppOpened: SharedFlow<String> = _sensitiveAppOpened.asSharedFlow()

    private val _blockedSiteDetected = MutableSharedFlow<BlockedSiteEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val blockedSiteDetected: SharedFlow<BlockedSiteEvent> = _blockedSiteDetected.asSharedFlow()

    // Retain the latest strict notice even while the foreground service starts.
    private val _appBlocked = Channel<InterventionDecision.Block>(Channel.CONFLATED)
    val appBlocked = _appBlocked.receiveAsFlow()

    fun onForegroundPackage(packageName: String) {
        _foregroundPackage.value = packageName
    }

    fun clearForegroundPackage() {
        _foregroundPackage.value = null
    }

    fun onMonitoredPackage(packageName: String) {
        _foregroundPackage.value = packageName
        _monitoredAppOpened.tryEmit(packageName)
    }

    fun onSensitivePackage(packageName: String) {
        _foregroundPackage.value = packageName
        _sensitiveAppOpened.tryEmit(packageName)
    }

    fun onBlockedSite(packageName: String, url: String, domain: String) {
        _blockedSiteDetected.tryEmit(
            BlockedSiteEvent(packageName = packageName, url = url, domain = domain)
        )
    }

    fun onAppBlocked(block: InterventionDecision.Block) {
        _appBlocked.trySend(block)
    }
}

data class ActiveFocoSession(
    val groupId: String,
    val packageName: String,
    val targetIdentifier: String,
    val isWebsite: Boolean,
    val displayName: String,
    val mode: SessionCountMode,
    val durationSeconds: Int,
    val remainingSeconds: Int,
    val isPaused: Boolean,
    val allowedPackages: Set<String> = setOf(packageName),
    val usageKey: String = groupId
)

object FocoSessionState {

    private val _session = MutableStateFlow<ActiveFocoSession?>(null)
    val session: StateFlow<ActiveFocoSession?> = _session.asStateFlow()

    internal fun set(session: ActiveFocoSession?) {
        _session.value = session
    }
}
