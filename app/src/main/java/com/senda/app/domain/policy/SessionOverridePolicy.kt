package com.senda.app.domain.policy

/**
 * Decide quando uma decisão de política deve valer mesmo com uma sessão ativa.
 *
 * Uma sessão libera o app por alguns minutos, mas duas regras são compromissos
 * do usuário e vencem a sessão:
 * - bloqueio rígido agendado (regra de horário);
 * - limite diário de uso.
 */
object SessionOverridePolicy {

    fun overridesActiveSession(decision: InterventionDecision): Boolean =
        decision is InterventionDecision.Block &&
            decision.reason in OVERRIDING_REASONS

    private val OVERRIDING_REASONS = setOf(
        BlockReason.STRICT_SCHEDULE,
        BlockReason.DAILY_LIMIT
    )
}
