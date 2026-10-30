package com.senda.app.data.repository

import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.preferences.ServiceHeartbeatStore
import com.senda.app.domain.lifecycle.ProtectionHealth
import com.senda.app.domain.lifecycle.ProtectionHealthResolver
import com.senda.app.domain.lifecycle.ProtectionStatus
import com.senda.app.service.AppMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

class ProtectionHealthRepository(
    private val permissions: FocoPermissions,
    private val heartbeats: ServiceHeartbeatStore
) {
    private val refreshes = Channel<Unit>(Channel.CONFLATED)
    val state = flow {
        while (true) {
            val health = try {
                val permission = permissions.current()
                ProtectionHealthResolver.resolve(
                    permission.accessibilityEnabled, permission.overlayEnabled, AppMonitor.accessibilityConnected,
                    AppMonitor.protectionServiceRunning, heartbeats.last(), System.currentTimeMillis(),
                    permission.notificationsEnabled, permission.batteryOptimizationIgnored
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ProtectionHealth(ProtectionStatus.SERVICE_UNRESPONSIVE)
            }
            emit(health)
            withTimeoutOrNull(2_000L) { refreshes.receive() }
        }
    }.distinctUntilChanged().stateIn(
        CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.WhileSubscribed(5_000L), ProtectionHealth()
    )

    fun refresh() { refreshes.trySend(Unit) }
}
