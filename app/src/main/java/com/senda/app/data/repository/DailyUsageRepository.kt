package com.senda.app.data.repository

import com.senda.app.data.local.dao.DailyUsageLogDao
import java.time.Clock
import java.time.LocalDate

class DailyUsageRepository(
    private val dailyUsageLogDao: DailyUsageLogDao,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    suspend fun recordOpen(groupId: String) {
        dailyUsageLogDao.recordOpen(today(), groupId)
    }

    suspend fun recordImpulseAborted(groupId: String) {
        dailyUsageLogDao.recordImpulseAborted(today(), groupId)
    }

    suspend fun recordUsageSeconds(groupId: String, seconds: Int) {
        dailyUsageLogDao.recordUsageSeconds(today(), groupId, seconds)
    }

    suspend fun sessionBalance(
        date: String, groupId: String, usageKey: String, shared: Boolean, sessionId: String
    ) = dailyUsageLogDao.dailySessionBalance(date, groupId, usageKey, shared, sessionId)

    private fun today(): String = LocalDate.now(clock).toString()
}
