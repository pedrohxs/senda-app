package com.senda.app.data.repository

import com.senda.app.data.local.dao.AppGroupDao
import com.senda.app.data.local.dao.DailyUsageLogDao
import com.senda.app.data.local.dao.GroupItemDao
import com.senda.app.data.local.dao.GroupScheduleDao
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.domain.policy.PolicySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class FocoPolicyRepository(
    private val appGroupDao: AppGroupDao,
    private val groupItemDao: GroupItemDao,
    private val groupScheduleDao: GroupScheduleDao,
    private val dailyUsageLogDao: DailyUsageLogDao,
    private val cooldownStore: CooldownStore,
    private val protectionStore: ProtectionStore
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSnapshot(): Flow<PolicySnapshot> = todayFlow().flatMapLatest { date ->
        val policies = combine(
            appGroupDao.observeEnabledGroups(),
            groupItemDao.observeActiveItems(),
            groupScheduleDao.observeAll(),
            dailyUsageLogDao.observeByDate(date.toString()),
            cooldownStore.cooldowns
        ) { groups, items, schedules, usage, cooldowns ->
            PolicySnapshot.build(
                groups = groups,
                items = items,
                schedules = schedules,
                usage = usage,
                cooldowns = cooldowns
            )
        }

        policies.combine(protectionStore.state) { snapshot, protection ->
            snapshot.copy(
                clockTamperRemainingSeconds = protection.clockTamperRemainingSeconds
            )
        }
    }

    private fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now()
            emit(now.toLocalDate())

            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val delayMillis = Duration.between(now, nextMidnight)
                .toMillis()
                .coerceAtLeast(1_000L)
            delay(delayMillis)
        }
    }
}
