package com.senda.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.preferences.ActiveSessionStore
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.preferences.PersistedActiveSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCheckpointRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: SendaDatabase
    private val store = ActiveSessionStore(context)
    private val cooldowns = CooldownStore(context)
    private lateinit var repository: SessionCheckpointRepository

    @Before
    fun setUp() = runBlocking {
        store.clear()
        cooldowns.clearCooldown("checkpoint-test")
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java).build()
        repository = SessionCheckpointRepository(store, database.dailyUsageLogDao(), cooldowns)
    }

    @After
    fun tearDown() = runBlocking {
        repository.load() // Drain queued writes before closing Room.
        store.clear()
        cooldowns.clearCooldown("checkpoint-test")
        database.close()
    }

    @Test
    fun interromperDepoisDoSaveAntesDoCommitRecuperaSomenteUsoPendente() = runBlocking {
        val dao = database.dailyUsageLogDao()
        dao.recordSessionUsage("checkpoint-session", "checkpoint-test", mapOf(DATE to 60))
        store.save(session(95_250L)) // Crash after DataStore, before Room commit.

        repeat(3) { assertEquals(95_250L, repository.load()?.usageMillisByDate?.get(DATE)) }
        assertEquals(95, dao.findByDateAndGroup(DATE, "checkpoint-test")?.totalTimeUsedSeconds)
    }

    @Test
    fun interromperDepoisDoCommitNaoDuplicaUsoAoRestaurar() = runBlocking {
        val checkpoint = session(60_500L)
        store.save(checkpoint)
        database.dailyUsageLogDao().recordSessionUsage(checkpoint.sessionId, checkpoint.usageKey, mapOf(DATE to 60))

        val restored = requireNotNull(repository.load())
        assertEquals(239_500L, restored.remainingMillis)
        repository.save(restored.copy(usageMillisByDate = mapOf(DATE to 90_000L), remainingMillis = 210_000L))
        repository.load()
        assertEquals(90, database.dailyUsageLogDao().findByDateAndGroup(DATE, "checkpoint-test")?.totalTimeUsedSeconds)
    }

    @Test
    fun sessaoConcluidaDuranteFalhaNaoRetomaNemReiniciaCooldown() = runBlocking {
        val until = System.currentTimeMillis() + 120_000L
        store.save(session(60_000L).copy(finished = true, cooldownUntilEpochMillis = until))
        assertNull(repository.load())
        assertNull(store.load())
        assertEquals(until, cooldowns.cooldowns.first()["checkpoint-test"])
        assertNull(repository.load())
        assertEquals(until, cooldowns.cooldowns.first()["checkpoint-test"])
    }

    @Test
    fun finalizarEIniciarOutraSessaoPreservaOrdemDasGravacoes() = runBlocking {
        repository.save(session(60_000L), finished = true)
        repository.save(session(10_000L).copy(sessionId = "next-session"))
        assertEquals("next-session", repository.load()?.sessionId)
        assertEquals(70, database.dailyUsageLogDao().findByDateAndGroup(DATE, "checkpoint-test")?.totalTimeUsedSeconds)
    }

    @Test
    fun esperaPersonalizadaEhPersistidaNoEscopoDaJanelaSemBloquearPadrao() = runBlocking {
        val scope = com.senda.app.core.UsageKey.forLimit("checkpoint-test", "weekend")
        repository.save(session(60_000L).copy(usageKey = scope), finished = true, startCooldown = true)
        repository.load()
        assertNull(cooldowns.cooldowns.first()["checkpoint-test"])
        assertEquals(true, cooldowns.cooldowns.first()[scope] != null)
        cooldowns.clearCooldown(scope)
    }

    private fun session(millis: Long) = PersistedActiveSession(
        groupId = "checkpoint-test", packageName = "test.app", targetIdentifier = "test.app",
        isWebsite = false, mode = SessionCountMode.ACTIVE_SCREEN_TIME,
        durationSeconds = 300, remainingSeconds = ((300_000L - millis + 999L) / 1000L).toInt(),
        remainingMillis = 300_000L - millis, deadlineElapsedRealtime = 400_000L,
        cooldownMinutes = 10, savedAtElapsedRealtime = 100_000L, bootCount = 7,
        sessionId = "checkpoint-session", savedAtEpochMillis = System.currentTimeMillis(),
        usageMillisByDate = mapOf(DATE to millis)
    )

    private companion object { const val DATE = "2026-10-26" }
}
