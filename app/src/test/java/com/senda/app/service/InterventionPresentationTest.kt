package com.senda.app.service

import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.domain.policy.InterventionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionPresentationTest {

    private val target = InterventionTarget(
        packageName = "com.instagram.android",
        identifier = "com.instagram.android",
        displayName = "Instagram",
        isWebsite = false
    )

    private fun block(reason: BlockReason, groupId: String = "group-1") =
        InterventionDecision.Block(
            target = target,
            groupId = groupId,
            groupName = "Redes",
            reason = reason
        )

    private fun challenge() = InterventionDecision.Challenge(
        target = target,
        groupId = "group-1",
        groupName = "Redes",
        interventionType = InterventionType.BREATHING,
        frictionSeconds = 10,
        sessionLimitMinutes = 5,
        cooldownMinutes = 10,
        countMode = SessionCountMode.ACTIVE_SCREEN_TIME,
        alternativeAppPackage = null,
        usageKey = "group-1"
    )

    @Test
    fun assinaturaIgnoraOTempoDeCooldown() {
        val first = block(BlockReason.COOLDOWN).copy(cooldownRemainingSeconds = 600)
        val later = block(BlockReason.COOLDOWN).copy(cooldownRemainingSeconds = 599)

        assertEquals(
            InterventionPresentation.blockSignature(first),
            InterventionPresentation.blockSignature(later)
        )
    }

    @Test
    fun motivoDiferenteMudaAAssinatura() {
        assertTrue(
            InterventionPresentation.blockSignature(block(BlockReason.COOLDOWN)) !=
                InterventionPresentation.blockSignature(block(BlockReason.DAILY_LIMIT))
        )
    }

    @Test
    fun mesmoBloqueioNaTelaNaoPrecisaSerRecriado() {
        assertTrue(
            InterventionPresentation.isSameBlockShowing(
                overlayTargetPackage = "com.instagram.android",
                overlayGroupId = "group-1",
                pending = block(BlockReason.STRICT_SCHEDULE),
                block = block(BlockReason.STRICT_SCHEDULE).copy(cooldownRemainingSeconds = 42)
            )
        )
    }

    @Test
    fun bloqueioComOutroMotivoPrecisaTrocarATela() {
        assertFalse(
            InterventionPresentation.isSameBlockShowing(
                overlayTargetPackage = "com.instagram.android",
                overlayGroupId = "group-1",
                pending = block(BlockReason.STRICT_SCHEDULE),
                block = block(BlockReason.DAILY_LIMIT)
            )
        )
    }

    @Test
    fun outroGrupoOuOutroAppNaoContamComoMesmoBloqueio() {
        assertFalse(
            InterventionPresentation.isSameBlockShowing(
                overlayTargetPackage = "com.instagram.android",
                overlayGroupId = "group-2",
                pending = block(BlockReason.DAILY_LIMIT),
                block = block(BlockReason.DAILY_LIMIT)
            )
        )
        assertFalse(
            InterventionPresentation.isSameBlockShowing(
                overlayTargetPackage = null,
                overlayGroupId = null,
                pending = null,
                block = block(BlockReason.DAILY_LIMIT)
            )
        )
    }

    @Test
    fun desafioPendenteNaoEhConfundidoComBloqueioVisivel() {
        assertFalse(
            InterventionPresentation.isSameBlockShowing(
                overlayTargetPackage = "com.instagram.android",
                overlayGroupId = "group-1",
                pending = challenge(),
                block = block(BlockReason.STRICT_SCHEDULE)
            )
        )
    }

    @Test
    fun janelaDeDeduplicacaoAgrupaEventosProximos() {
        val dedup = InterventionPresentation.DedupWindow(windowMillis = 1_200L)
        val key = InterventionPresentation.blockSignature(block(BlockReason.DAILY_LIMIT))

        assertFalse(dedup.isDuplicate(key, nowMillis = 10_000L))
        assertTrue(dedup.isDuplicate(key, nowMillis = 10_400L))
        assertTrue(dedup.isDuplicate(key, nowMillis = 11_100L))
        // Depois da janela o mesmo bloqueio é uma abertura nova.
        assertFalse(dedup.isDuplicate(key, nowMillis = 11_300L))
    }

    @Test
    fun outroBloqueioNaoEhDeduplicado() {
        val dedup = InterventionPresentation.DedupWindow(windowMillis = 1_200L)

        assertFalse(dedup.isDuplicate(InterventionPresentation.blockSignature(block(BlockReason.DAILY_LIMIT)), 1_000L))
        assertFalse(dedup.isDuplicate(InterventionPresentation.blockSignature(block(BlockReason.STRICT_SCHEDULE)), 1_100L))
    }

    @Test
    fun resetLiberaOMesmoBloqueioNaHora() {
        val dedup = InterventionPresentation.DedupWindow(windowMillis = 1_200L)
        val key = InterventionPresentation.blockSignature(block(BlockReason.DAILY_LIMIT))

        assertFalse(dedup.isDuplicate(key, 1_000L))
        assertTrue(dedup.isDuplicate(key, 1_100L))

        dedup.reset()

        assertFalse(dedup.isDuplicate(key, 1_200L))
    }
}
