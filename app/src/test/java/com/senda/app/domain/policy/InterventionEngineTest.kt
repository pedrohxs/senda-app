package com.senda.app.domain.policy

import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.AppGroupEntity
import com.senda.app.data.local.entity.DailyUsageLogEntity
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.local.entity.GroupScheduleEntity
import com.senda.app.data.local.entity.InterventionType
import com.senda.app.data.local.entity.MathDifficulty
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.local.entity.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class InterventionEngineTest {

    private val engine = InterventionEngine()
    private val now = LocalDateTime.of(2024, 1, 8, 10, 0)
    private val instagram = "com.instagram.android"

    @Test
    fun modoLivrePermite() {
        val snapshot = snapshot(scheduleType = ScheduleType.BREAK)

        assertEquals(InterventionDecision.Allow, engine.evaluateApp(instagram, snapshot, now))
    }

    @Test
    fun bloqueioRigidoBloqueia() {
        val snapshot = snapshot(scheduleType = ScheduleType.STRICT)

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.STRICT_SCHEDULE,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun limiteDiarioBloqueia() {
        val snapshot = snapshot(usage = usage(totalTimeUsedSeconds = 30 * 60))

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.DAILY_LIMIT,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun usoDe59SegundosAindaPermiteChallenge() {
        val snapshot = snapshot(
            group = defaultGroup.copy(dailyLimitMinutes = 1),
            usage = usage(totalTimeUsedSeconds = 59)
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
    }

    @Test
    fun usoDe60SegundosBloqueiaPeloLimiteDiario() {
        val snapshot = snapshot(
            group = defaultGroup.copy(dailyLimitMinutes = 1),
            usage = usage(totalTimeUsedSeconds = 60)
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.DAILY_LIMIT,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun limiteDeAberturasBloqueia() {
        val snapshot = snapshot(usage = usage(totalOpens = 15))

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.OPEN_LIMIT,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun cooldownBloqueiaComTempoRestante() {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = snapshot(
            cooldowns = mapOf("group-1" to nowMillis + 120_000L)
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        val block = decision as InterventionDecision.Block
        assertEquals(BlockReason.COOLDOWN, block.reason)
        assertEquals(120L, block.cooldownRemainingSeconds)
    }

    @Test
    fun challengeAplicaFriccaoProgressiva() {
        val snapshot = snapshot(usage = usage(totalOpens = 2))

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        val challenge = decision as InterventionDecision.Challenge
        assertEquals(instagram, challenge.target.packageName)
        assertEquals("Instagram", challenge.target.displayName)
        assertEquals(20, challenge.frictionSeconds)
        assertEquals(5, challenge.sessionLimitMinutes)
        assertEquals(10, challenge.cooldownMinutes)
        assertEquals(SessionCountMode.ACTIVE_SCREEN_TIME, challenge.countMode)
        assertEquals(InterventionType.BREATHING, challenge.interventionType)
    }

    @Test
    fun desafioDeMatematicaIgnoraFriccaoProgressiva() {
        val snapshot = snapshot(
            group = defaultGroup.copy(interventionType = InterventionType.MATH),
            usage = usage(totalOpens = 2)
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        val challenge = decision as InterventionDecision.Challenge
        assertEquals(10, challenge.frictionSeconds)
        assertEquals(InterventionType.MATH, challenge.interventionType)
    }

    @Test
    fun desafioDeMotivoIgnoraFriccaoProgressiva() {
        val snapshot = snapshot(
            group = defaultGroup.copy(interventionType = InterventionType.REASON),
            usage = usage(totalOpens = 4)
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        assertEquals(10, (decision as InterventionDecision.Challenge).frictionSeconds)
    }

    @Test
    fun desabilitadoTemporariamentePermite() {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = snapshot(
            group = defaultGroup.copy(temporaryDisableUntil = nowMillis + 60_000L)
        )

        assertEquals(InterventionDecision.Allow, engine.evaluateApp(instagram, snapshot, now))
    }

    @Test
    fun foraDaJanelaPermiteUsoLivre() {
        val snapshot = snapshot()

        val decision = engine.evaluateApp(
            instagram,
            snapshot,
            LocalDateTime.of(2024, 1, 8, 20, 0)
        )

        assertEquals(InterventionDecision.Allow, decision)
    }

    @Test
    fun semAgendamentoPermiteUsoLivre() {
        val snapshot = snapshot(scheduleType = null)

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertEquals(InterventionDecision.Allow, decision)
    }

    @Test
    fun intervaloLivreIgnoraCotaEEsperaDoHorarioAnterior() {
        val snapshot = snapshot(usage = usage(totalTimeUsedSeconds = 1800, totalOpens = 15),
            cooldowns = mapOf("group-1" to Long.MAX_VALUE), clockTamperRemainingSeconds = 1800)
        assertEquals(InterventionDecision.Allow, engine.evaluateApp(instagram, snapshot, now.withHour(20)))
    }

    @Test
    fun limiteValeNoInicioELiberaNoFimDaJanela() {
        val snapshot = snapshot()
        assertEquals(InterventionDecision.Allow, engine.evaluateApp(instagram, snapshot, now.withHour(8).withMinute(59)))
        assertTrue(engine.evaluateApp(instagram, snapshot, now.withHour(9)) is InterventionDecision.Challenge)
        assertTrue(engine.evaluateApp(instagram, snapshot, now.withHour(17).withMinute(59)) is InterventionDecision.Challenge)
        assertEquals(InterventionDecision.Allow, engine.evaluateApp(instagram, snapshot, now.withHour(18)))
    }

    @Test
    fun sitesEPalavrasDoGrupoTambemFicamLivresForaDoHorario() {
        val snapshot = snapshot(items = listOf(websiteItem("twitter.com", "Twitter"), keywordItem("video", "Vídeo")))
        assertEquals(InterventionDecision.Allow, engine.evaluateDomain("twitter.com", "browser", snapshot, now.withHour(20)))
        assertEquals(InterventionDecision.Allow, engine.evaluateKeyword("video", "browser", snapshot, now.withHour(20)))
    }

    @Test
    fun siteBloqueadoGeraBloqueioComNomeDoItem() {
        val snapshot = snapshot(
            items = listOf(
                appItem(),
                websiteItem("twitter.com", "Twitter")
            )
        )

        val decision = engine.evaluateDomain(
            host = "twitter.com",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertTrue(decision is InterventionDecision.Block)
        val block = decision as InterventionDecision.Block
        assertEquals(BlockReason.BLOCKED_DOMAIN, block.reason)
        assertEquals("com.android.chrome", block.target.packageName)
        assertEquals("twitter.com", block.target.identifier)
        assertEquals("Twitter", block.target.displayName)
        assertTrue(block.target.isWebsite)
    }

    @Test
    fun subdominioDeSiteBloqueadoTambemEhBloqueado() {
        val snapshot = snapshot(
            items = listOf(
                appItem(),
                websiteItem("reddit.com", "Reddit")
            )
        )

        val decision = engine.evaluateDomain(
            host = "old.reddit.com",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.BLOCKED_DOMAIN,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun palavraChaveBloqueadaGeraBloqueio() {
        val snapshot = snapshot(
            items = listOf(
                appItem(),
                keywordItem("shorts", "Shorts")
            )
        )

        val decision = engine.evaluateKeyword(
            keyword = "shorts",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertTrue(decision is InterventionDecision.Block)
        val block = decision as InterventionDecision.Block
        assertEquals(BlockReason.BLOCKED_KEYWORD, block.reason)
        assertEquals("shorts", block.target.identifier)
        assertEquals("Shorts", block.target.displayName)
        assertTrue(!block.target.isWebsite)
    }

    @Test
    fun siteBloqueadoIgnoraCooldownDoGrupo() {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = snapshot(
            items = listOf(
                appItem(),
                websiteItem("twitter.com", "Twitter")
            ),
            cooldowns = mapOf("group-1" to nowMillis + 120_000L)
        )

        val decision = engine.evaluateDomain(
            host = "twitter.com",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(
            BlockReason.BLOCKED_DOMAIN,
            (decision as InterventionDecision.Block).reason
        )
    }

    @Test
    fun siteBloqueadoEmJanelaLivrePermite() {
        val snapshot = snapshot(
            items = listOf(
                appItem(),
                websiteItem("twitter.com", "Twitter")
            ),
            scheduleType = ScheduleType.BREAK
        )

        val decision = engine.evaluateDomain(
            host = "twitter.com",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertEquals(InterventionDecision.Allow, decision)
    }

    @Test
    fun siteBloqueadoComGrupoPausadoPermite() {
        val nowMillis = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val snapshot = snapshot(
            group = defaultGroup.copy(temporaryDisableUntil = nowMillis + 60 * 60 * 1000L),
            items = listOf(
                appItem(),
                websiteItem("twitter.com", "Twitter")
            )
        )

        val decision = engine.evaluateDomain(
            host = "twitter.com",
            browserPackage = "com.android.chrome",
            snapshot = snapshot,
            now = now
        )

        assertEquals(InterventionDecision.Allow, decision)
    }

    @Test
    fun limiteIndividualNaoConsomeCotaDeOutroApp() {
        val tiktok = "com.zhiliaoapp.musically"
        val snapshot = PolicySnapshot.build(
            groups = listOf(defaultGroup.copy(sharedLimitAcrossApps = false)),
            items = listOf(
                appItem(),
                appItem().copy(targetIdentifier = tiktok, displayName = "TikTok")
            ),
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = listOf(
                usage(totalTimeUsedSeconds = 30 * 60).copy(
                    groupId = UsageKey.forTarget("group-1", instagram)
                )
            ),
            cooldowns = emptyMap()
        )

        val instagramDecision = engine.evaluateApp(instagram, snapshot, now)
        val tiktokDecision = engine.evaluateApp(tiktok, snapshot, now)

        assertTrue(instagramDecision is InterventionDecision.Block)
        assertEquals(BlockReason.DAILY_LIMIT, (instagramDecision as InterventionDecision.Block).reason)
        assertTrue(tiktokDecision is InterventionDecision.Challenge)
        assertEquals(UsageKey.forTarget("group-1", tiktok), (tiktokDecision as InterventionDecision.Challenge).usageKey)
    }

    @Test
    fun limiteCompartilhadoSomaUsoDosApps() {
        val tiktok = "com.zhiliaoapp.musically"
        val snapshot = PolicySnapshot.build(
            groups = listOf(defaultGroup.copy(sharedLimitAcrossApps = true)),
            items = listOf(
                appItem(),
                appItem().copy(targetIdentifier = tiktok, displayName = "TikTok")
            ),
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = listOf(
                usage(totalTimeUsedSeconds = 15 * 60).copy(
                    groupId = UsageKey.forTarget("group-1", instagram)
                ),
                usage(totalTimeUsedSeconds = 15 * 60).copy(
                    groupId = UsageKey.forTarget("group-1", tiktok)
                )
            ),
            cooldowns = emptyMap()
        )

        val decision = engine.evaluateApp(tiktok, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals(BlockReason.DAILY_LIMIT, (decision as InterventionDecision.Block).reason)
    }

    @Test
    fun desafioCompartilhadoPermiteTodosOsAppsDoGrupo() {
        val tiktok = "com.zhiliaoapp.musically"
        val snapshot = PolicySnapshot.build(
            groups = listOf(defaultGroup.copy(sharedLimitAcrossApps = true)),
            items = listOf(
                appItem(),
                appItem().copy(targetIdentifier = tiktok, displayName = "TikTok")
            ),
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = emptyList(),
            cooldowns = emptyMap()
        )

        val decision = engine.evaluateApp(tiktok, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        val challenge = decision as InterventionDecision.Challenge
        assertEquals(setOf(instagram, tiktok), challenge.allowedPackages)
        assertEquals("group-1", challenge.usageKey)
    }

    @Test
    fun desafioDeMatematicaLevaDificuldadeEQuantidadeDeContas() {
        val mathGroup = defaultGroup.copy(
            interventionType = InterventionType.MATH,
            mathDifficulty = MathDifficulty.HARD,
            mathEquationCount = 5
        )
        val snapshot = PolicySnapshot.build(
            groups = listOf(mathGroup),
            items = listOf(appItem()),
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = emptyList(),
            cooldowns = emptyMap()
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        val challenge = decision as InterventionDecision.Challenge
        assertEquals(MathDifficulty.HARD, challenge.mathDifficulty)
        assertEquals(5, challenge.mathEquationCount)
    }

    @Test
    fun desafioIndividualNaoPermiteOutroAppDoGrupo() {
        val tiktok = "com.zhiliaoapp.musically"
        val snapshot = PolicySnapshot.build(
            groups = listOf(defaultGroup.copy(sharedLimitAcrossApps = false)),
            items = listOf(
                appItem(),
                appItem().copy(targetIdentifier = tiktok, displayName = "TikTok")
            ),
            schedules = listOf(schedule(ScheduleType.LIMITED)),
            usage = emptyList(),
            cooldowns = emptyMap()
        )

        val decision = engine.evaluateApp(tiktok, snapshot, now)

        assertTrue(decision is InterventionDecision.Challenge)
        assertEquals(setOf(tiktok), (decision as InterventionDecision.Challenge).allowedPackages)
    }

    @Test
    fun relogioAlteradoBloqueiaAteEmModoLivre() {
        val snapshot = snapshot(
            scheduleType = ScheduleType.BREAK,
            clockTamperRemainingSeconds = 1_800L
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        val block = decision as InterventionDecision.Block
        assertEquals(BlockReason.CLOCK_TAMPER, block.reason)
        assertEquals(1_800L, block.cooldownRemainingSeconds)
    }

    @Test
    fun bloqueioRigidoVenceQuandoAppEstaEmDoisGrupos() {
        val strictGroup = defaultGroup.copy(id = "group-2", name = "Trabalho")
        val snapshot = PolicySnapshot.build(
            groups = listOf(defaultGroup, strictGroup),
            items = listOf(appItem(), appItem().copy(groupId = "group-2")),
            schedules = listOf(
                schedule(ScheduleType.LIMITED),
                schedule(ScheduleType.STRICT).copy(groupId = "group-2")
            ),
            usage = emptyList(),
            cooldowns = emptyMap()
        )

        val decision = engine.evaluateApp(instagram, snapshot, now)

        assertTrue(decision is InterventionDecision.Block)
        assertEquals("group-2", (decision as InterventionDecision.Block).groupId)
    }

    private fun snapshot(
        group: AppGroupEntity = defaultGroup,
        items: List<GroupItemEntity> = listOf(appItem()),
        scheduleType: ScheduleType? = ScheduleType.LIMITED,
        usage: DailyUsageLogEntity? = null,
        cooldowns: Map<String, Long> = emptyMap(),
        clockTamperRemainingSeconds: Long = 0L
    ): PolicySnapshot = PolicySnapshot.build(
        groups = listOf(group),
        items = items,
        schedules = scheduleType?.let { listOf(schedule(it)) }.orEmpty(),
        usage = listOfNotNull(usage),
        cooldowns = cooldowns,
        clockTamperRemainingSeconds = clockTamperRemainingSeconds
    )

    private fun schedule(type: ScheduleType) = GroupScheduleEntity(
        groupId = "group-1",
        scheduleType = type,
        daysOfWeek = listOf(2),
        startHour = 9,
        startMinute = 0,
        endHour = 18,
        endMinute = 0
    )

    private fun usage(
        totalTimeUsedSeconds: Int = 0,
        totalOpens: Int = 0
    ) = DailyUsageLogEntity(
        date = "2024-01-08",
        groupId = "group-1",
        totalTimeUsedMinutes = totalTimeUsedSeconds / 60,
        totalTimeUsedSeconds = totalTimeUsedSeconds,
        totalOpens = totalOpens
    )

    private fun appItem() = GroupItemEntity(
        groupId = "group-1",
        targetType = TargetType.APP,
        targetIdentifier = instagram,
        displayName = "Instagram"
    )

    private fun websiteItem(domain: String, name: String) = GroupItemEntity(
        groupId = "group-1",
        targetType = TargetType.WEBSITE,
        targetIdentifier = domain,
        displayName = name
    )

    private fun keywordItem(keyword: String, name: String) = GroupItemEntity(
        groupId = "group-1",
        targetType = TargetType.KEYWORD,
        targetIdentifier = keyword,
        displayName = name
    )

    private val defaultGroup = AppGroupEntity(
        id = "group-1",
        name = "Redes Sociais",
        dailyLimitMinutes = 30,
        sessionLimitMinutes = 5,
        cooldownMinutes = 10,
        maxDailyOpens = 15,
        sessionCountMode = SessionCountMode.ACTIVE_SCREEN_TIME,
        interventionType = InterventionType.BREATHING,
        breathingSeconds = 10,
        progressiveFrictionEnabled = true,
        progressiveSecondsPerOpen = 5
    )
}
