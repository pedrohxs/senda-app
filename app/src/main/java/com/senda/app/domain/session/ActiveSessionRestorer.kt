package com.senda.app.domain.session

import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.preferences.PersistedActiveSession

sealed interface SessionRestoreDecision {

    data object Discard : SessionRestoreDecision

    data class Resume(
        val remainingSeconds: Int,
        val usedSeconds: Int,
        val remainingMillis: Long = remainingSeconds * 1000L
    ) : SessionRestoreDecision

    data class Expired(
        val usedSeconds: Int
    ) : SessionRestoreDecision
}

object ActiveSessionRestorer {

    fun decide(
        session: PersistedActiveSession,
        nowElapsedRealtime: Long,
        currentBootCount: Int,
        foregroundPackage: String?
    ): SessionRestoreDecision {
        if (session.bootCount != currentBootCount || nowElapsedRealtime < session.savedAtElapsedRealtime ||
            session.durationSeconds <= 0) {
            return SessionRestoreDecision.Discard
        }

        val remaining = when (session.mode) {
            SessionCountMode.WALL_CLOCK_WINDOW -> {
                (session.deadlineElapsedRealtime - nowElapsedRealtime).coerceAtLeast(0L)
            }

            SessionCountMode.ACTIVE_SCREEN_TIME -> {
                // The current foreground app cannot prove what happened while the
                // service was absent (screen off, another app, etc.). Resume the checkpoint.
                session.remainingMillis
            }
        }.coerceIn(0L, session.durationSeconds * 1000L)

        if (remaining <= 0) {
            return SessionRestoreDecision.Expired(usedSeconds = session.durationSeconds)
        }

        val remainingSeconds = ((remaining + 999L) / 1000L).toInt()
        return SessionRestoreDecision.Resume(
            remainingSeconds = remainingSeconds,
            usedSeconds = (session.durationSeconds - remainingSeconds).coerceAtLeast(0),
            remainingMillis = remaining
        )
    }
}
