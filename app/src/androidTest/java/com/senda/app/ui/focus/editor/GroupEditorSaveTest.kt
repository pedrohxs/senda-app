package com.senda.app.ui.focus.editor

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.TargetType
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.repository.GroupRepository
import com.senda.app.data.repository.InstalledAppsRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupEditorSaveTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val cooldowns = CooldownStore(context)
    private lateinit var database: SendaDatabase
    private lateinit var repository: GroupRepository
    private lateinit var viewModel: GroupEditorViewModel
    private var cooldownUntil = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, SendaDatabase::class.java).build()
        repository = GroupRepository(database, database.appGroupDao(), database.groupItemDao(),
            database.groupScheduleDao(), database.dailyUsageLogDao())
        repository.saveGroup(
            AppGroupEntity(id = GROUP, name = "Redes", isEnabled = false, temporaryDisableUntil = 123_456L),
            listOf(GroupItemEntity(groupId = GROUP, targetType = TargetType.APP,
                targetIdentifier = "test.app", displayName = "App")),
            listOf(GroupScheduleEntity(groupId = GROUP, scheduleType = ScheduleType.STRICT,
                daysOfWeek = listOf(2), startHour = 9, startMinute = 0, endHour = 10, endMinute = 0))
        )
        cooldowns.startCooldown(GROUP, 10)
        cooldownUntil = requireNotNull(cooldowns.cooldowns.first()[GROUP])
        instrumentation.runOnMainSync {
            viewModel = GroupEditorViewModel(GROUP, false, repository, InstalledAppsRepository(context))
        }
        withTimeout(10_000L) { viewModel.uiState.first { it.baseline != null && !it.isLoading } }
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        instrumentation.runOnMainSync { viewModel.viewModelScope.cancel() }
        cooldowns.clearCooldown(GROUP)
        database.close()
    }

    @Test
    fun salvarNomeECorMantemTempoDeEsperaEPreservaRegrasAtuais() = runBlocking {
        // A change made after the editor opened must survive a cosmetic save.
        database.groupItemDao().upsertAll(listOf(GroupItemEntity(groupId = GROUP,
            targetType = TargetType.WEBSITE, targetIdentifier = "example.com", displayName = "Site")))
        val before = requireNotNull(repository.getGroupForEdit(GROUP))
        instrumentation.runOnMainSync {
            viewModel.setName("Novo nome")
            viewModel.setColor("#FFFFFF")
        }
        assertFalse(viewModel.uiState.value.hasRuleChanges)
        saveAndAwait()

        assertEquals(cooldownUntil, cooldowns.cooldowns.first()[GROUP])
        assertEquals(before.copy(group = before.group.copy(name = "Novo nome", colorHex = "#FFFFFF")),
            repository.getGroupForEdit(GROUP))
    }

    @Test
    fun salvarLimitesMantemEsperaJaIniciadaEPausaMaisRecente() = runBlocking {
        repository.setTemporaryDisableUntil(GROUP, 987_654L)
        instrumentation.runOnMainSync {
            viewModel.setDailyLimit(60)
            viewModel.setCooldown(0)
        }
        assertTrue(viewModel.uiState.value.hasRuleChanges)
        saveAndAwait()

        val saved = requireNotNull(repository.getGroupForEdit(GROUP))
        assertEquals(cooldownUntil, cooldowns.cooldowns.first()[GROUP])
        assertEquals(60, saved.group.dailyLimitMinutes)
        assertEquals(0, saved.group.cooldownMinutes)
        assertEquals(987_654L, saved.group.temporaryDisableUntil)
        assertFalse(saved.group.isEnabled)
    }

    @Test
    fun salvarSemMudancasNaoReiniciaNemApagaEspera() = runBlocking {
        val before = repository.getGroupForEdit(GROUP)
        saveAndAwait()
        assertEquals(cooldownUntil, cooldowns.cooldowns.first()[GROUP])
        assertEquals(before, repository.getGroupForEdit(GROUP))
    }

    private suspend fun saveAndAwait() {
        instrumentation.runOnMainSync { viewModel.save() }
        val saved = withTimeout(10_000L) { viewModel.uiState.first { it.saved || it.errorMessage != null } }
        assertNull(saved.errorMessage)
        assertTrue(saved.saved)
    }

    private companion object { const val GROUP = "editor-save-test" }
}
