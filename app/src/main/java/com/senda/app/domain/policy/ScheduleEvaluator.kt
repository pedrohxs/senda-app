package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import java.time.LocalDateTime

object ScheduleEvaluator {

    fun currentSchedule(
        schedules: List<GroupScheduleEntity>,
        dateTime: LocalDateTime
    ): ScheduleType? {
        val activeSchedules = activeSchedules(schedules, dateTime)

        return when {
            activeSchedules.any { it.scheduleType == ScheduleType.BREAK } -> ScheduleType.BREAK
            activeSchedules.any { it.scheduleType == ScheduleType.STRICT } -> ScheduleType.STRICT
            activeSchedules.any { it.scheduleType == ScheduleType.LIMITED } -> ScheduleType.LIMITED
            else -> null
        }
    }

    fun activeSchedules(schedules: List<GroupScheduleEntity>, dateTime: LocalDateTime): List<GroupScheduleEntity> {
        val day = sendaDayOfWeek(dateTime)
        val minute = dateTime.hour * 60 + dateTime.minute
        return schedules.filter { isActive(it, day, minute) }
    }

    fun sendaDayOfWeek(dateTime: LocalDateTime): Int {
        val isoDay = dateTime.dayOfWeek.value
        return if (isoDay == 7) 1 else isoDay + 1
    }

    private fun isActive(
        schedule: GroupScheduleEntity,
        day: Int,
        minuteOfDay: Int
    ): Boolean {
        if (schedule.daysOfWeek.isEmpty()) return false

        val start = schedule.startHour * 60 + schedule.startMinute
        val end = schedule.endHour * 60 + schedule.endMinute

        if (start == end) return day in schedule.daysOfWeek

        if (start < end) {
            return day in schedule.daysOfWeek && minuteOfDay >= start && minuteOfDay < end
        }

        val previousDay = if (day == 1) 7 else day - 1
        val startedYesterday = previousDay in schedule.daysOfWeek && minuteOfDay < end
        val startedToday = day in schedule.daysOfWeek && minuteOfDay >= start
        return startedYesterday || startedToday
    }
}
