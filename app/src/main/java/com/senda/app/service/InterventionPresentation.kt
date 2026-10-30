package com.senda.app.service

import com.senda.app.domain.policy.InterventionDecision

/**
 * Regras de "isso já está na tela?" compartilhadas pelo serviço e pelo
 * controlador de sobreposição.
 *
 * Abrir um app monitorado gera vários eventos de acessibilidade (splash,
 * atividade principal, diálogos). Sem essas regras cada evento pedia um novo
 * bloqueio, que removia e recriava a janela — o overlay piscava na tela.
 */
object InterventionPresentation {

    fun blockSignature(block: InterventionDecision.Block): String =
        "block|${block.target.packageName}|${block.groupId}|${block.reason}"

    /** Descrição curta para o log de diagnóstico. */
    fun describe(decision: InterventionDecision): String = when (decision) {
        is InterventionDecision.Allow -> "permitido"
        is InterventionDecision.Challenge ->
            "desafio ${decision.target.packageName} ${decision.interventionType.name} " +
                "grupo=${decision.groupId}"
        is InterventionDecision.Block ->
            "bloqueio ${decision.target.packageName} ${decision.reason.name} " +
                "grupo=${decision.groupId}"
    }

    /**
     * O bloqueio pedido é exatamente o que já está visível?
     *
     * O motivo do bloqueio entra na comparação: quando a regra muda (por
     * exemplo, horário rígido vira limite diário) a tela precisa ser trocada.
     */
    fun isSameBlockShowing(
        overlayTargetPackage: String?,
        overlayGroupId: String?,
        pending: InterventionDecision?,
        block: InterventionDecision.Block
    ): Boolean {
        val pendingBlock = pending as? InterventionDecision.Block ?: return false
        return overlayTargetPackage == block.target.packageName &&
            overlayGroupId == block.groupId &&
            pendingBlock.reason == block.reason
    }

    /** Janela curta para agrupar os eventos da mesma abertura de app. */
    class DedupWindow(private val windowMillis: Long) {

        private var key: String? = null
        private var atMillis = 0L

        fun isDuplicate(key: String, nowMillis: Long): Boolean {
            if (key == this.key && nowMillis - atMillis < windowMillis) return true
            this.key = key
            this.atMillis = nowMillis
            return false
        }

        fun reset() {
            key = null
            atMillis = 0L
        }
    }
}
