package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NextWindowCalculatorTest {

    @Test
    fun proximaJanelaDoMesmoDia() {
        val windows = listOf(
            schedule(type = ScheduleType.STRICT, days = listOf(2), startHour = 22, endHour = 23)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Noturno",
            now = mondayAt(20, 0)
        )

        assertNull(plan.current)
        assertEquals(mondayAt(22, 0), plan.next?.start)
        assertEquals(mondayAt(23, 0), plan.next?.end)
        assertEquals("Noturno", plan.next?.groupName)
    }

    @Test
    fun janelaQueJaComecouApareceComoAtual() {
        // Segunda e terça: a próxima janela depois da de hoje é a de amanhã.
        val windows = listOf(
            schedule(type = ScheduleType.LIMITED, days = listOf(2, 3), startHour = 9, endHour = 18)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Trabalho",
            now = mondayAt(10, 30)
        )

        assertEquals(mondayAt(9, 0), plan.current?.start)
        assertEquals(mondayAt(18, 0), plan.current?.end)
        assertEquals(mondayAt(9, 0).plusDays(1), plan.next?.start)
    }

    @Test
    fun janelaQueCruzaMeiaNoiteEContadaNoDiaSeguinte() {
        val windows = listOf(
            schedule(type = ScheduleType.STRICT, days = listOf(2, 3), startHour = 22, endHour = 6)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Dormir",
            now = mondayAt(23, 0)
        )

        assertEquals(mondayAt(22, 0), plan.current?.start)
        assertEquals(mondayAt(22, 0).plusHours(8), plan.current?.end)
        assertTrue(plan.current?.crossesMidnight == true)
        // A próxima ocorrência é na terça à noite, não na madrugada de terça.
        assertEquals(mondayAt(22, 0).plusDays(1), plan.next?.start)
    }

    @Test
    fun madrugadaDeJanelaNoturnaAindaEstaAtiva() {
        // Domingo é o dia 1 no formato do Senda.
        val windows = listOf(
            schedule(type = ScheduleType.STRICT, days = listOf(1), startHour = 22, endHour = 6)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Dormir",
            now = mondayAt(2, 0)
        )

        // Domingo 22h -> segunda 6h.
        assertEquals(LocalDateTime.of(2024, 1, 7, 22, 0), plan.current?.start)
        assertEquals(mondayAt(6, 0), plan.current?.end)
        assertTrue(plan.current?.crossesMidnight == true)
    }

    @Test
    fun diaInteiroContaComoJanelaDeVinteEQuatroHoras() {
        val windows = listOf(
            schedule(type = ScheduleType.BREAK, days = listOf(2), startHour = 0, endHour = 0)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Folga",
            now = mondayAt(12, 0)
        )

        assertEquals(mondayAt(0, 0), plan.current?.start)
        assertEquals(mondayAt(0, 0).plusDays(1), plan.current?.end)
        assertEquals(mondayAt(0, 0).plusDays(7), plan.next?.start)
    }

    @Test
    fun diaSemJanelaPulaParaOProximoDiaMarcado() {
        val windows = listOf(
            schedule(type = ScheduleType.LIMITED, days = listOf(6), startHour = 8, endHour = 12)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Fim de semana",
            now = mondayAt(9, 0)
        )

        assertNull(plan.current)
        assertEquals(LocalDateTime.of(2024, 1, 12, 8, 0), plan.next?.start)
    }

    @Test
    fun semHorariosNaoExisteProximoBloqueio() {
        val plan = NextWindowCalculator.plan(
            schedules = emptyList(),
            groupId = "g1",
            groupName = "Sem horário",
            now = mondayAt(9, 0)
        )

        assertNull(plan.current)
        assertNull(plan.next)
    }

    @Test
    fun entreGruposVenceAJanelaQueComecaPrimeiro() {
        val groups = listOf(
            GroupWindows(
                groupId = "g1",
                groupName = "Trabalho",
                schedules = listOf(
                    schedule(type = ScheduleType.LIMITED, days = listOf(2), startHour = 14, endHour = 18)
                )
            ),
            GroupWindows(
                groupId = "g2",
                groupName = "Redes",
                schedules = listOf(
                    schedule(type = ScheduleType.STRICT, days = listOf(2), startHour = 11, endHour = 12)
                )
            )
        )

        val next = NextWindowCalculator.nextAcrossGroups(groups, mondayAt(9, 0))

        assertEquals("Redes", next?.groupName)
        assertEquals(mondayAt(11, 0), next?.start)
    }

    @Test
    fun janelaDuplicadaApareceUmaVezSo() {
        val duplicated = schedule(
            type = ScheduleType.LIMITED,
            days = listOf(2),
            startHour = 9,
            endHour = 18
        )

        val plan = NextWindowCalculator.plan(
            schedules = listOf(duplicated, duplicated.copy(ruleId = "outra")),
            groupId = "g1",
            groupName = "Trabalho",
            now = mondayAt(8, 0)
        )

        assertEquals(mondayAt(9, 0), plan.next?.start)
        assertFalse(plan.next?.crossesMidnight == true)
    }

    @Test
    fun tempoAteAProximaJanelaEBatido() {
        val windows = listOf(
            schedule(type = ScheduleType.STRICT, days = listOf(2), startHour = 22, endHour = 23)
        )

        val plan = NextWindowCalculator.plan(
            schedules = windows,
            groupId = "g1",
            groupName = "Noturno",
            now = mondayAt(21, 15)
        )

        assertEquals(45L, plan.next?.startsIn(mondayAt(21, 15))?.toMinutes())
    }

    private fun mondayAt(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2024, 1, 8, hour, minute)

    private fun schedule(
        type: ScheduleType,
        days: List<Int>,
        startHour: Int,
        endHour: Int
    ) = GroupScheduleEntity(
        groupId = "group-1",
        scheduleType = type,
        daysOfWeek = days,
        startHour = startHour,
        startMinute = 0,
        endHour = endHour,
        endMinute = 0
    )
}
