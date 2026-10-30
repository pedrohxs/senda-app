package com.senda.app.domain.security

import android.os.SystemClock
import com.senda.app.data.preferences.ProtectionStore

data class ClockGuardStatus(
    val tampered: Boolean,
    val remainingSeconds: Long
)

class ClockGuard(private val protectionStore: ProtectionStore) {

    fun check(
        nowWallMillis: Long = System.currentTimeMillis(),
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime(),
        bootCount: Int = protectionStore.currentBootCount()
    ): ClockGuardStatus {
        if (!protectionStore.clockGuardEnabled) {
            if (protectionStore.clockTamperRemainingSeconds(
                    nowElapsedRealtime = nowElapsedRealtime,
                    bootCount = bootCount
                ) > 0L
            ) {
                protectionStore.clearClockTamper()
            }
            return ClockGuardStatus(tampered = false, remainingSeconds = 0L)
        }

        val activeTamper = protectionStore.clockTamperRemainingSeconds(
            nowElapsedRealtime = nowElapsedRealtime,
            bootCount = bootCount
        )

        val evaluation = ClockDriftDetector.evaluate(
            anchor = protectionStore.clockAnchor(),
            nowWallMillis = nowWallMillis,
            nowElapsedRealtime = nowElapsedRealtime,
            bootCount = bootCount,
            nowZoneOffsetSeconds = protectionStore.currentZoneOffsetSeconds(nowWallMillis)
        )

        if (evaluation.zoneChanged) {
            com.senda.app.core.DiagnosticsLog.add(
                "relógio",
                "mudança de fuso detectada (novo offset ${protectionStore.currentZoneOffsetSeconds(nowWallMillis)}s)"
            )
        }

        if (evaluation.tampered) {
            com.senda.app.core.DiagnosticsLog.add(
                "relógio",
                "relógio alterado além da tolerância; bloqueio de 30 min"
            )
            if (activeTamper > 0L) {
                return ClockGuardStatus(tampered = true, remainingSeconds = activeTamper)
            }
            protectionStore.startClockTamper(
                nowElapsedRealtime = nowElapsedRealtime,
                bootCount = bootCount
            )
            return ClockGuardStatus(
                tampered = true,
                remainingSeconds = TAMPER_DURATION_SECONDS
            )
        }

        if (activeTamper > 0L) {
            protectionStore.clearClockTamper()
        }
        evaluation.nextAnchor?.let(protectionStore::saveClockAnchor)
        if (protectionStore.state.value.clockTamperRemainingSeconds > 0L) {
            protectionStore.refreshState()
        }
        return ClockGuardStatus(tampered = false, remainingSeconds = 0L)
    }

    companion object {
        const val TAMPER_DURATION_SECONDS = 30L * 60L
    }
}
