package com.senda.app.domain.policy

import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.ScheduleType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Janela de horário que já começou ou que vai começar. */
data class UpcomingWindow(
    val groupId: String,
    val groupName: String,
    val type: ScheduleType,
    val name: String,
    val start: LocalDateTime,
    val end: LocalDateTime
) {
    val crossesMidnight: Boolean get() = end.toLocalDate() != start.toLocalDate()

    fun startsIn(now: LocalDateTime): Duration = Duration.between(now, start)

    fun endsIn(now: LocalDateTime): Duration = Duration.between(now, end)
}

data class NextWindowPlan(
    val current: UpcomingWindow?,
    val next: UpcomingWindow?
)

/** Grupo com os horários que valem para ele. */
data class GroupWindows(
    val groupId: String,
    val groupName: String,
    val schedules: List<GroupScheduleEntity>
)

/**
 * Calcula a próxima janela de horário a partir das regras que o serviço de
 * verdade usa ([ScheduleEvaluator]) — inclusive janelas que atravessam a
 * meia-noite, dia inteiro e dias da semana no formato do Senda.
 *
 * Nada aqui é sorte: o app só anuncia o próximo bloqueio depois de procurar em
 * oito dias de ocorrências.
 */
object NextWindowCalculator {

    const val LOOK_AHEAD_DAYS: Long = 8L

    fun plan(
        schedules: List<GroupScheduleEntity>,
        groupId: String,
        groupName: String,
        now: LocalDateTime,
        lookAheadDays: Long = LOOK_AHEAD_DAYS
    ): NextWindowPlan {
        if (schedules.isEmpty()) return NextWindowPlan(current = null, next = null)

        val occurrences = occurrences(schedules, groupId, groupName, now, lookAheadDays)
        val currentType = ScheduleEvaluator.currentSchedule(schedules, now)
        val current = currentType?.let { type ->
            occurrences
                .filter { it.start <= now && it.end > now && it.type == type }
                .minByOrNull { it.start }
        }
        val next = occurrences.filter { it.start > now }.minByOrNull { it.start }
        return NextWindowPlan(current = current, next = next)
    }

    /** Primeira janela futura entre todos os grupos, ou null se nada estiver agendado. */
    fun nextAcrossGroups(
        groups: List<GroupWindows>,
        now: LocalDateTime,
        lookAheadDays: Long = LOOK_AHEAD_DAYS
    ): UpcomingWindow? = groups
        .mapNotNull { group ->
            plan(
                schedules = group.schedules,
                groupId = group.groupId,
                groupName = group.groupName,
                now = now,
                lookAheadDays = lookAheadDays
            ).next
        }
        .minByOrNull { it.start }

    private fun occurrences(
        schedules: List<GroupScheduleEntity>,
        groupId: String,
        groupName: String,
        now: LocalDateTime,
        lookAheadDays: Long
    ): List<UpcomingWindow> {
        val firstDay = now.toLocalDate().minusDays(1)
        val lastDay = now.toLocalDate().plusDays(lookAheadDays)
        val windows = mutableListOf<UpcomingWindow>()
        var date = firstDay
        while (!date.isAfter(lastDay)) {
            schedules.forEach { schedule ->
                occurrence(schedule, date)?.let { (start, end) ->
                    windows += UpcomingWindow(
                        groupId = groupId,
                        groupName = groupName,
                        type = schedule.scheduleType,
                        name = schedule.name,
                        start = start,
                        end = end
                    )
                }
            }
            date = date.plusDays(1)
        }
        return windows
            .filter { it.end.isAfter(now) }
            .distinctBy { listOf(it.start, it.end, it.type) }
            .sortedBy { it.start }
    }

    /**
     * Ocorrência que começa em [date], ou null quando o dia não está marcado.
     * Início igual ao fim significa o dia inteiro, como no avaliador.
     */
    internal fun occurrence(
        schedule: GroupScheduleEntity,
        date: LocalDate
    ): Pair<LocalDateTime, LocalDateTime>? {
        val day = ScheduleEvaluator.sendaDayOfWeek(date.atStartOfDay())
        if (day !in schedule.daysOfWeek) return null

        val startMinute = schedule.startHour * 60 + schedule.startMinute
        val endMinute = schedule.endHour * 60 + schedule.endMinute
        val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
        val end = when {
            startMinute == endMinute -> date.plusDays(1).atStartOfDay()
            endMinute > startMinute -> date.atStartOfDay().plusMinutes(endMinute.toLong())
            else -> date.plusDays(1).atStartOfDay().plusMinutes(endMinute.toLong())
        }
        return start to end
    }
}
