package com.senda.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatsRepositoryTest {

    private lateinit var database: SendaDatabase
    private lateinit var repository: StatsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StatsRepository(
            appGroupDao = database.appGroupDao(),
            dailyUsageLogDao = database.dailyUsageLogDao(),
            challengeReasonDao = database.challengeReasonDao(),
            reportHistoryDao = database.reportHistoryDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun agregaUsoDaSemanaEOfensiva() = runBlocking {
        val today = LocalDate.now()

        database.appGroupDao().upsert(
            AppGroupEntity(
                id = "group-1",
                name = "Redes Sociais",
                dailyLimitMinutes = 60
            )
        )
        database.dailyUsageLogDao().insert(
            DailyUsageLogEntity(
                date = today.toString(),
                groupId = "group-1",
                totalTimeUsedMinutes = 30,
                totalTimeUsedSeconds = 30 * 60,
                impulsesAborted = 2
            )
        )
        database.dailyUsageLogDao().insert(
            DailyUsageLogEntity(
                date = today.minusDays(1).toString(),
                groupId = "group-1",
                totalTimeUsedMinutes = 40,
                totalTimeUsedSeconds = 40 * 60
            )
        )
        database.challengeReasonDao().insert(
            ChallengeReasonEntity(
                packageName = "com.instagram.android",
                displayName = "Instagram",
                groupId = "group-1",
                reason = "Responder uma mensagem",
                createdAt = System.currentTimeMillis()
            )
        )

        val stats = repository.observeUsage().first()

        assertEquals(30 * 60, stats.todaySeconds)
        assertEquals(7, stats.week.size)
        assertEquals(0, stats.streakDays) // Old records do not prove historical goals or coverage.
        assertEquals(0, stats.impulses) // Today's unfinished day is shown separately.
        assertEquals(today.minusDays(1).toString(), stats.week.last().date)
        assertEquals(0, stats.weekAverageSeconds)
        assertEquals(null, stats.trendPercent)
        assertEquals(40 * 60, stats.groupSummaries.single().seconds)
        assertEquals("Responder uma mensagem", stats.reasons.single().reason)
        val todayDetail = stats.dayDetails.first { it.date == today.minusDays(1).toString() }
        assertEquals(40 * 60, todayDetail.totalSeconds)
        assertEquals("Redes Sociais", todayDetail.groups.single().name)
    }
}
