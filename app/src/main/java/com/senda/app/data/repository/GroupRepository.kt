package com.senda.app.data.repository

import androidx.room.withTransaction
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.local.dao.AppGroupDao
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.local.dao.GroupItemDao
import com.senda.app.data.local.dao.GroupScheduleDao
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity

data class GroupEditorData(
    val group: AppGroupEntity,
    val items: List<GroupItemEntity>,
    val schedules: List<GroupScheduleEntity>
)

class GroupRepository(
    private val database: SendaDatabase,
    private val appGroupDao: AppGroupDao,
    private val groupItemDao: GroupItemDao,
    private val groupScheduleDao: GroupScheduleDao,
    private val dailyUsageLogDao: DailyUsageLogDao
) {
    private val reportHistory = ReportHistoryRepository(database)

    suspend fun hasGroups(): Boolean = appGroupDao.hasGroups()

    suspend fun getGroupForEdit(groupId: String): GroupEditorData? {
        val group = appGroupDao.findById(groupId) ?: return null
        return GroupEditorData(
            group = group,
            items = groupItemDao.getByGroup(groupId),
            schedules = groupScheduleDao.getForGroup(groupId)
        )
    }

    suspend fun saveGroup(
        group: AppGroupEntity,
        items: List<GroupItemEntity>,
        schedules: List<GroupScheduleEntity>
    ) {
        database.withTransaction {
            replaceContents(group, items, schedules)
            reportHistory.captureGoals()
        }
    }

    suspend fun updateAppearance(groupId: String, name: String, colorHex: String) {
        database.withTransaction {
            check(appGroupDao.updateAppearance(groupId, name, colorHex) == 1) { "Grupo não encontrado." }
            reportHistory.captureGoals()
        }
    }

    suspend fun updateGroup(
        group: AppGroupEntity,
        items: List<GroupItemEntity>,
        schedules: List<GroupScheduleEntity>
    ) {
        database.withTransaction {
            val current = checkNotNull(appGroupDao.findById(group.id)) { "Grupo não encontrado." }
            // Pausa e ativação não pertencem ao formulário de regras. Preservar o
            // valor atual evita sobrescrever ações aplicadas depois de abrir o editor.
            replaceContents(group.copy(
                isEnabled = current.isEnabled,
                temporaryDisableUntil = current.temporaryDisableUntil
            ), items, schedules)
            reportHistory.captureGoals()
        }
    }

    private suspend fun replaceContents(
        group: AppGroupEntity,
        items: List<GroupItemEntity>,
        schedules: List<GroupScheduleEntity>
    ) {
        check(schedules.all { it.customRules == null || (it.scheduleType == com.senda.app.data.local.entity.ScheduleType.LIMITED && it.customRules.isValid) }) {
            "Configurações inválidas de limite personalizado."
        }
        check(!com.senda.app.domain.policy.GroupRuleResolver.overlappingCustomLimits(schedules)) {
            "Dois limites personalizados se sobrepõem."
        }
        appGroupDao.upsert(group)
        groupItemDao.deleteByGroup(group.id)
        if (items.isNotEmpty()) {
            groupItemDao.upsertAll(items.map { it.copy(id = 0, groupId = group.id) })
        }
        groupScheduleDao.deleteByGroup(group.id)
        if (schedules.isNotEmpty()) {
            groupScheduleDao.upsertAll(schedules.map { it.copy(id = 0, groupId = group.id) })
        }
    }

    suspend fun setTemporaryDisableUntil(groupId: String, untilMillis: Long?) {
        appGroupDao.setTemporaryDisableUntil(groupId, untilMillis)
    }

    suspend fun deleteGroup(groupId: String) {
        database.withTransaction {
            appGroupDao.deleteById(groupId)
            reportHistory.captureGoals()
        }
    }
}
