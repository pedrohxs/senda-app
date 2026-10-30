package com.senda.app.domain.policy

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.ScheduleType
import java.time.LocalDateTime

data class ResolvedGroupRule(val scopeKey: String, val group: AppGroupEntity, val scheduleType: ScheduleType?)

object GroupRuleResolver {
    fun nextChange(policy: GroupPolicy, now: LocalDateTime): LocalDateTime? {
        if (policy.schedules.isEmpty()) return null
        val current = resolve(policy, now)
        val candidates = mutableSetOf<LocalDateTime>()
        for (offset in -1L..8L) {
            val date = now.toLocalDate().plusDays(offset)
            val day = if (date.dayOfWeek.value == 7) 1 else date.dayOfWeek.value + 1
            policy.schedules.filter { day in it.daysOfWeek }.forEach { schedule ->
                val start = schedule.startHour * 60 + schedule.startMinute
                val end = schedule.endHour * 60 + schedule.endMinute
                if (start == end) {
                    candidates += date.atStartOfDay(); candidates += date.plusDays(1).atStartOfDay()
                } else {
                    candidates += date.atStartOfDay().plusMinutes(start.toLong())
                    candidates += date.plusDays(if (end < start) 1 else 0).atStartOfDay().plusMinutes(end.toLong())
                }
            }
        }
        return candidates.filter { it > now }.sorted().firstOrNull { candidate ->
            val next = resolve(policy, candidate)
            next.scopeKey != current.scopeKey || next.scheduleType != current.scheduleType
        }
    }

    fun resolve(policy: GroupPolicy, now: LocalDateTime): ResolvedGroupRule {
        val type = ScheduleEvaluator.currentSchedule(policy.schedules, now)
        val custom = if (type == ScheduleType.LIMITED) ScheduleEvaluator.activeSchedules(policy.schedules, now)
            .filter { it.scheduleType == ScheduleType.LIMITED && it.customRules != null }
            .minByOrNull { it.ruleId } else null
        return if (custom == null) ResolvedGroupRule(policy.group.id, policy.group, type)
        else ResolvedGroupRule(UsageKey.forLimit(policy.group.id, custom.ruleId), custom.customRules!!.applyTo(policy.group), type)
    }

    fun overlappingCustomLimits(schedules: List<com.senda.app.data.local.entity.GroupScheduleEntity>): Boolean {
        val custom = schedules.filter { it.scheduleType == ScheduleType.LIMITED && it.customRules != null }
        if (custom.size < 2) return false
        val start = java.time.LocalDate.of(2026, 1, 4)
        return (0..6).any { day -> (0 until 1440).any { minute ->
            ScheduleEvaluator.activeSchedules(custom, start.plusDays(day.toLong()).atStartOfDay().plusMinutes(minute.toLong())).size > 1
        } }
    }
}
