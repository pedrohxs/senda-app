package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ScheduleEvaluatorTest {

    @Test
    fun janelaNormalDentroDoHorario() {
        val schedule = schedule(
            type = ScheduleType.LIMITED,
            days = listOf(2),
            startHour = 9,
            endHour = 18
        )

        assertEquals(
            ScheduleType.LIMITED,
            ScheduleEvaluator.currentSchedule(listOf(schedule), mondayAt(10, 0))
        )
        assertNull(ScheduleEvaluator.currentSchedule(listOf(schedule), mondayAt(8, 59)))
        assertNull(ScheduleEvaluator.currentSchedule(listOf(schedule), mondayAt(18, 0)))
    }

    @Test
    fun janelaQueCruzaMeiaNoite() {
        val schedule = schedule(
            type = ScheduleType.STRICT,
            days = listOf(7),
            startHour = 22,
            endHour = 6
        )

        assertEquals(
            ScheduleType.STRICT,
            ScheduleEvaluator.currentSchedule(
                listOf(schedule),
                LocalDateTime.of(2024, 1, 6, 23, 0)
            )
        )
        assertEquals(
            ScheduleType.STRICT,
            ScheduleEvaluator.currentSchedule(
                listOf(schedule),
                LocalDateTime.of(2024, 1, 7, 2, 0)
            )
        )
        assertNull(
            ScheduleEvaluator.currentSchedule(
                listOf(schedule),
                LocalDateTime.of(2024, 1, 7, 7, 0)
            )
        )
    }

    @Test
    fun janelaLivreTemPrecedenciaSobreBloqueioRigido() {
        val strict = schedule(
            type = ScheduleType.STRICT,
            days = listOf(2),
            startHour = 9,
            endHour = 18
        )
        val free = schedule(
            type = ScheduleType.BREAK,
            days = listOf(2),
            startHour = 12,
            endHour = 13
        )

        assertEquals(
            ScheduleType.BREAK,
            ScheduleEvaluator.currentSchedule(listOf(strict, free), mondayAt(12, 30))
        )
        assertEquals(
            ScheduleType.STRICT,
            ScheduleEvaluator.currentSchedule(listOf(strict, free), mondayAt(10, 0))
        )
    }

    @Test
    fun domingoCorrespondeAoDiaUm() {
        assertEquals(1, ScheduleEvaluator.sendaDayOfWeek(LocalDateTime.of(2024, 1, 7, 10, 0)))
        assertEquals(2, ScheduleEvaluator.sendaDayOfWeek(mondayAt(10, 0)))
        assertEquals(7, ScheduleEvaluator.sendaDayOfWeek(LocalDateTime.of(2024, 1, 6, 10, 0)))
    }

    @Test
    fun inicioIgualAoFimRepresentaDiaInteiro() {
        val schedule = schedule(
            type = ScheduleType.BREAK,
            days = listOf(2),
            startHour = 0,
            endHour = 0
        )

        assertEquals(
            ScheduleType.BREAK,
            ScheduleEvaluator.currentSchedule(listOf(schedule), mondayAt(23, 59))
        )
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
