package com.senda.app.domain.policy

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CustomLimitPolicyTest {
    private val zone = ZoneId.of("UTC")
    private val saturday = LocalDateTime.of(2026, 10, 24, 10, 0)
    private val engine = InterventionEngine(Clock.fixed(saturday.atZone(zone).toInstant(), zone))
    private val group = AppGroupEntity(id = "g", name = "Redes")
    private val config = LimitRules(dailyLimitMinutes = 90, sessionLimitMinutes = 20, cooldownMinutes = 3,
        maxDailyOpens = 5, interventionType = InterventionType.MATH, mathDifficulty = MathDifficulty.HARD, mathEquationCount = 6)
    private val window = GroupScheduleEntity(groupId = "g", scheduleType = ScheduleType.LIMITED, daysOfWeek = listOf(7, 1),
        startHour = 0, startMinute = 0, endHour = 0, endMinute = 0, ruleId = "weekend", name = "Fim de semana", customRules = config)
    private val scope = UsageKey.forLimit("g", "weekend")

    @Test fun diasNormaisUsamPadroesEFinalDeSemanaUsaConfiguracaoPropria() {
        val normal = engine.evaluateApp("app.a", snapshot(), saturday.minusDays(1)) as InterventionDecision.Challenge
        assertEquals(5, normal.sessionLimitMinutes); assertEquals(10, normal.cooldownMinutes)
        assertEquals("g", normal.usageKey)
        val custom = engine.evaluateApp("app.a", snapshot(), saturday) as InterventionDecision.Challenge
        assertEquals(20, custom.sessionLimitMinutes); assertEquals(3, custom.cooldownMinutes)
        assertEquals(InterventionType.MATH, custom.interventionType)
        assertEquals(MathDifficulty.HARD, custom.mathDifficulty); assertEquals(6, custom.mathEquationCount)
        assertEquals(scope, custom.usageKey)
    }

    @Test fun editarPadroesNaoReescreveConfiguracoesProprias() {
        val decision = engine.evaluateApp("app.a", snapshot(base = group.copy(sessionLimitMinutes = 1, cooldownMinutes = 90)), saturday) as InterventionDecision.Challenge
        assertEquals(20, decision.sessionLimitMinutes); assertEquals(3, decision.cooldownMinutes)
    }

    @Test fun janelaSemPersonalizacaoContinuaHerdandoPadroes() {
        val decision = engine.evaluateApp("app.a", snapshot(base = group.copy(sessionLimitMinutes = 2), schedules = listOf(window.copy(customRules = null))), saturday) as InterventionDecision.Challenge
        assertEquals(2, decision.sessionLimitMinutes); assertEquals("g", decision.usageKey)
    }

    @Test fun usoDoLimiteGeralNaoConsomeCotaPersonalizada() {
        assertTrue(engine.evaluateApp("app.a", snapshot(logs = listOf(log("g", 1800))), saturday) is InterventionDecision.Challenge)
    }

    @Test fun cotaPersonalizadaBloqueiaSomenteSuaJanela() {
        val data = snapshot(logs = listOf(log(scope, 5400)))
        val custom = engine.evaluateApp("app.a", data, saturday) as InterventionDecision.Block
        assertEquals(BlockReason.DAILY_LIMIT, custom.reason); assertEquals(scope, custom.usageKey)
        assertEquals(90, custom.dailyLimitMinutes)
        assertTrue(engine.evaluateApp("app.a", data, saturday.minusDays(1)) is InterventionDecision.Challenge)
    }

    @Test fun esperasDeLimitesDiferentesNaoVazamEntreSi() {
        val until = saturday.plusHours(2).atZone(zone).toInstant().toEpochMilli()
        assertTrue(engine.evaluateApp("app.a", snapshot(cooldowns = mapOf("g" to until)), saturday) is InterventionDecision.Challenge)
        val shortWindow = window.copy(startHour = 9, endHour = 10)
        val data = snapshot(schedules = listOf(shortWindow), cooldowns = mapOf(scope to until))
        val blocked = engine.evaluateApp("app.a", data, saturday.minusMinutes(30)) as InterventionDecision.Block
        assertEquals(BlockReason.COOLDOWN, blocked.reason)
        assertEquals(InterventionDecision.Allow, engine.evaluateApp("app.a", data, saturday.plusMinutes(30)))
    }

    @Test fun personalizacaoPodeTerCotasIndividuaisSemSomarApps() {
        val data = snapshot(schedules = listOf(window.copy(customRules = config.copy(sharedLimitAcrossApps = false))),
            logs = listOf(log(UsageKey.forTarget(scope, "app.a"), 3600), log(UsageKey.forTarget(scope, "app.b"), 3600)))
        val decision = engine.evaluateApp("app.a", data, saturday) as InterventionDecision.Challenge
        assertEquals(UsageKey.forTarget(scope, "app.a"), decision.usageKey)
        assertEquals(setOf("app.a"), decision.allowedPackages)
        assertEquals(GroupStatus.LIMITED, GroupStatusResolver().resolve(data.groups.single(), emptyMap(), saturday).status)
    }

    @Test fun contadorDeLiberacoesTambemPertenceAoLimitePersonalizado() {
        val decision = engine.evaluateApp("app.a", snapshot(logs = listOf(log(scope, 0).copy(totalOpens = 5))), saturday) as InterventionDecision.Block
        assertEquals(BlockReason.OPEN_LIMIT, decision.reason)
    }

    @Test fun bloqueioEPausaContinuamPrevalecendoSobreLimitePersonalizado() {
        val strict = window.copy(ruleId = "strict", scheduleType = ScheduleType.STRICT, customRules = null)
        assertEquals(BlockReason.STRICT_SCHEDULE,
            (engine.evaluateApp("app.a", snapshot(schedules = listOf(window, strict)), saturday) as InterventionDecision.Block).reason)
        assertEquals(InterventionDecision.Allow, engine.evaluateApp("app.a", snapshot(schedules = listOf(window, strict.copy(scheduleType = ScheduleType.BREAK))), saturday))
    }

    @Test fun duasPersonalizacoesSobrepostasSaoIdentificadasAntesDeSalvar() {
        assertTrue(GroupRuleResolver.overlappingCustomLimits(listOf(window, window.copy(ruleId = "another"))))
        assertFalse(GroupRuleResolver.overlappingCustomLimits(listOf(window, window.copy(ruleId = "another", customRules = null))))
        assertFalse(GroupRuleResolver.overlappingCustomLimits(listOf(window, window.copy(ruleId = "another", daysOfWeek = listOf(2)))))
    }

    @Test fun mudancaDeHorarioTrocaEscopoSemTransferirSessao() {
        val policy = snapshot(schedules = listOf(window.copy(startHour = 9, endHour = 10))).groups.single()
        assertEquals(scope, GroupRuleResolver.resolve(policy, saturday.minusMinutes(1)).scopeKey)
        assertEquals("g", GroupRuleResolver.resolve(policy, saturday).scopeKey)
        assertEquals(saturday, GroupRuleResolver.nextChange(policy, saturday.minusMinutes(1)))
    }

    @Test fun recuperacaoConheceFimDaJanelaMesmoSemEventosDeAcessibilidade() {
        val policy = snapshot().groups.single()
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), GroupRuleResolver.nextChange(policy, saturday))
    }

    @Test fun fimDeJanelaHerdadaTambemEncerraSessaoAntesDoIntervaloLivre() {
        val policy = snapshot(schedules = listOf(window.copy(customRules = null, startHour = 9, endHour = 11))).groups.single()
        assertEquals(saturday.withHour(11), GroupRuleResolver.nextChange(policy, saturday))
        assertEquals(InterventionDecision.Allow, engine.evaluateApp("app.a", PolicySnapshot(groups = listOf(policy), cooldowns = emptyMap()), saturday.withHour(11)))
        assertEquals(saturday.withHour(9), GroupRuleResolver.nextChange(policy, saturday.withHour(8)))
    }

    @Test fun chavesPersonalizadasPreservamGrupoETargetOriginais() {
        val key = UsageKey.forTarget(scope, "app.a")
        assertEquals("g", UsageKey.baseGroupId(key)); assertEquals(scope, UsageKey.scopeKey(key))
        assertEquals("app.a", UsageKey.targetIdentifier(key))
    }

    private fun log(key: String, seconds: Int) = DailyUsageLogEntity(date = "2026-10-24", groupId = key, totalTimeUsedSeconds = seconds)
    private fun snapshot(base: AppGroupEntity = group, schedules: List<GroupScheduleEntity> = listOf(
        window, window.copy(ruleId = "weekdays", daysOfWeek = listOf(2, 3, 4, 5, 6), customRules = null)),
        logs: List<DailyUsageLogEntity> = emptyList(), cooldowns: Map<String, Long> = emptyMap()) = PolicySnapshot.build(
        listOf(base), listOf("app.a", "app.b").map { GroupItemEntity(groupId = "g", targetType = TargetType.APP, targetIdentifier = it, displayName = it) },
        schedules, logs, cooldowns)
}
