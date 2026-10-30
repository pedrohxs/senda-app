package com.senda.app.domain.policy

import java.time.Clock
import java.time.LocalDate

/**
 * Memoriza que o limite diário de um grupo já foi atingido hoje.
 *
 * O uso é gravado de forma assíncrona (durante a sessão e no fim
 * dela), então por alguns instantes a decisão pode ver o uso um pouco abaixo do
 * limite e oferecer um desafio em vez do bloqueio. Como o uso do dia só cresce,
 * uma vez atingido o limite o grupo não pode mais voltar a oferecer desafio —
 * isso fazia a tela de "limite diário" virar a tela de respiração e liberar o
 * app por engano.
 *
 * O registro é zerado na virada do dia ou quando o usuário aumenta o limite do
 * grupo no editor.
 */
class DailyLimitLatch(
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private var date: LocalDate? = null
    private val reachedAtLimitMinutes = mutableMapOf<String, Int>()

    fun markReached(groupId: String, limitMinutes: Int) {
        if (groupId.isBlank() || limitMinutes <= 0) return
        rotateIfNeeded()
        val previous = reachedAtLimitMinutes[groupId]
        reachedAtLimitMinutes[groupId] = if (previous == null) {
            limitMinutes
        } else {
            maxOf(previous, limitMinutes)
        }
    }

    fun isReached(groupId: String, limitMinutes: Int): Boolean {
        rotateIfNeeded()
        if (limitMinutes <= 0) {
            reachedAtLimitMinutes.remove(groupId)
            return false
        }
        val recorded = reachedAtLimitMinutes[groupId] ?: return false
        // O usuário subiu o limite no editor: o bloqueio antigo não vale mais.
        if (limitMinutes > recorded) {
            reachedAtLimitMinutes.remove(groupId)
            return false
        }
        return true
    }

    fun clear() {
        date = null
        reachedAtLimitMinutes.clear()
    }

    private fun rotateIfNeeded() {
        val today = LocalDate.now(clock)
        if (date != today) {
            date = today
            reachedAtLimitMinutes.clear()
        }
    }
}
