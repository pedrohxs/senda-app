package com.senda.app.domain.policy

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailyRolloverRegressionTest {
    @Test fun unlimitedClearsPreviouslyReachedLimit() {
        val latch = DailyLimitLatch()
        latch.markReached("g", 30)
        assertFalse(latch.isReached("g", 0))
        assertFalse(latch.isReached("g", 30))
    }

    @Test fun oldSnapshotCannotCarryYesterdayUsageIntoToday() {
        val group = AppGroupEntity(id = "g", name = "Group")
        val yesterday = DailyUsageLogEntity(date = "2026-10-28", groupId = "g", totalTimeUsedSeconds = 1800, totalOpens = 15)
        val policy = GroupPolicy(group, emptyList(), emptyList(), yesterday, usageLogs = listOf(yesterday))
        assertNull(policy.scoped("g", group, LocalDate.parse("2026-10-29")).usage)
        assertEquals(1800, policy.scoped("g", group, LocalDate.parse("2026-10-28")).usage?.totalTimeUsedSeconds)
    }

    @Test fun currentDayUsageIsPreserved() {
        val group = AppGroupEntity(id = "g", name = "Group")
        val today = DailyUsageLogEntity(date = "2026-10-29", groupId = "g", totalTimeUsedSeconds = 60)
        val policy = GroupPolicy(group, emptyList(), emptyList(), today)
        assertEquals(60, policy.scoped("g", group, LocalDate.parse("2026-10-29")).usage?.totalTimeUsedSeconds)
    }
}
