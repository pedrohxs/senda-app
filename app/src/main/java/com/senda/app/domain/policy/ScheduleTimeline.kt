package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import java.time.LocalDate

data class ScheduleSegment(val startMinute: Int, val endMinute: Int, val type: ScheduleType?) {
    val minutes: Int get() = endMinute - startMinute
}
data class ScheduleOverlap(val day: Int, val startMinute: Int, val endMinute: Int, val winner: ScheduleType)
data class ScheduleDayPreview(val day: Int, val segments: List<ScheduleSegment>, val overlaps: List<ScheduleOverlap>)

/** The preview uses the execution evaluator, including yesterday's overnight windows. */
object ScheduleTimeline {
    fun week(schedules: List<GroupScheduleEntity>): List<ScheduleDayPreview> = (1..7).map { day ->
        val date = LocalDate.of(2026, 1, 4).plusDays(day - 1L) // Sunday = 1.
        val types = Array(1440) { minute ->
            ScheduleEvaluator.currentSchedule(schedules, date.atStartOfDay().plusMinutes(minute.toLong()))
        }
        val conflicts = BooleanArray(1440) { minute ->
            ScheduleEvaluator.activeSchedules(schedules, date.atStartOfDay().plusMinutes(minute.toLong()))
                .map { it.scheduleType }.distinct().size > 1
        }
        val segments = mutableListOf<ScheduleSegment>()
        val overlaps = mutableListOf<ScheduleOverlap>()
        var start = 0
        for (minute in 1..1440) {
            if (minute == 1440 || types[minute] != types[start]) {
                segments += ScheduleSegment(start, minute, types[start])
                start = minute
            }
        }
        start = 0
        while (start < 1440) {
            if (!conflicts[start]) { start++; continue }
            var end = start + 1
            while (end < 1440 && conflicts[end] && types[end] == types[start]) end++
            overlaps += ScheduleOverlap(day, start, end, requireNotNull(types[start]))
            start = end
        }
        ScheduleDayPreview(day, segments, overlaps)
    }
}
