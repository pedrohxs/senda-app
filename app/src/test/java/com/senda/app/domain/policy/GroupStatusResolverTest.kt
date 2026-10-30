package com.senda.app.domain.policy

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.TargetType

class GroupStatusResolverTest {

    private val resolver = GroupStatusResolver()
    private val now = LocalDateTime.of(2024, 1, 8, 10, 0)

    @Test
    fun semAgendamentoMostraLivre() {
        val policy = policy(schedules = emptyList())

        assertEquals(GroupStatus.FREE, resolver.resolve(policy, emptyMap(), now).status)
    }

    @Test
    fun janelaLivreMostraFree() {
        val policy = policy(schedules = listOf(schedule(ScheduleType.BREAK)))

        assertEquals(GroupStatus.FREE, resolver.resolve(policy, emptyMap(), now).status)
    }

    @Test
    fun bloqueioRigidoMostraStrict() {
        val policy = policy(schedules = listOf(schedule(ScheduleType.STRICT)))

        val result = resolver.resolve(policy, emptyMap(), now)

        assertEquals(GroupStatus.STRICT, result.status)
        assertEquals(BlockReason.STRICT_SCHEDULE, result.blockReason)
    }

    @Test
    fun limiteDiarioMostraBlocked() {
        val policy = policy(
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = usage(totalTimeUsedSeconds = 30 * 60)
        )

        val result = resolver.resolve(policy, emptyMap(), now)

        assertEquals(GroupStatus.BLOCKED, result.status)
        assertEquals(BlockReason.DAILY_LIMIT, result.blockReason)
    }

    @Test
    fun cooldownMostraBlocked() {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val policy = policy(schedules = listOf(schedule(ScheduleType.LIMITED)))

        val result = resolver.resolve(
            policy = policy,
            cooldowns = mapOf("group-1" to nowMillis + 60_000L),
            now = now
        )

        assertEquals(GroupStatus.BLOCKED, result.status)
        assertEquals(BlockReason.COOLDOWN, result.blockReason)
    }

    @Test
    fun somaDeUsoDeAppsIndependentesNaoBloqueiaOGrupoInteiro() {
        assertEquals(GroupStatus.LIMITED, resolver.resolve(individualPolicy(20 * 60, 20 * 60), emptyMap(), now).status)
    }

    @Test
    fun apenasUmAppNoLimiteMostraBloqueioParcial() {
        assertEquals(GroupStatus.PARTIAL, resolver.resolve(individualPolicy(30 * 60, 5 * 60), emptyMap(), now).status)
    }

    @Test
    fun todosOsAppsIndividuaisNoLimiteMostramGrupoBloqueado() {
        assertEquals(GroupStatus.BLOCKED, resolver.resolve(individualPolicy(30 * 60, 30 * 60), emptyMap(), now).status)
    }

    private fun individualPolicy(first: Int, second: Int) = GroupPolicy(
        group = group.copy(sharedLimitAcrossApps = false),
        items = listOf("app.a", "app.b").map {
            GroupItemEntity(groupId = group.id, targetType = TargetType.APP, targetIdentifier = it, displayName = it)
        },
        schedules = listOf(schedule(ScheduleType.LIMITED)), usage = usage(first + second),
        usageByTarget = mapOf("app.a" to usage(first), "app.b" to usage(second))
    )

    private fun policy(
        schedules: List<GroupScheduleEntity>,
        usage: DailyUsageLogEntity? = null
    ) = GroupPolicy(
        group = group,
        items = emptyList(),
        schedules = schedules,
        usage = usage
    )

    private fun schedule(type: ScheduleType) = GroupScheduleEntity(
        groupId = "group-1",
        scheduleType = type,
        daysOfWeek = listOf(2),
        startHour = 9,
        startMinute = 0,
        endHour = 18,
        endMinute = 0
    )

    private fun usage(totalTimeUsedSeconds: Int) = DailyUsageLogEntity(
        date = "2024-01-08",
        groupId = "group-1",
        totalTimeUsedMinutes = totalTimeUsedSeconds / 60,
        totalTimeUsedSeconds = totalTimeUsedSeconds
    )

    private val group = AppGroupEntity(
        id = "group-1",
        name = "Redes Sociais",
        dailyLimitMinutes = 30,
        maxDailyOpens = 15
    )
}
