package com.senda.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.DailyGoalEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.LimitRules
import com.senda.app.data.local.entity.ReportDayEntity
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.data.preferences.SendaSettingsStore
import com.senda.app.domain.backup.BackupCodec
import com.senda.app.domain.backup.BackupReadResult
import com.senda.app.domain.policy.DailyLimitLatch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {

    private lateinit var database: SendaDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(
            resolver = context.contentResolver,
            database = database,
            settingsStore = SendaSettingsStore(context),
            protectionStore = ProtectionStore(context),
            dailyLimitLatch = DailyLimitLatch(),
            versionName = "test",
            versionCode = 1,
            onBeforeRestore = {}
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportaRestauraEDepoisOutroArquivoSubstituiTudo() = runBlocking {
        seed()

        val exported = repository.snapshot()
        assertEquals(1, exported.data.groups.size)
        assertEquals(2, exported.data.items.size)
        assertEquals(1, exported.data.schedules.size)
        assertEquals(2, exported.data.usage.size)
        assertEquals(1, exported.data.challengeReasons.size)
        assertEquals(1, exported.data.reportDays.size)
        assertEquals(1, exported.data.dailyGoals.size)

        val encoded = BackupCodec.encode(exported)
        val decoded = BackupCodec.decode(encoded)
        assertTrue(decoded is BackupReadResult.Success)
        val file = (decoded as BackupReadResult.Success).file

        wipe()

        val summary = repository.restore(file)
        assertEquals(1, summary.groups)
        assertEquals(2, summary.targets)
        assertEquals(2, summary.usageDays)

        val groups = database.appGroupDao().allGroups()
        assertEquals(1, groups.size)
        assertEquals("Redes", groups.first().name)
        assertEquals(45, groups.first().dailyLimitMinutes)

        val items = database.groupItemDao().allItems()
        assertEquals(2, items.size)
        assertTrue(items.any { it.targetIdentifier == "com.instagram.android" })

        val schedules = database.groupScheduleDao().allSchedules()
        assertEquals(1, schedules.size)
        assertEquals(ScheduleType.STRICT, schedules.first().scheduleType)
        assertEquals(20, schedules.first().customRules?.dailyLimitMinutes)

        val usage = database.dailyUsageLogDao().allUsage()
        assertEquals(2, usage.size)
        assertEquals(600, usage.first { it.date == "2026-10-28" }.totalTimeUsedSeconds)

        assertEquals(1, database.challengeReasonDao().all().size)
        assertEquals(1, database.reportHistoryDao().allDays().size)
        assertEquals(1, database.reportHistoryDao().allGoals().size)
    }

    @Test
    fun restaurarApagaOsDadosQueNaoEstaoNoArquivo() = runBlocking {
        seed()
        val file = (BackupCodec.decode(BackupCodec.encode(repository.snapshot()))
            as BackupReadResult.Success).file

        // Um grupo criado depois do backup precisa sumir na restauração.
        database.appGroupDao().upsert(
            AppGroupEntity(id = "novo", name = "Criado depois", dailyLimitMinutes = 10)
        )
        assertEquals(2, database.appGroupDao().allGroups().size)

        repository.restore(file)

        val groups = database.appGroupDao().allGroups()
        assertEquals(1, groups.size)
        assertNull(groups.firstOrNull { it.id == "novo" })
        assertEquals("g1", groups.first().id)
    }

    private suspend fun seed() {
        val groupDao = database.appGroupDao()
        val itemDao = database.groupItemDao()
        val scheduleDao = database.groupScheduleDao()
        val usageDao = database.dailyUsageLogDao()
        val reasonDao = database.challengeReasonDao()
        val reportDao = database.reportHistoryDao()

        groupDao.upsert(
            AppGroupEntity(
                id = "g1",
                name = "Redes",
                dailyLimitMinutes = 45,
                sessionLimitMinutes = 10
            )
        )
        itemDao.upsertAll(
            listOf(
                GroupItemEntity(
                    groupId = "g1",
                    targetType = TargetType.APP,
                    targetIdentifier = "com.instagram.android",
                    displayName = "Instagram"
                ),
                GroupItemEntity(
                    groupId = "g1",
                    targetType = TargetType.WEBSITE,
                    targetIdentifier = "instagram.com",
                    displayName = "instagram.com"
                )
            )
        )
        scheduleDao.upsertAll(
            listOf(
                GroupScheduleEntity(
                    groupId = "g1",
                    scheduleType = ScheduleType.STRICT,
                    daysOfWeek = listOf(1, 2, 3),
                    startHour = 8,
                    startMinute = 0,
                    endHour = 18,
                    endMinute = 0,
                    customRules = LimitRules(dailyLimitMinutes = 20)
                )
            )
        )
        usageDao.insertAll(
            listOf(
                DailyUsageLogEntity(date = "2026-10-28", groupId = "g1", totalTimeUsedSeconds = 600),
                DailyUsageLogEntity(
                    date = "2026-10-29",
                    groupId = "g1::item::com.instagram.android",
                    totalTimeUsedSeconds = 300
                )
            )
        )
        reasonDao.insertAll(
            listOf(
                ChallengeReasonEntity(
                    packageName = "com.instagram.android",
                    displayName = "Instagram",
                    groupId = "g1",
                    reason = "ver stories",
                    createdAt = 1_700_000_000_000L
                )
            )
        )
        reportDao.saveDays(
            listOf(
                ReportDayEntity(
                    date = "2026-10-29",
                    coveredMillis = 60_000L,
                    expectedMillis = 60_000L,
                    goalsRecorded = true,
                    zoneId = "America/Fortaleza"
                )
            )
        )
        reportDao.saveAllGoals(
            listOf(
                DailyGoalEntity(
                    date = "2026-10-29",
                    usageKey = "g1",
                    groupId = "g1",
                    groupName = "Redes",
                    targetName = "Redes",
                    limitSeconds = 2700,
                    shared = true
                )
            )
        )
    }

    private suspend fun wipe() {
        database.appGroupDao().deleteAll()
        database.dailyUsageLogDao().deleteAll()
        database.dailyUsageLogDao().deleteAllSessionUsage()
        database.challengeReasonDao().deleteAll()
        database.reportHistoryDao().deleteAllDays()
        database.reportHistoryDao().deleteAllGoals()
    }
}
