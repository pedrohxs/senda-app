package com.senda.app.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.dao.DailyUsageLogDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyUsageLogDaoTest {

    private lateinit var database: SendaDatabase
    private lateinit var dailyUsageLogDao: DailyUsageLogDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dailyUsageLogDao = database.dailyUsageLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun duasSessoesDeDoisSegundosNaoConsomemUmMinuto() = runBlocking {
        dailyUsageLogDao.recordUsageSeconds(DATE, GROUP_ID, 2)
        dailyUsageLogDao.recordUsageSeconds(DATE, GROUP_ID, 2)

        val log = requireNotNull(dailyUsageLogDao.findByDateAndGroup(DATE, GROUP_ID))

        assertEquals(4, log.totalTimeUsedSeconds)
        assertEquals(0, log.totalTimeUsedMinutes)
    }

    @Test
    fun trintaSessoesDeDoisSegundosSomamSessentaSegundos() = runBlocking {
        repeat(30) {
            dailyUsageLogDao.recordUsageSeconds(DATE, GROUP_ID, 2)
        }

        val log = requireNotNull(dailyUsageLogDao.findByDateAndGroup(DATE, GROUP_ID))

        assertEquals(60, log.totalTimeUsedSeconds)
        assertEquals(1, log.totalTimeUsedMinutes)
    }

    @Test
    fun replayERestauracoesRepetidasNaoDuplicamUso() = runBlocking {
        dailyUsageLogDao.recordOpen(DATE, GROUP_ID)
        repeat(3) { dailyUsageLogDao.recordSessionUsage("session-1", GROUP_ID, mapOf(DATE to 60)) }
        dailyUsageLogDao.recordSessionUsage("session-1", GROUP_ID, mapOf(DATE to 95))
        // A stale snapshot must neither lower the checkpoint nor add usage.
        dailyUsageLogDao.recordSessionUsage("session-1", GROUP_ID, mapOf(DATE to 30))
        dailyUsageLogDao.recordSessionUsage("session-1", GROUP_ID, mapOf(DATE to 95))

        val log = requireNotNull(dailyUsageLogDao.findByDateAndGroup(DATE, GROUP_ID))
        assertEquals(95, log.totalTimeUsedSeconds)
        assertEquals(1, log.totalTimeUsedMinutes)
        assertEquals(1, log.totalOpens)
        assertEquals(95, dailyUsageLogDao.findSessionUsage("session-1", DATE)?.accountedSeconds)
    }

    @Test
    fun sessoesDistintasSomamEReplayDepoisDaMeiaNoiteMantemDataOriginal() = runBlocking {
        val tomorrow = "2026-01-02"
        dailyUsageLogDao.recordSessionUsage("a", GROUP_ID, mapOf(DATE to 10, tomorrow to 20))
        dailyUsageLogDao.recordSessionUsage("b", GROUP_ID, mapOf(tomorrow to 5))
        dailyUsageLogDao.recordSessionUsage("a", GROUP_ID, mapOf(DATE to 10, tomorrow to 20))
        assertEquals(10, dailyUsageLogDao.findByDateAndGroup(DATE, GROUP_ID)?.totalTimeUsedSeconds)
        assertEquals(25, dailyUsageLogDao.findByDateAndGroup(tomorrow, GROUP_ID)?.totalTimeUsedSeconds)
    }

    @Test
    fun saldoSeparaContagemCompartilhadaDaContagemPorAplicativo() = runBlocking {
        val instagram = "$GROUP_ID::item::instagram"
        val youtube = "$GROUP_ID::item::youtube"
        dailyUsageLogDao.recordSessionUsage("a", instagram, mapOf(DATE to 60))
        dailyUsageLogDao.recordSessionUsage("b", youtube, mapOf(DATE to 100))

        val shared = dailyUsageLogDao.dailySessionBalance(DATE, GROUP_ID, instagram, true, "a")
        val individual = dailyUsageLogDao.dailySessionBalance(DATE, GROUP_ID, instagram, false, "a")
        assertEquals(160, shared.totalSeconds)
        assertEquals(60, shared.sessionSeconds)
        assertEquals(60, individual.totalSeconds)
        assertEquals(60, individual.sessionSeconds)
    }

    private companion object {
        const val DATE = "2026-01-01"
        const val GROUP_ID = "group-1"
    }
}
