package com.senda.app.data.repository

import android.util.Log
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.preferences.ActiveSessionStore
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.preferences.PersistedActiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Application-scoped, ordered writes: save recovery data before committing usage. */
class SessionCheckpointRepository(
    private val store: ActiveSessionStore,
    private val usageDao: DailyUsageLogDao,
    private val cooldownStore: CooldownStore
) {
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (command in commands) {
                while (true) {
                    try {
                        command()
                        break
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.e("SessionCheckpoint", "Falha ao salvar sessão; nova tentativa pendente", error)
                        delay(1_000L)
                    }
                }
            }
        }
    }

    fun save(session: PersistedActiveSession, finished: Boolean = false, startCooldown: Boolean = false) {
        val checkpoint = session.copy(
            finished = finished,
            cooldownUntilEpochMillis = if (startCooldown && session.cooldownMinutes > 0) {
                session.savedAtEpochMillis + session.cooldownMinutes * 60_000L
            } else null
        )
        commands.trySend {
            store.save(checkpoint)
            replayUsage(checkpoint)
            if (checkpoint.finished) finish(checkpoint)
        }.getOrThrow()
    }

    suspend fun load(): PersistedActiveSession? {
        val result = CompletableDeferred<PersistedActiveSession?>()
        commands.send {
            val session = store.load()
            if (session != null) {
                replayUsage(session)
                if (session.finished) finish(session)
            }
            result.complete(session?.takeUnless { it.finished })
        }
        return result.await()
    }

    fun discard() {
        commands.trySend { store.clear() }.getOrThrow()
    }

    private suspend fun replayUsage(session: PersistedActiveSession) {
        if (session.groupId.isBlank()) return
        usageDao.recordSessionUsage(
            session.sessionId, session.usageKey,
            session.usageMillisByDate.mapValues { (_, millis) -> (millis / 1000L).toInt() }
        )
    }

    private suspend fun finish(session: PersistedActiveSession) {
        val until = session.cooldownUntilEpochMillis
        if (until != null && session.groupId.isNotBlank()) {
            cooldownStore.startCooldown(
                com.senda.app.core.UsageKey.scopeKey(session.usageKey), session.cooldownMinutes, until - session.cooldownMinutes * 60_000L
            )
        }
        store.clear()
    }
}
