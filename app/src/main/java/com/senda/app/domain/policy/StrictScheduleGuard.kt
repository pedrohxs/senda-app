package com.senda.app.domain.policy

import com.senda.app.data.local.entity.ScheduleType
import java.time.LocalDateTime

/**
 * Detecta se alguma das políticas já entrou em bloqueio rígido.
 *
 * Usado pelo serviço de primeiro plano para interromper uma sessão ativa sem
 * depender de um novo evento de janela — o usuário pode continuar no app e o
 * horário virar no meio da sessão.
 */
object StrictScheduleGuard {

    fun isActive(
        policies: List<GroupPolicy>,
        now: LocalDateTime
    ): Boolean = policies.any { policy ->
        ScheduleEvaluator.currentSchedule(policy.schedules, now) == ScheduleType.STRICT
    }
}
