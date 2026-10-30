package com.senda.app.domain.lifecycle

enum class ProtectionStatus {
    CHECKING, ACCESSIBILITY_DISABLED, OVERLAY_MISSING, ACCESSIBILITY_DISCONNECTED,
    SERVICE_STOPPED, SERVICE_UNRESPONSIVE, NOTIFICATIONS_DISABLED, BATTERY_RESTRICTED, ACTIVE
}

data class ProtectionHealth(val status: ProtectionStatus = ProtectionStatus.CHECKING) {
    val monitoringAvailable: Boolean get() = status in setOf(
        ProtectionStatus.ACTIVE, ProtectionStatus.NOTIFICATIONS_DISABLED, ProtectionStatus.BATTERY_RESTRICTED
    )
}

object ProtectionHealthResolver {
    fun resolve(
        accessibilityEnabled: Boolean, overlayEnabled: Boolean, accessibilityConnected: Boolean,
        serviceRunning: Boolean, lastHeartbeatMillis: Long, nowMillis: Long,
        notificationsEnabled: Boolean, batteryOptimizationIgnored: Boolean
    ): ProtectionHealth = ProtectionHealth(when {
        !accessibilityEnabled -> ProtectionStatus.ACCESSIBILITY_DISABLED
        !overlayEnabled -> ProtectionStatus.OVERLAY_MISSING
        !accessibilityConnected -> ProtectionStatus.ACCESSIBILITY_DISCONNECTED
        !serviceRunning -> ProtectionStatus.SERVICE_STOPPED
        lastHeartbeatMillis <= 0L || nowMillis - lastHeartbeatMillis !in 0L..90_000L -> ProtectionStatus.SERVICE_UNRESPONSIVE
        !notificationsEnabled -> ProtectionStatus.NOTIFICATIONS_DISABLED
        !batteryOptimizationIgnored -> ProtectionStatus.BATTERY_RESTRICTED
        else -> ProtectionStatus.ACTIVE
    })
}
