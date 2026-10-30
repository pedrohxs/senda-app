package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleTimelineTest {
    @Test fun foraDasJanelasOResultadoEhLivre() {
        ScheduleTimeline.week(emptyList()).forEach {
            assertEquals(listOf(ScheduleSegment(0, 1440, null)), it.segments)
            assertTrue(it.overlaps.isEmpty())
        }
    }
    @Test fun janelaNoturnaDeSegundaContinuaNaTercaSemBloquearMadrugadaDeSegunda() {
        val week = ScheduleTimeline.week(listOf(window(2, 22, 6, ScheduleType.STRICT)))
        assertEquals(null, typeAt(week, 2, 2 * 60))
        assertEquals(ScheduleType.STRICT, typeAt(week, 2, 23 * 60))
        assertEquals(ScheduleType.STRICT, typeAt(week, 3, 2 * 60))
        assertEquals(null, typeAt(week, 3, 6 * 60))
    }
    @Test fun domingoANoiteContinuaNaSegundaDaSemanaSeguinte() {
        val week = ScheduleTimeline.week(listOf(window(1, 22, 6, ScheduleType.STRICT)))
        assertEquals(ScheduleType.STRICT, typeAt(week, 2, 2 * 60))
        assertEquals(null, typeAt(week, 1, 2 * 60))
    }
    @Test fun horariosIguaisSignificamDiaInteiroApenasNosDiasSelecionados() {
        val week = ScheduleTimeline.week(listOf(window(2, 9, 9, ScheduleType.STRICT)))
        assertEquals(listOf(ScheduleSegment(0, 1440, ScheduleType.STRICT)), week.first { it.day == 2 }.segments)
        assertEquals(null, typeAt(week, 3, 1))
    }
    @Test fun sobreposicaoMostraIntervaloERegraQueRealmentePrevalece() {
        val day = ScheduleTimeline.week(listOf(window(2, 9, 18, ScheduleType.STRICT), window(2, 12, 13, ScheduleType.BREAK)))
            .first { it.day == 2 }
        assertEquals(listOf(ScheduleOverlap(2, 720, 780, ScheduleType.BREAK)), day.overlaps)
    }
    @Test fun intervalosQueApenasSeEncostamNaoGeramConflito() {
        val week = ScheduleTimeline.week(listOf(window(2, 9, 12, ScheduleType.STRICT), window(2, 12, 13, ScheduleType.BREAK)))
        assertTrue(week.flatMap { it.overlaps }.isEmpty())
    }
    @Test fun graficoConcordaComExecutorEmTodosOsMinutosDaSemana() {
        val schedules = listOf(window(2, 22, 6, ScheduleType.STRICT), window(3, 4, 8, ScheduleType.BREAK),
            window(1, 0, 0, ScheduleType.STRICT), window(1, 13, 14, ScheduleType.LIMITED))
        val week = ScheduleTimeline.week(schedules)
        week.forEach { day ->
            assertEquals(1440, day.segments.sumOf { it.minutes })
            for (minute in 0 until 1440) {
                val time = LocalDate.of(2026, 1, 4).plusDays(day.day - 1L).atStartOfDay().plusMinutes(minute.toLong())
                assertEquals(ScheduleEvaluator.currentSchedule(schedules, time), typeAt(week, day.day, minute))
            }
        }
    }
    private fun typeAt(week: List<ScheduleDayPreview>, day: Int, minute: Int) = week.first { it.day == day }.segments
        .first { minute >= it.startMinute && minute < it.endMinute }.type
    private fun window(day: Int, from: Int, to: Int, type: ScheduleType) = GroupScheduleEntity(
        groupId = "g", scheduleType = type, daysOfWeek = listOf(day), startHour = from, startMinute = 0, endHour = to, endMinute = 0)
}
