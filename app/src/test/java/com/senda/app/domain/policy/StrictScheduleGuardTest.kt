package com.senda.app.domain.policy

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class StrictScheduleGuardTest {

    private val now = LocalDateTime.of(2026, 10, 20, 22, 45)

    @Test
    fun detectaBloqueioRigidoNoHorarioDeInicio() {
        assertTrue(StrictScheduleGuard.isActive(listOf(policy(ScheduleType.STRICT)), now))
    }

    @Test
    fun naoDetectaAntesDoHorario() {
        val antes = LocalDateTime.of(2026, 10, 20, 22, 44)

        assertFalse(StrictScheduleGuard.isActive(listOf(policy(ScheduleType.STRICT)), antes))
    }

    @Test
    fun naoDetectaDepoisDoFim() {
        val depois = LocalDateTime.of(2026, 10, 20, 23, 1)

        assertFalse(StrictScheduleGuard.isActive(listOf(policy(ScheduleType.STRICT)), depois))
    }

    @Test
    fun agendamentoDePausaNaoInterrompeSessao() {
        assertFalse(StrictScheduleGuard.isActive(listOf(policy(ScheduleType.BREAK)), now))
    }

    @Test
    fun semAgendamentosNaoInterrompe() {
        assertFalse(StrictScheduleGuard.isActive(emptyList(), now))
    }

    @Test
    fun janelaQueComecouOntemContinuaValendo() {
        val schedule = GroupScheduleEntity(
            groupId = "g1",
            scheduleType = ScheduleType.STRICT,
            daysOfWeek = listOf(ScheduleEvaluator.sendaDayOfWeek(now.minusDays(1))),
            startHour = 23,
            startMinute = 0,
            endHour = 1,
            endMinute = 0
        )
        val madrugada = LocalDateTime.of(2026, 10, 20, 0, 30)

        assertTrue(
            StrictScheduleGuard.isActive(
                listOf(policyWith(listOf(schedule))),
                madrugada
            )
        )
    }

    private fun policy(type: ScheduleType): GroupPolicy {
        val schedule = GroupScheduleEntity(
            groupId = "g1",
            scheduleType = type,
            daysOfWeek = listOf(ScheduleEvaluator.sendaDayOfWeek(now)),
            startHour = 22,
            startMinute = 45,
            endHour = 23,
            endMinute = 0
        )
        return policyWith(listOf(schedule))
    }

    private fun policyWith(schedules: List<GroupScheduleEntity>) = GroupPolicy(
        group = AppGroupEntity(id = "g1", name = "Teste"),
        items = emptyList(),
        schedules = schedules,
        usage = null
    )
}
