package com.senda.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.TargetType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupRepositoryTest {

    private lateinit var database: SendaDatabase
    private lateinit var repository: GroupRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = GroupRepository(
            database = database,
            appGroupDao = database.appGroupDao(),
            groupItemDao = database.groupItemDao(),
            groupScheduleDao = database.groupScheduleDao(),
            dailyUsageLogDao = database.dailyUsageLogDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun salvaCarregaEExcluiGrupoComItensEHorarios() = runBlocking {
        repository.saveGroup(
            group = group(),
            items = listOf(item()),
            schedules = listOf(schedule())
        )

        val loaded = requireNotNull(repository.getGroupForEdit(GROUP_ID))
        assertEquals("Redes Sociais", loaded.group.name)
        assertEquals(1, loaded.items.size)
        assertEquals("com.instagram.android", loaded.items.first().targetIdentifier)
        assertEquals(1, loaded.schedules.size)

        repository.deleteGroup(GROUP_ID)

        assertNull(repository.getGroupForEdit(GROUP_ID))
    }

    @Test
    fun edicaoAtualizaItensSemDuplicar() = runBlocking {
        repository.saveGroup(
            group = group(),
            items = listOf(item()),
            schedules = emptyList()
        )

        repository.updateGroup(
            group = group().copy(name = "Redes Editadas"),
            items = listOf(
                item().copy(targetIdentifier = "com.instagram.android"),
                item().copy(targetIdentifier = "com.zhiliaoapp.musically", displayName = "TikTok")
            ),
            schedules = listOf(schedule())
        )

        val loaded = requireNotNull(repository.getGroupForEdit(GROUP_ID))
        assertEquals("Redes Editadas", loaded.group.name)
        assertEquals(2, loaded.items.size)
        assertEquals(1, loaded.schedules.size)
    }

    @Test
    fun aparenciaPreservaRegrasPausaEstadoItensHorariosEHistorico() = runBlocking {
        repository.saveGroup(group().copy(isEnabled = false, temporaryDisableUntil = 123_456L),
            listOf(item()), listOf(schedule()))
        database.dailyUsageLogDao().recordUsageSeconds("2026-10-26", GROUP_ID, 75)
        val before = requireNotNull(repository.getGroupForEdit(GROUP_ID))

        repository.updateAppearance(GROUP_ID, "Novo nome", "#FFFFFF")

        assertEquals(before.copy(group = before.group.copy(name = "Novo nome", colorHex = "#FFFFFF")),
            repository.getGroupForEdit(GROUP_ID))
        assertEquals(75, database.dailyUsageLogDao().findByDateAndGroup("2026-10-26", GROUP_ID)?.totalTimeUsedSeconds)
    }

    @Test
    fun atualizarRegrasPreservaEstadoEPausaMaisRecentesDoBanco() = runBlocking {
        repository.saveGroup(group(), listOf(item()), emptyList())
        val draft = group().copy(dailyLimitMinutes = 45)
        database.appGroupDao().upsert(group().copy(isEnabled = false, temporaryDisableUntil = 987_654L))

        repository.updateGroup(draft, listOf(item()), listOf(schedule()))

        val loaded = requireNotNull(repository.getGroupForEdit(GROUP_ID))
        assertEquals(45, loaded.group.dailyLimitMinutes)
        assertEquals(false, loaded.group.isEnabled)
        assertEquals(987_654L, loaded.group.temporaryDisableUntil)
    }

    @Test(expected = IllegalStateException::class)
    fun editarAparenciaNaoRecriaGrupoExcluido() = runBlocking {
        repository.updateAppearance(GROUP_ID, "Novo nome", "#FFFFFF")
    }

    @Test(expected = IllegalStateException::class)
    fun editarRegrasNaoRecriaGrupoExcluido() = runBlocking {
        repository.updateGroup(group(), listOf(item()), emptyList())
    }

    @Test
    fun excluirGrupoPreservaUsoHistorico() = runBlocking {
        repository.saveGroup(group(), listOf(item()), emptyList())
        database.dailyUsageLogDao().recordUsageSeconds("2026-10-25", GROUP_ID, 125)
        repository.deleteGroup(GROUP_ID)
        assertNull(repository.getGroupForEdit(GROUP_ID))
        assertEquals(125, database.dailyUsageLogDao().findByDateAndGroup("2026-10-25", GROUP_ID)?.totalTimeUsedSeconds)
    }

    @Test
    fun editarGrupoPreservaIdentidadeECotaDaJanelaPersonalizada() = runBlocking {
        val config = com.senda.app.data.local.entity.LimitRules(dailyLimitMinutes = 90, sessionLimitMinutes = 15, cooldownMinutes = 3)
        val custom = schedule().copy(ruleId = "weekend", name = "Fim de semana", customRules = config)
        repository.saveGroup(group(), listOf(item()), listOf(custom))
        val scope = com.senda.app.core.UsageKey.forLimit(GROUP_ID, "weekend")
        database.dailyUsageLogDao().recordUsageSeconds("2026-10-24", scope, 125)
        val loaded = requireNotNull(repository.getGroupForEdit(GROUP_ID))
        repository.updateGroup(loaded.group.copy(sessionLimitMinutes = 1), loaded.items, loaded.schedules)
        val saved = requireNotNull(repository.getGroupForEdit(GROUP_ID))
        assertEquals("weekend", saved.schedules.single().ruleId)
        assertEquals(config, saved.schedules.single().customRules)
        assertEquals(125, database.dailyUsageLogDao().findByDateAndGroup("2026-10-24", scope)?.totalTimeUsedSeconds)
    }

    private fun group() = AppGroupEntity(
        id = GROUP_ID,
        name = "Redes Sociais"
    )

    private fun item() = GroupItemEntity(
        groupId = GROUP_ID,
        targetType = TargetType.APP,
        targetIdentifier = "com.instagram.android",
        displayName = "Instagram"
    )

    private fun schedule() = GroupScheduleEntity(
        groupId = GROUP_ID,
        scheduleType = ScheduleType.LIMITED,
        daysOfWeek = listOf(2, 3, 4, 5, 6),
        startHour = 9,
        startMinute = 0,
        endHour = 18,
        endMinute = 0
    )

    private companion object {
        const val GROUP_ID = "group-test"
    }
}
