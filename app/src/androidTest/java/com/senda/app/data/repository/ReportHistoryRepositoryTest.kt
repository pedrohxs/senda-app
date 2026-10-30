package com.senda.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ReportHistoryRepositoryTest {
    private val zone = ZoneId.of("America/Fortaleza")
    private val clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), zone)
    private lateinit var db: SendaDatabase
    private lateinit var repository: ReportHistoryRepository
    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SendaDatabase::class.java).build()
        db.appGroupDao().upsert(AppGroupEntity(id = "g", name = "Redes", dailyLimitMinutes = 1))
        db.groupItemDao().upsert(GroupItemEntity(groupId = "g", targetType = TargetType.APP, targetIdentifier = "app.a", displayName = "App A"))
        db.groupScheduleDao().upsert(GroupScheduleEntity(groupId = "g", scheduleType = ScheduleType.LIMITED,
            daysOfWeek = (1..7).toList(), startHour = 0, startMinute = 0, endHour = 0, endMinute = 0))
        repository = ReportHistoryRepository(db, clock)
        Unit
    }
    @After fun tearDown() { db.close() }

    @Test fun grupoSemHorariosNaoCriaMetaDiariaAtiva() = runBlocking {
        db.groupScheduleDao().deleteByGroup("g")
        repository.captureGoals()
        assertTrue(db.reportHistoryDao().goalsForDate("2026-10-26").isEmpty())
    }

    @Test fun ausenciaDeUsoNaoSignificaAusenciaDeMonitoramento() = runBlocking {
        val start = clock.millis()
        repository.recordHeartbeat(start, 100_000L, 1, true)
        repository.recordHeartbeat(start + 30_000L, 130_000L, 1, true)
        assertEquals(30_000L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
        assertTrue(db.reportHistoryDao().findDay("2026-10-26")?.goalsRecorded == true)
        assertNull(db.dailyUsageLogDao().findByDateAndGroup("2026-10-26", "g"))
        repository.recordHeartbeat(start + 60_000L, 160_000L, 1, false)
        assertEquals(30_000L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
    }

    @Test fun interrupcaoLongaNaoPreencheLacunaComoSeFosseUsoZero() = runBlocking {
        repository.recordHeartbeat(clock.millis(), 100_000L, 1, true)
        repository.recordHeartbeat(clock.millis() + 120_000L, 220_000L, 1, true)
        assertEquals(0L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
    }

    @Test fun meiaNoiteConcluiDiaComUsoZeroSemExigirAberturaDeApp() = runBlocking {
        val midnight = Instant.parse("2026-10-26T03:00:00Z").toEpochMilli()
        repository.recordHeartbeat(midnight - 30_000L, 100_000L, 1, true)
        val previous = requireNotNull(db.reportHistoryDao().findDay("2026-10-25"))
        db.reportHistoryDao().saveDay(previous.copy(coveredMillis = previous.expectedMillis - 30_000L))
        repository.recordHeartbeat(midnight, 130_000L, 1, true)
        val stats = StatsRepository(db.appGroupDao(), db.dailyUsageLogDao(), db.challengeReasonDao(), db.reportHistoryDao(), clock).observeUsage().first()
        assertEquals(1, stats.completeDays)
        assertEquals(0, stats.weekAverageSeconds)
        assertEquals(1, stats.streakDays)
        assertEquals(DayCoverage.COMPLETE, stats.week.last().coverage)
    }

    @Test fun editarMetaHojeNaoMudaRegistroDeOntem() = runBlocking {
        val yesterday = Instant.parse("2026-10-25T15:00:00Z").toEpochMilli()
        repository.recordHeartbeat(yesterday, 100_000L, 1, true)
        db.appGroupDao().upsert(AppGroupEntity(id = "g", name = "Redes", dailyLimitMinutes = 120))
        repository.captureGoals()
        assertEquals(60, db.reportHistoryDao().goalsForDate("2026-10-25").single().limitSeconds)
        assertEquals(7200, db.reportHistoryDao().goalsForDate("2026-10-26").single().limitSeconds)
    }

    @Test fun mudancaParaLimitesIndividuaisNaoInventaDistribuicaoDeUsoAnterior() = runBlocking {
        repository.captureGoals()
        db.appGroupDao().upsert(AppGroupEntity(id = "g", name = "Redes", sharedLimitAcrossApps = false))
        repository.captureGoals()
        assertFalse(requireNotNull(db.reportHistoryDao().findDay("2026-10-26")).goalsReliable)
        repository.captureGoals()
        assertFalse(requireNotNull(db.reportHistoryDao().findDay("2026-10-26")).goalsReliable)
        assertFalse(db.reportHistoryDao().goalsForDate("2026-10-26").single().shared)
    }

    @Test fun telaApagadaMantemCoberturaDuranteSonoDoMesmoProcesso() = runBlocking {
        val start = Instant.parse("2026-10-25T23:00:00Z").toEpochMilli() // 20h local
        repository.recordHeartbeat(start, 100_000L, 1, true, screenOff = true)
        repository.recordHeartbeat(start + 12 * 3_600_000L, 100_000L + 12 * 3_600_000L, 1, true)
        assertEquals(4 * 3_600_000L, db.reportHistoryDao().findDay("2026-10-25")?.coveredMillis)
        assertEquals(8 * 3_600_000L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
    }

    @Test fun novoProcessoNaoPresumeQueTelaFicouApagadaDuranteAusencia() = runBlocking {
        repository.recordHeartbeat(clock.millis(), 100_000L, 1, true, screenOff = true)
        ReportHistoryRepository(db, clock).recordHeartbeat(clock.millis() + 120_000L, 220_000L, 1, true)
        assertEquals(0L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
    }

    @Test fun semAppsAtivosNaoContaDiaComoMonitorado() = runBlocking {
        db.appGroupDao().upsert(AppGroupEntity(id = "g", name = "Redes", isEnabled = false))
        repository.recordHeartbeat(clock.millis(), 100_000L, 1, true)
        repository.recordHeartbeat(clock.millis() + 30_000L, 130_000L, 1, true)
        assertEquals(0L, db.reportHistoryDao().findDay("2026-10-26")?.coveredMillis)
    }
}
