package com.senda.app.domain.policy

import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOverridePolicyTest {

    @Test
    fun bloqueioRigidoVenceSessaoAtiva() {
        assertTrue(
            SessionOverridePolicy.overridesActiveSession(
                block(reason = BlockReason.STRICT_SCHEDULE)
            )
        )
    }

    @Test
    fun limiteDiarioInterrompeSessao() {
        assertTrue(
            SessionOverridePolicy.overridesActiveSession(
                block(reason = BlockReason.DAILY_LIMIT)
            )
        )
    }

    @Test
    fun limiteDeAberturasNaoInterrompeSessao() {
        assertFalse(
            SessionOverridePolicy.overridesActiveSession(
                block(reason = BlockReason.OPEN_LIMIT)
            )
        )
    }

    @Test
    fun cooldownNaoInterrompeSessao() {
        assertFalse(
            SessionOverridePolicy.overridesActiveSession(
                block(reason = BlockReason.COOLDOWN)
            )
        )
    }

    @Test
    fun desafioNaoInterrompeSessao() {
        val challenge = InterventionDecision.Challenge(
            target = target(),
            groupId = "g1",
            groupName = "Grupo",
            interventionType = InterventionType.BREATHING,
            frictionSeconds = 10,
            sessionLimitMinutes = 5,
            cooldownMinutes = 10,
            countMode = SessionCountMode.ACTIVE_SCREEN_TIME,
            alternativeAppPackage = null,
            usageKey = "g1"
        )

        assertFalse(SessionOverridePolicy.overridesActiveSession(challenge))
    }

    @Test
    fun permitirNaoInterrompeSessao() {
        assertFalse(SessionOverridePolicy.overridesActiveSession(InterventionDecision.Allow))
    }

    private fun target() = InterventionTarget(
        packageName = "com.instagram.android",
        identifier = "com.instagram.android",
        displayName = "Instagram",
        isWebsite = false
    )

    private fun block(reason: BlockReason) = InterventionDecision.Block(
        target = target(),
        groupId = "g1",
        groupName = "Grupo",
        reason = reason
    )

    @Suppress("unused")
    private fun group() = AppGroupEntity(id = "g1", name = "Grupo")
}
