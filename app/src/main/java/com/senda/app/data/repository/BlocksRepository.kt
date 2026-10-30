package com.senda.app.data.repository

import com.senda.app.data.local.dao.AppGroupDao
import com.senda.app.data.local.dao.GroupItemDao
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupItemEntity
import kotlinx.coroutines.flow.Flow

class BlocksRepository(
    private val appGroupDao: AppGroupDao,
    private val groupItemDao: GroupItemDao
) {

    fun observeGroups(): Flow<List<AppGroupEntity>> = appGroupDao.observeEnabledGroups()

    fun observeItems(groupId: String): Flow<List<GroupItemEntity>> =
        groupItemDao.observeByGroup(groupId)

    suspend fun addItem(item: GroupItemEntity) {
        groupItemDao.upsert(item)
    }

    suspend fun deleteItem(item: GroupItemEntity) {
        groupItemDao.delete(item)
    }
}
