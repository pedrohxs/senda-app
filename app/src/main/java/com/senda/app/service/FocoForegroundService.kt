package com.senda.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.senda.app.R
import com.senda.app.SendaApplication
import com.senda.app.core.ForegroundPackages
import com.senda.app.data.local.SendaDatabase
import com.senda.app.data.permissions.FocoPermissions
import com.senda.app.data.local.dao.ChallengeReasonDao
import com.senda.app.data.local.entity.ChallengeReasonEntity
import com.senda.app.data.local.entity.SessionCountMode
import com.senda.app.data.preferences.ActiveSessionStore
import com.senda.app.data.preferences.CooldownStore
import com.senda.app.data.preferences.PersistedActiveSession
import com.senda.app.data.preferences.ServiceHeartbeatStore
import com.senda.app.data.repository.DailyUsageRepository
import com.senda.app.data.repository.SessionCheckpointRepository
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.DailyLimitGuard
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.domain.policy.InterventionTarget
import com.senda.app.domain.policy.PolicySnapshot
import com.senda.app.domain.policy.StrictScheduleGuard
import com.senda.app.domain.policy.GroupRuleResolver
import com.senda.app.domain.policy.InterventionEngine
import com.senda.app.core.UsageKey
import com.senda.app.data.local.entity.ScheduleType
import com.senda.app.domain.session.ActiveSessionRestorer
import com.senda.app.domain.session.SessionRestoreDecision
import com.senda.app.domain.session.SessionUsageCounter
import com.senda.app.ui.overlay.ChallengeActivity
import com.senda.app.ui.overlay.FocoBubbleController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max

class FocoForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var usageRepository: DailyUsageRepository
    private lateinit var challengeReasonDao: ChallengeReasonDao
    private lateinit var cooldownStore: CooldownStore
    private lateinit var activeSessionStore: ActiveSessionStore
    private lateinit var sessionCheckpoints: SessionCheckpointRepository
    private lateinit var heartbeatStore: ServiceHeartbeatStore
    private lateinit var overlayController: FocoOverlayController
    private var powerManager: PowerManager? = null

    private val notifications by lazy { ServiceNotifications(this) }

    private val permissions by lazy { FocoPermissions(this) }
    private lateinit var bubbleController: FocoBubbleController
    private var timerJob: Job? = null
    private var watchdogJob: Job? = null
    private var interventionGuardianJob: Job? = null
    private var restoreJob: Job? = null
    private var overlayDismissJob: Job? = null
    private var restoreAttempted = false
    private var runningSession: RunningSession? = null
    private var endingSession = false
    private var lastNotificationSecond = -1
    private var lastPersistedAt = 0L
    private var lastPersistedPaused = false
    private var watchdogNotified = false
    private var latestPolicySnapshot: PolicySnapshot? = null
    private var lastIntervention: InterventionDecision? = null
    private var lastInterventionAt = 0L

    /**
     * Controle de reexibição: quando a sobreposição sai da tela de propósito
     * (o usuário foi para a Home ou tocou em "voltar ao que importa"), ela só
     * volta depois que o usuário realmente saiu do app e retornou — senão o
     * bloqueio reapareceria em cima da Home, em laço.
     */
    private var interventionAwaitingReturn = false
    private var interventionMayReturn = false
    private var suppressingPackage: String? = null
    private var suppressUntil = 0L
    private val pausedWakeUp = Channel<Unit>(Channel.CONFLATED)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val screenOff = intent?.action == Intent.ACTION_SCREEN_OFF
            (application as SendaApplication).container.reportHistory.recordHeartbeatAsync(
                System.currentTimeMillis(), SystemClock.elapsedRealtime(), activeSessionStore.currentBootCount(),
                AppMonitor.accessibilityConnected && permissions.isAccessibilityEnabled() && Settings.canDrawOverlays(this@FocoForegroundService),
                screenOff
            )
            runningSession?.let { session ->
                advanceSession(session)
                persistActiveSession(session)
                pausedWakeUp.trySend(Unit)
            }
        }
    }
    private val dailyLimitLatch by lazy {
        (application as SendaApplication).container.dailyLimitLatch
    }

    private val imePackages: Set<String> by lazy {
        val inputMethodManager = getSystemService(InputMethodManager::class.java)
        inputMethodManager.enabledInputMethodList
            .mapNotNull { info -> info.packageName }
            .toSet()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        val database = SendaDatabase.getInstance(applicationContext)
        usageRepository = DailyUsageRepository(database.dailyUsageLogDao())
        challengeReasonDao = database.challengeReasonDao()
        cooldownStore = CooldownStore(applicationContext)
        activeSessionStore = ActiveSessionStore(applicationContext)
        sessionCheckpoints = (application as SendaApplication).container.sessionCheckpoints
        heartbeatStore = ServiceHeartbeatStore(applicationContext)
        overlayController = FocoOverlayController(
            context = this,
            windowManager = windowManager,
            onDismissBlock = { block ->
                // O bloqueio continua pendente (some da tela, não da memória):
                // se o usuário voltar ao app antes do TTL, ele reaparece.
                // Evita re-bloquear enquanto o HOME ainda está sendo processado.
                interventionAwaitingReturn = true
                interventionMayReturn = false
                suppressBriefly(block.target.packageName)
                goHome()
            }
        )
        bubbleController = FocoBubbleController(
            context = applicationContext,
            windowManager = windowManager
        )
        notifications.createChannels()
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        observeForegroundPackage()
        observePolicySnapshot()
        startWatchdog()
        startInterventionGuardian()
        AppMonitor.setProtectionServiceRunning(true)
    }

    private fun observePolicySnapshot() {
        val container = (application as SendaApplication).container
        serviceScope.launch {
            container.policyRepository.observeSnapshot().collect { snapshot ->
                latestPolicySnapshot = snapshot
                applyUpdatedSessionLimit(snapshot)
            }
        }
    }

    /**
     * Se o usuário mudar a duração da sessão no editor enquanto ela está ativa,
     * a sessão em andamento passa a respeitar o novo valor.
     */
    private fun applyUpdatedSessionLimit(snapshot: PolicySnapshot) {
        val session = runningSession ?: return

        val policy = snapshot.groupsForPackage(session.packageName)
            .firstOrNull { it.group.id == session.groupId }
            ?: return

        val resolved = GroupRuleResolver.resolve(policy, LocalDateTime.now())
        if (resolved.scheduleType != ScheduleType.LIMITED || resolved.scopeKey != UsageKey.scopeKey(session.usageKey)) return
        val newDuration = resolved.group.sessionLimitMinutes * 60
        if (newDuration <= 0 || newDuration == session.durationSeconds) return

        advanceSession(session)
        val usedMillis = session.durationSeconds * 1000L - session.counter.remainingMillis
        val newRemaining = newDuration * 1000L - usedMillis

        if (newRemaining <= 0) {
            finishSession(startCooldown = false)
            updateServiceNotification()
            return
        }

        session.durationSeconds = newDuration
        session.counter.remainingMillis = newRemaining
        if (session.mode == SessionCountMode.WALL_CLOCK_WINDOW) {
            session.deadlineElapsedRealtime = SystemClock.elapsedRealtime() + newRemaining
        }

        publishSessionState()
        persistActiveSession(session)
        updateServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        when (intent?.action) {
            ACTION_COMPLETE_CHALLENGE -> {
                val id = intent.getStringExtra(EXTRA_CHALLENGE_ID) ?: return START_STICKY
                val reason = intent.getStringExtra(EXTRA_CHALLENGE_REASON)
                serviceScope.launch {
                    val snapshot = withTimeoutOrNull(5_000L) {
                        (application as SendaApplication).container.policyRepository.observeSnapshot().first()
                    }
                    val pending = ChallengeIntervention.store.request.value
                        ?.takeIf { it.id == id && it.status == ChallengeRequestStore.Status.SUBMITTING }
                        ?: return@launch
                    if (snapshot == null) {
                        ChallengeIntervention.store.clear(id)
                        goHome()
                        return@launch
                    }
                    latestPolicySnapshot = snapshot
                    acceptChallenge(pending, reason)
                }
            }

            ACTION_CHALLENGE_ABORTED -> {
                recordImpulseAborted(intent.getStringExtra(FocoActions.EXTRA_USAGE_KEY).orEmpty())
            }

            FocoActions.ACTION_START_SESSION -> {
                restoreAttempted = true
                restoreJob?.cancel()
                val packageName = intent.getStringExtra(FocoActions.EXTRA_PACKAGE_NAME)
                if (packageName.isNullOrBlank()) return START_STICKY

                val groupId = intent.getStringExtra(FocoActions.EXTRA_GROUP_ID).orEmpty()
                val usageKey = intent.getStringExtra(FocoActions.EXTRA_USAGE_KEY)
                    ?.takeIf { it.isNotBlank() }
                    ?: groupId
                val targetIdentifier = intent.getStringExtra(FocoActions.EXTRA_TARGET_IDENTIFIER)
                    ?: packageName
                val isWebsite = intent.getBooleanExtra(FocoActions.EXTRA_IS_WEBSITE, false)
                val durationMinutes = intent.getIntExtra(
                    FocoActions.EXTRA_DURATION_MINUTES,
                    DEFAULT_SESSION_MINUTES
                )
                val cooldownMinutes = intent.getIntExtra(
                    FocoActions.EXTRA_COOLDOWN_MINUTES,
                    DEFAULT_COOLDOWN_MINUTES
                )
                val mode = intent.getStringExtra(FocoActions.EXTRA_COUNT_MODE)
                    ?.let { value -> runCatching { SessionCountMode.valueOf(value) }.getOrNull() }
                    ?: SessionCountMode.ACTIVE_SCREEN_TIME

                startSession(
                    groupId = groupId,
                    packageName = packageName,
                    targetIdentifier = targetIdentifier,
                    isWebsite = isWebsite,
                    durationMinutes = durationMinutes,
                    cooldownMinutes = cooldownMinutes,
                    mode = mode,
                    usageKey = usageKey
                )
            }

            FocoActions.ACTION_STOP_SESSION -> {
                restoreAttempted = true
                restoreJob?.cancel()
                finishSession(startCooldown = true)
                updateServiceNotification()
            }

            else -> {
                if (runningSession == null) {
                    updateServiceNotification()
                    restoreActiveSessionIfNeeded()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppMonitor.setProtectionServiceRunning(false)
        (application as SendaApplication).container.reportHistory.recordHeartbeatAsync(
            System.currentTimeMillis(), SystemClock.elapsedRealtime(), activeSessionStore.currentBootCount(), false,
            powerManager?.isInteractive == false
        )
        runningSession?.let { session ->
            advanceSession(session)
            persistActiveSession(session)
        }
        unregisterReceiver(screenReceiver)
        overlayController.remove()
        serviceScope.cancel()
        removeBubble()
        FocoSessionState.set(null)
        super.onDestroy()
    }

    private fun startSession(
        groupId: String,
        packageName: String,
        targetIdentifier: String,
        isWebsite: Boolean,
        durationMinutes: Int,
        cooldownMinutes: Int,
        mode: SessionCountMode,
        usageKey: String = groupId,
        allowedPackages: Set<String> = setOf(packageName)
    ) {
        restoreAttempted = true
        restoreJob?.cancel()
        overlayController.remove()

        if (runningSession != null) {
            finishSession(startCooldown = false)
        }

        val durationSeconds = max(1, durationMinutes) * 60
        timerJob?.cancel()
        endingSession = false
        lastNotificationSecond = -1
        val startedElapsed = SystemClock.elapsedRealtime()
        val startedEpoch = System.currentTimeMillis()
        lastPersistedAt = startedElapsed
        lastPersistedPaused = false

        runningSession = RunningSession(
            groupId = groupId,
            packageName = packageName,
            targetIdentifier = targetIdentifier,
            isWebsite = isWebsite,
            displayName = resolveAppLabel(packageName),
            mode = mode,
            durationSeconds = durationSeconds,
            counter = SessionUsageCounter(
                remainingMillis = durationSeconds * 1000L,
                lastElapsedRealtime = startedElapsed,
                lastEpochMillis = startedEpoch,
                counting = mode == SessionCountMode.WALL_CLOCK_WINDOW ||
                    (AppMonitor.foregroundPackage.value in (allowedPackages + packageName) &&
                        powerManager?.isInteractive != false)
            ),
            deadlineElapsedRealtime = startedElapsed + durationSeconds * 1000L,
            cooldownMinutes = max(0, cooldownMinutes),
            usageKey = usageKey,
            allowedPackages = (allowedPackages + packageName).toSet(),
            countingPackage = AppMonitor.foregroundPackage.value
        )

        if (groupId.isNotBlank()) {
            serviceScope.launch {
                usageRepository.recordOpen(usageKey)
                cooldownStore.clearCooldown(UsageKey.scopeKey(usageKey))
            }
        }

        publishSessionState()
        updateServiceNotification()
        runningSession?.let(::persistActiveSession)

        if (AppMonitor.foregroundPackage.value == packageName) {
            showBubble()
        }

        startTimerLoop()
    }

    private fun restoreActiveSessionIfNeeded() {
        if (restoreAttempted) return
        restoreAttempted = true

        restoreJob = serviceScope.launch {
            val persisted = sessionCheckpoints.load()
                ?: return@launch
            if (runningSession != null) return@launch

            val snapshot = latestPolicySnapshot ?: (application as SendaApplication).container.policyRepository.observeSnapshot().first()
            val policy = snapshot.groups.firstOrNull { it.group.id == persisted.groupId }
            val savedTime = java.time.Instant.ofEpochMilli(persisted.savedAtEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val savedRule = policy?.let { GroupRuleResolver.resolve(it, savedTime) }
            val validAtSave = savedRule?.scheduleType == ScheduleType.LIMITED && savedRule.scopeKey == UsageKey.scopeKey(persisted.usageKey)
            val boundary = if (validAtSave && persisted.savedAtEpochMillis > 0L) GroupRuleResolver.nextChange(policy!!, savedTime)
                ?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli() else null
            val boundaryElapsed = boundary?.let { persisted.savedAtElapsedRealtime + it - persisted.savedAtEpochMillis }

            val now = SystemClock.elapsedRealtime()
            if (persisted.bootCount == activeSessionStore.currentBootCount() && persisted.savedAtEpochMillis > 0L &&
                (!validAtSave || (boundaryElapsed != null && now >= boundaryElapsed &&
                    (persisted.mode == SessionCountMode.ACTIVE_SCREEN_TIME || boundaryElapsed < persisted.deadlineElapsedRealtime)))) {
                val ended = restoredSession(persisted, if (validAtSave) boundaryElapsed else persisted.savedAtElapsedRealtime)
                sessionCheckpoints.save(ended.toPersisted(), finished = true)
                val foreground = AppMonitor.foregroundPackage.value
                if (foreground != null && foreground in persisted.allowedPackages) presentDecision(InterventionEngine().evaluateApp(foreground, snapshot))
                return@launch
            }
            val decision = ActiveSessionRestorer.decide(
                session = persisted,
                nowElapsedRealtime = now,
                currentBootCount = activeSessionStore.currentBootCount(),
                foregroundPackage = AppMonitor.foregroundPackage.value
            )

            when (decision) {
                SessionRestoreDecision.Discard -> sessionCheckpoints.discard()

                is SessionRestoreDecision.Expired -> {
                    val restored = restoredSession(persisted)
                    sendAppLockBroadcast(
                        groupId = persisted.groupId,
                        packageName = persisted.packageName
                    )
                    sessionCheckpoints.save(restored.toPersisted(), finished = true, startCooldown = true)
                }

                is SessionRestoreDecision.Resume -> {
                    resumeSession(persisted)
                }
            }
        }
    }

    private fun resumeSession(persisted: PersistedActiveSession) {
        overlayController.remove()
        endingSession = false
        lastNotificationSecond = -1
        lastPersistedAt = SystemClock.elapsedRealtime()
        lastPersistedPaused = false

        runningSession = restoredSession(persisted)

        publishSessionState()
        updateServiceNotification()
        runningSession?.let(::persistActiveSession)

        val foreground = AppMonitor.foregroundPackage.value
        if (foreground != null && foreground in persisted.allowedPackages) {
            runningSession?.let { session -> updateCurrentPackage(session, foreground) }
            showBubble()
        }

        startTimerLoop()
    }

    private fun restoredSession(persisted: PersistedActiveSession, accountingDeadlineElapsed: Long? = null): RunningSession {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val elapsedSinceSave = (nowElapsed - persisted.savedAtElapsedRealtime).coerceAtLeast(0L)
        val counter = SessionUsageCounter(
            remainingMillis = persisted.remainingMillis,
            lastElapsedRealtime = persisted.savedAtElapsedRealtime,
            lastEpochMillis = persisted.savedAtEpochMillis.takeIf { it > 0L }
                ?: (nowEpoch - elapsedSinceSave),
            counting = persisted.mode == SessionCountMode.WALL_CLOCK_WINDOW,
            usageMillisByDate = persisted.usageMillisByDate
        )
        if (accountingDeadlineElapsed != null) {
            val end = minOf(nowElapsed, accountingDeadlineElapsed).coerceAtLeast(persisted.savedAtElapsedRealtime)
            counter.advance(end, persisted.savedAtEpochMillis + end - persisted.savedAtElapsedRealtime, false)
        }
        counter.advance(nowElapsed, nowEpoch, persisted.mode == SessionCountMode.WALL_CLOCK_WINDOW ||
            (AppMonitor.foregroundPackage.value in persisted.allowedPackages && powerManager?.isInteractive != false))
        return RunningSession(
            groupId = persisted.groupId,
            packageName = persisted.packageName,
            targetIdentifier = persisted.targetIdentifier,
            isWebsite = persisted.isWebsite,
            displayName = resolveAppLabel(persisted.packageName),
            mode = persisted.mode,
            durationSeconds = persisted.durationSeconds,
            counter = counter,
            deadlineElapsedRealtime = persisted.deadlineElapsedRealtime,
            cooldownMinutes = persisted.cooldownMinutes,
            usageKey = persisted.usageKey,
            allowedPackages = persisted.allowedPackages.ifEmpty {
                setOf(persisted.packageName)
            },
            sessionId = persisted.sessionId,
            countingPackage = AppMonitor.foregroundPackage.value,
            isPaused = persisted.mode == SessionCountMode.ACTIVE_SCREEN_TIME &&
                (AppMonitor.foregroundPackage.value !in persisted.allowedPackages || powerManager?.isInteractive == false)
        )
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                // Sessão pausada (app fora de foco ou tela apagada) não precisa
                // acordar a cada segundo: o tempo não corre. O sinal de foreground
                // retoma o ritmo normal na hora e o timeout é só uma rede de segurança.
                if (runningSession?.isPaused == true) {
                    withTimeoutOrNull(PAUSED_TICK_INTERVAL_MS) { pausedWakeUp.receive() }
                } else {
                    delay(TICK_INTERVAL_MS)
                }

                val session = runningSession ?: break
                tick(session)
                if (runningSession === session && session.remainingSeconds <= 0) {
                    expireSession(session)
                    break
                }
            }
        }
    }

    private suspend fun tick(session: RunningSession) {
        advanceSession(session)
        if (endSessionForChangedRule(session)) return
        interruptingBlock(session)?.let { block ->
            if (runningSession !== session) return
            interruptSession(session, block)
            return
        }
        if (runningSession !== session) return

        ensureInterventionVisible()

        val now = SystemClock.elapsedRealtime()

        publishSessionState()

        val shouldUpdateNotification = session.remainingSeconds != lastNotificationSecond &&
            (session.remainingSeconds % NOTIFICATION_UPDATE_SECONDS == 0 ||
                session.remainingSeconds <= NOTIFICATION_CRITICAL_SECONDS)
        if (shouldUpdateNotification) {
            lastNotificationSecond = session.remainingSeconds
            updateServiceNotification()
        }

        val pausedChanged = session.isPaused != lastPersistedPaused
        val persistDue = !session.isPaused && now - lastPersistedAt >= PERSIST_INTERVAL_MS
        if (pausedChanged || persistDue) {
            lastPersistedAt = now
            lastPersistedPaused = session.isPaused
            persistActiveSession(session)
        }
    }

    private fun endSessionForChangedRule(session: RunningSession): Boolean {
        val snapshot = latestPolicySnapshot ?: return false
        val policy = snapshot.groups.firstOrNull { it.group.id == session.groupId }
        val resolved = policy?.let { GroupRuleResolver.resolve(it, LocalDateTime.now()) }
        val paused = policy?.group?.temporaryDisableUntil?.let { it > System.currentTimeMillis() } == true
        val expectedKey = resolved?.let { if (it.group.sharedLimitAcrossApps) it.scopeKey
            else UsageKey.forTarget(it.scopeKey, session.targetIdentifier) }
        val expectedPackages = if (resolved?.group?.sharedLimitAcrossApps == true)
            policy?.appPackages.orEmpty() + (if (session.isWebsite) setOf(session.packageName) else emptySet())
            else setOf(session.packageName)
        if (resolved != null && resolved.scopeKey == UsageKey.scopeKey(session.usageKey) &&
            resolved.scheduleType == ScheduleType.LIMITED && !paused && resolved.group.isEnabled &&
            expectedKey == session.usageKey && resolved.group.sessionCountMode == session.mode &&
            expectedPackages == session.allowedPackages &&
            (session.isWebsite || policy?.appPackages?.contains(session.packageName) == true)) return false
        val foreground = AppMonitor.foregroundPackage.value
        finishSession(startCooldown = false)
        clearPendingIntervention()
        overlayController.remove()
        updateServiceNotification()
        if (foreground != null && foreground in session.allowedPackages) {
            presentDecision(InterventionEngine().evaluateApp(foreground, snapshot))
        }
        return true
    }

    /**
     * Uma sessão ativa libera o app, mas duas regras vencem a sessão e podem
     * começar no meio dela: o bloqueio rígido agendado e o limite diário.
     * Como o usuário pode continuar no mesmo app, a checagem precisa acontecer
     * no tick do timer — não apenas em eventos de acessibilidade.
     */
    private suspend fun interruptingBlock(session: RunningSession): InterventionDecision.Block? {
        val snapshot = latestPolicySnapshot ?: return null
        val foreground = AppMonitor.foregroundPackage.value ?: return null
        if (foreground !in session.allowedPackages) return null

        val policies = snapshot.groupsForPackage(session.packageName)
        if (policies.isEmpty()) return null

        val policy = policies.firstOrNull { it.group.id == session.groupId }
            ?: policies.first()
        val resolved = GroupRuleResolver.resolve(policy, LocalDateTime.now())
        val effectivePolicy = policy.scoped(resolved.scopeKey, resolved.group, LocalDate.now())
        val today = LocalDate.now().toString()
        val balance = if (resolved.group.dailyLimitMinutes > 0) {
            usageRepository.sessionBalance(
                today, resolved.scopeKey, session.usageKey,
                resolved.group.sharedLimitAcrossApps, session.sessionId
            )
        } else null

        val reason = when {
            StrictScheduleGuard.isActive(policies, LocalDateTime.now()) ->
                BlockReason.STRICT_SCHEDULE

            balance != null && DailyLimitGuard.isExceeded(
                effectivePolicy, session.counter.secondsOn(today), balance.sessionSeconds, balance.totalSeconds
            ) ->
                BlockReason.DAILY_LIMIT

            else -> return null
        }

        return InterventionDecision.Block(
            target = InterventionTarget(
                packageName = session.packageName,
                identifier = session.packageName,
                displayName = session.displayName,
                isWebsite = false
            ),
            groupId = policy.group.id,
            groupName = policy.group.name,
            reason = reason,
            usageKey = session.usageKey,
            dailyLimitMinutes = resolved.group.dailyLimitMinutes
        )
    }

    private fun interruptSession(session: RunningSession, block: InterventionDecision.Block) {
        finishSession(startCooldown = false)
        updateServiceNotification()
        if (block.reason == BlockReason.DAILY_LIMIT) {
            dailyLimitLatch.markReached(session.usageKey, block.dailyLimitMinutes)
        }
        presentDecision(block)
    }

    private fun expireSession(session: RunningSession) {
        if (endingSession) return
        endingSession = true
        clearPendingIntervention()

        sendAppLockBroadcast(
            groupId = session.groupId,
            packageName = session.packageName
        )

        finishSession(startCooldown = true)
        updateServiceNotification()
    }

    private fun sendAppLockBroadcast(groupId: String, packageName: String) {
        sendBroadcast(
            Intent(FocoActions.ACTION_APP_LOCK)
                .setPackage(this.packageName)
                .putExtra(FocoActions.EXTRA_GROUP_ID, groupId)
                .putExtra(FocoActions.EXTRA_PACKAGE_NAME, packageName)
        )
    }

    private fun finishSession(startCooldown: Boolean) {
        val session = runningSession ?: return
        advanceSession(session)

        timerJob?.cancel()
        timerJob = null
        runningSession = null
        endingSession = false
        lastNotificationSecond = -1
        lastPersistedAt = 0L
        lastPersistedPaused = false
        removeBubble()
        FocoSessionState.set(null)
        sessionCheckpoints.save(session.toPersisted(), finished = true, startCooldown = startCooldown)
    }

    private fun persistActiveSession(session: RunningSession) {
        sessionCheckpoints.save(session.toPersisted())
    }

    private fun RunningSession.toPersisted() = PersistedActiveSession(
            groupId = groupId,
            packageName = packageName,
            targetIdentifier = targetIdentifier,
            isWebsite = isWebsite,
            mode = mode,
            durationSeconds = durationSeconds,
            remainingSeconds = remainingSeconds,
            deadlineElapsedRealtime = deadlineElapsedRealtime,
            cooldownMinutes = cooldownMinutes,
            savedAtElapsedRealtime = counter.checkpointElapsedRealtime,
            bootCount = activeSessionStore.currentBootCount(),
            usageKey = usageKey,
            allowedPackages = allowedPackages,
            sessionId = sessionId,
            remainingMillis = counter.remainingMillis,
            savedAtEpochMillis = counter.checkpointEpochMillis,
            usageMillisByDate = counter.usageMillisByDate
        )

    private fun advanceSession(session: RunningSession, foreground: String? = AppMonitor.foregroundPackage.value) {
        if (foreground !in TRANSIENT_FOREGROUND_PACKAGES && foreground !in imePackages) {
            session.countingPackage = foreground
        }
        val counting = session.mode == SessionCountMode.WALL_CLOCK_WINDOW ||
            (session.countingPackage in session.allowedPackages && powerManager?.isInteractive != false)
        session.counter.advance(SystemClock.elapsedRealtime(), System.currentTimeMillis(), counting)
        session.isPaused = !counting
    }
    private fun publishSessionState() {
        val session = runningSession ?: return
        FocoSessionState.set(
            ActiveFocoSession(
                groupId = session.groupId,
                packageName = session.packageName,
                targetIdentifier = session.targetIdentifier,
                isWebsite = session.isWebsite,
                displayName = session.displayName,
                mode = session.mode,
                durationSeconds = session.durationSeconds,
                remainingSeconds = session.remainingSeconds,
                isPaused = session.isPaused,
                allowedPackages = session.allowedPackages,
                usageKey = session.usageKey
            )
        )
    }

    private fun observeForegroundPackage() {
        serviceScope.launch {
            AppMonitor.foregroundPackage.collect { packageName ->
                handleOverlayForegroundChange(packageName)

                val session = runningSession
                if (session == null) {
                    removeBubble()
                    return@collect
                }

                // Account for the interval before switching counting state; frequent
                // foreground events must neither add a second nor reset fractional use.
                advanceSession(session, packageName)
                persistActiveSession(session)

                if (packageName == null) {
                    removeBubble()
                    return@collect
                }

                if (packageName in TRANSIENT_FOREGROUND_PACKAGES || packageName in imePackages) {
                    // Notification shade, permission dialogs, keyboards and similar windows do
                    // not mean the user left the app. Keep the floating timer on screen.
                    return@collect
                }

                if (packageName in session.allowedPackages) {
                    // Retoma o contador imediatamente após uma pausa.
                    pausedWakeUp.trySend(Unit)
                    updateCurrentPackage(session, packageName)
                    if (overlayController.current == null) {
                        showBubble()
                    }
                } else {
                    removeBubble()
                }
            }
        }

        serviceScope.launch {
            ChallengeIntervention.store.request.collect { request ->
                if (request != null) {
                    if (runningSession?.let { UsageKey.scopeKey(it.usageKey) != UsageKey.scopeKey(request.challenge.usageKey) } == true) {
                        finishSession(startCooldown = false)
                    }
                    clearPendingIntervention()
                    overlayController.remove()
                    removeBubble()
                }
            }
        }

        serviceScope.launch {
            AppMonitor.appBlocked.collect { block ->
                val foreground = AppMonitor.foregroundPackage.value
                if (foreground == null || foreground == block.target.packageName ||
                    ForegroundPackages.isHome(packageManager, foreground)) {
                    presentDecision(block)
                }
            }
        }

        serviceScope.launch {
            AppMonitor.sensitiveAppOpened.collect {
                clearPendingIntervention()
                removeBubble()
                overlayController.remove()
            }
        }
    }

    private fun handleOverlayForegroundChange(packageName: String?) {
        if (packageName == this.packageName) {
            clearPendingIntervention()
            overlayController.remove()
            return
        }
        val overlay = overlayController.current ?: return
        if (packageName == null || packageName == overlay.targetPackage) return
        if (isTransientOverlayPackage(packageName) || ForegroundPackages.isHome(packageManager, packageName)) return

        // Keyboard, permission dialogs and Play Services can briefly take focus while
        // the user is still inside the target app. Wait a moment and only close the
        // intervention if another real app actually became foreground.
        overlayDismissJob?.cancel()
        overlayDismissJob = serviceScope.launch {
            delay(OVERLAY_DISMISS_GRACE_MS)
            val currentOverlay = overlayController.current ?: return@launch
            val currentForeground = AppMonitor.foregroundPackage.value
            if (currentForeground == null ||
                currentForeground == currentOverlay.targetPackage ||
                isTransientOverlayPackage(currentForeground) ||
                ForegroundPackages.isHome(packageManager, currentForeground)
            ) {
                return@launch
            }

            overlayController.remove()
            // A decisão continua pendente de propósito: se o usuário voltar ao
            // app antes do TTL, o vigia reexibe o bloqueio na hora, sem depender
            // de um novo evento de acessibilidade (que às vezes não chega).
            interventionAwaitingReturn = true
            interventionMayReturn = true
        }
    }

    private fun isTransientOverlayPackage(packageName: String): Boolean =
        packageName == "com.android.systemui" ||
            packageName == "com.google.android.gms" ||
            packageName == "com.google.android.gsf" ||
            packageName == "android" ||
            packageName in imePackages ||
            (!ForegroundPackages.isUserApp(packageManager, packageName) &&
                !ForegroundPackages.isHome(packageManager, packageName))

    private fun presentDecision(decision: InterventionDecision) {
        if (AppMonitor.foregroundPackage.value == packageName) return
        val scope = when (decision) {
            is InterventionDecision.Challenge -> UsageKey.scopeKey(decision.usageKey)
            is InterventionDecision.Block -> if (decision.reason in setOf(BlockReason.STRICT_SCHEDULE, BlockReason.DAILY_LIMIT, BlockReason.OPEN_LIMIT, BlockReason.COOLDOWN))
                UsageKey.scopeKey(decision.usageKey) else null
            else -> null
        }
        if (scope != null && runningSession?.let { UsageKey.scopeKey(it.usageKey) != scope } == true) finishSession(startCooldown = false)
        lastInterventionAt = SystemClock.elapsedRealtime()
        interventionAwaitingReturn = false
        interventionMayReturn = false
        com.senda.app.core.DiagnosticsLog.add(
            "serviço",
            "apresentar ${InterventionPresentation.describe(decision)}"
        )
        when (decision) {
            is InterventionDecision.Challenge -> showChallengeActivity(decision)
            is InterventionDecision.Block -> showBlockOverlay(decision)
            is InterventionDecision.Allow -> Unit
        }
    }

    private fun clearPendingIntervention() {
        lastIntervention = null
        lastInterventionAt = 0L
        interventionAwaitingReturn = false
        interventionMayReturn = false
    }

    private fun showChallengeActivity(challenge: InterventionDecision.Challenge) {
        clearPendingIntervention()
        overlayController.remove()
        removeBubble()
        if (!ChallengeActivity.show(this, challenge)) goHome()
    }

    private fun showBlockOverlay(block: InterventionDecision.Block) {
        // Cada abertura de app dispara vários eventos de acessibilidade. Se o
        // bloqueio já está na tela, repetir o pedido só faria a janela piscar
        // (remove + recria) e chamaria a Home de novo sem motivo.
        if (isSameBlockVisible(block)) {
            lastIntervention = block
            com.senda.app.core.DiagnosticsLog.add(
                "overlay",
                "bloqueio já visível; nada a refazer"
            )
            return
        }
        // A acessibilidade já manda para a Home antes de avisar. Repetir o HOME
        // aqui reiniciava a animação do launcher por cima do aviso e contribuía
        // para a sensação de tela piscando.
        if (AppMonitor.foregroundPackage.value == block.target.packageName) goHome()
        ChallengeIntervention.store.clear()
        if (isSuppressed(block.target.packageName)) return
        removeBubble()
        if (overlayController.showBlock(block)) {
            lastIntervention = block
            interventionAwaitingReturn = false
            interventionMayReturn = false
        } else {
            clearPendingIntervention()
            goHome()
        }
    }

    private fun isSameBlockVisible(block: InterventionDecision.Block): Boolean {
        val overlay = overlayController.current ?: return false
        return InterventionPresentation.isSameBlockShowing(
            overlayTargetPackage = overlay.targetPackage,
            overlayGroupId = overlay.groupId,
            pending = lastIntervention,
            block = block
        )
    }

    /**
     * Rede de segurança da intervenção.
     *
     * Na One UI a janela de sobreposição é descartada ao abrir a visão geral, e
     * o evento de acessibilidade do app monitorado pode demorar a voltar. Sem
     * esta verificação o bloqueio sumia por vários segundos e dava para usar o
     * app normalmente até algo reavaliar o primeiro plano.
     *
     * A bolha NÃO entra aqui de propósito: recriá-la enquanto a anterior ainda
     * existia fazia aparecer mais de uma bolha na tela.
     */
    private fun startInterventionGuardian() {
        interventionGuardianJob?.cancel()
        interventionGuardianJob = serviceScope.launch {
            while (isActive) {
                delay(
                    if (lastIntervention != null) {
                        OVERLAY_HEALTH_INTERVAL_MS
                    } else {
                        OVERLAY_IDLE_INTERVAL_MS
                    }
                )
                if (runningSession == null && lastIntervention == null && ChallengeIntervention.store.request.value == null && powerManager?.isInteractive != false) {
                    val foreground = AppMonitor.foregroundPackage.value
                    val snapshot = latestPolicySnapshot
                    if (foreground != null && snapshot != null && snapshot.groupsForPackage(foreground).isNotEmpty()) {
                        presentDecision(InterventionEngine().evaluateApp(foreground, snapshot))
                    }
                }
                if (ChallengeIntervention.store.request.value == null) ensureInterventionVisible()
            }
        }
    }

    private fun ensureInterventionVisible() {
        val pending = lastIntervention ?: return

        val now = SystemClock.elapsedRealtime()
        if (lastInterventionAt == 0L) {
            lastInterventionAt = now
        } else if (now - lastInterventionAt > PENDING_INTERVENTION_TTL_MS) {
            clearPendingIntervention()
            return
        }

        val (targetPackage, allowedPackages) = when (pending) {
            is InterventionDecision.Challenge ->
                pending.target.packageName to pending.allowedPackages
            is InterventionDecision.Block ->
                pending.target.packageName to setOf(pending.target.packageName)
            is InterventionDecision.Allow -> return
        }

        if (isSuppressed(targetPackage)) return
        // Never re-evaluate a post-Home notice into a challenge over the launcher.
        if (AppMonitor.foregroundPackage.value !in allowedPackages) {
            if (interventionAwaitingReturn) interventionMayReturn = true
            return
        }
        val appTarget = when (pending) {
            is InterventionDecision.Challenge -> pending.target
            is InterventionDecision.Block -> pending.target
            else -> null
        }
        if (appTarget != null) {
            latestPolicySnapshot?.let { snapshot ->
                val engine = InterventionEngine()
                val fresh = when {
                    appTarget.isWebsite -> engine.evaluateDomain(appTarget.identifier, appTarget.packageName, snapshot)
                    appTarget.identifier != appTarget.packageName -> engine.evaluateKeyword(appTarget.identifier, appTarget.packageName, snapshot)
                    else -> engine.evaluateApp(appTarget.packageName, snapshot)
                }
                val changed = when {
                    pending is InterventionDecision.Block && pending.reason == BlockReason.DAILY_LIMIT &&
                        fresh is InterventionDecision.Challenge && fresh.usageKey == pending.usageKey &&
                        dailyLimitLatch.isReached(pending.usageKey, snapshot.groups.firstOrNull { it.group.id == pending.groupId }
                            ?.let { GroupRuleResolver.resolve(it, LocalDateTime.now()).group.dailyLimitMinutes } ?: 0) -> false
                    fresh is InterventionDecision.Allow -> true
                    fresh is InterventionDecision.Challenge && pending is InterventionDecision.Challenge ->
                        fresh.usageKey != pending.usageKey || fresh.interventionType != pending.interventionType
                    fresh is InterventionDecision.Block && pending is InterventionDecision.Block ->
                        fresh.usageKey != pending.usageKey || fresh.reason != pending.reason
                    else -> true
                }
                if (changed) {
                    if (fresh is InterventionDecision.Allow) {
                        // A regra deixou de valer (horário acabou, grupo desativado):
                        // tira a tela, sem passar por presentDecision.
                        overlayController.remove()
                        clearPendingIntervention()
                    } else {
                        // Não remove nada aqui: presentDecision decide entre trocar
                        // a tela (decisão realmente diferente) ou não tocar em nada.
                        // Remover antes transformava qualquer falso positivo em piscada.
                        presentDecision(fresh)
                    }
                    return
                }
            }
        }
        if (overlayController.current != null) return

        val foreground = AppMonitor.foregroundPackage.value ?: return
        if (foreground != targetPackage && foreground !in allowedPackages) {
            // Viu o usuário fora do app: na volta a intervenção pode reaparecer.
            if (interventionAwaitingReturn) interventionMayReturn = true
            return
        }
        if (isTransientOverlayPackage(foreground)) return

        // Saiu da tela de propósito e ainda não passou por outro app: não
        // reexibe (evita o bloqueio piscando em cima da Home).
        if (interventionAwaitingReturn && !interventionMayReturn) return

        // Sessão ativa liberando o app vence o bloqueio pendente.
        if (runningSession?.allowedPackages?.contains(foreground) == true) return

        when (pending) {
            is InterventionDecision.Challenge -> showChallengeActivity(pending)
            is InterventionDecision.Block -> showBlockOverlay(pending)
            is InterventionDecision.Allow -> Unit
        }
    }

    private fun acceptChallenge(pending: ChallengeRequestStore.Request, reason: String?) {
        val requested = pending.challenge
        val snapshot = latestPolicySnapshot ?: return
        val target = requested.target
        val engine = InterventionEngine()
        val checkedSnapshot = snapshot.copy(clockTamperRemainingSeconds =
            (application as SendaApplication).container.clockGuard.check().remainingSeconds)
        val fresh = when {
            target.isWebsite -> engine.evaluateDomain(target.identifier, target.packageName, checkedSnapshot)
            target.identifier != target.packageName -> engine.evaluateKeyword(target.identifier, target.packageName, checkedSnapshot)
            else -> engine.evaluateApp(target.packageName, checkedSnapshot)
        }
        val latched = fresh is InterventionDecision.Challenge &&
            checkedSnapshot.groups.firstOrNull { it.group.id == fresh.groupId }?.let {
                dailyLimitLatch.isReached(fresh.usageKey, GroupRuleResolver.resolve(it, LocalDateTime.now()).group.dailyLimitMinutes)
            } == true
        if (latched) {
            ChallengeIntervention.store.clear(pending.id)
            goHome()
            return
        }
        if (fresh is InterventionDecision.Allow) {
            clearPendingIntervention()
            ChallengeIntervention.store.accept(pending.id)
            return
        }
        if (fresh !is InterventionDecision.Challenge || fresh.usageKey != requested.usageKey ||
            fresh.interventionType != requested.interventionType || fresh.frictionSeconds != requested.frictionSeconds ||
            fresh.mathDifficulty != requested.mathDifficulty || fresh.mathEquationCount != requested.mathEquationCount) {
            if (fresh is InterventionDecision.Challenge) ChallengeIntervention.store.replace(fresh)
            else ChallengeIntervention.store.clear(pending.id)
            clearPendingIntervention()
            // This is a response to the visible challenge, not an unsolicited intervention in MainActivity.
            when (fresh) {
                is InterventionDecision.Challenge -> showChallengeActivity(fresh)
                is InterventionDecision.Block -> showBlockOverlay(fresh)
                is InterventionDecision.Allow -> Unit
            }
            return
        }
        clearPendingIntervention()
        recordChallengeReason(fresh, reason)
        startSession(
            groupId = fresh.groupId,
            packageName = fresh.target.packageName,
            targetIdentifier = fresh.target.identifier,
            isWebsite = fresh.target.isWebsite,
            durationMinutes = fresh.sessionLimitMinutes,
            cooldownMinutes = fresh.cooldownMinutes,
            mode = fresh.countMode,
            usageKey = fresh.usageKey,
            allowedPackages = fresh.allowedPackages
        )
        // Publish the session before the Activity resumes the target.
        ChallengeIntervention.store.accept(pending.id)
    }

    private fun recordChallengeReason(
        challenge: InterventionDecision.Challenge,
        reason: String?
    ) {
        val text = reason?.trim()?.take(MAX_REASON_LENGTH)?.takeIf { it.isNotEmpty() } ?: return

        val entry = ChallengeReasonEntity(
            packageName = challenge.target.packageName,
            displayName = challenge.target.displayName,
            groupId = challenge.groupId,
            reason = text,
            createdAt = System.currentTimeMillis()
        )

        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                challengeReasonDao.insert(entry)
                challengeReasonDao.prune(MAX_STORED_REASONS)
            }
        }
    }

    private fun recordImpulseAborted(groupId: String) {
        if (groupId.isBlank()) return
        serviceScope.launch {
            usageRepository.recordImpulseAborted(groupId)
        }
    }

    private fun goHome() {
        if (permissions.isAccessibilityEnabled()) {
            sendBroadcast(
                Intent(FocoActions.ACTION_REQUEST_HOME)
                    .setPackage(packageName)
            )
            return
        }

        // Sem acessibilidade o broadcast não chega a ninguém: vai direto para a Home.
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun suppressBriefly(packageName: String) {
        suppressingPackage = packageName
        suppressUntil = SystemClock.elapsedRealtime() + DISMISS_SUPPRESSION_MS
    }

    private fun isSuppressed(packageName: String): Boolean =
        packageName == suppressingPackage && SystemClock.elapsedRealtime() < suppressUntil

    private fun ensureForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            ServiceNotifications.NOTIFICATION_ID_SERVICE,
            buildServiceNotification(),
            type
        )
    }

    private fun updateServiceNotification() {
        notifications.notify(
            ServiceNotifications.NOTIFICATION_ID_SERVICE,
            buildServiceNotification()
        )
    }

    private fun buildServiceNotification(): Notification =
        notifications.serviceNotification(
            runningSession?.let { session ->
                SessionNotification(
                    displayName = session.displayName,
                    remainingSeconds = session.remainingSeconds,
                    durationSeconds = session.durationSeconds
                )
            }
        )

    private fun showBubble() {
        if (overlayController.current != null || ChallengeIntervention.store.request.value != null) return
        if (!Settings.canDrawOverlays(this)) return
        val session = runningSession ?: return
        val foreground = AppMonitor.foregroundPackage.value
        if (foreground != null && foreground !in session.allowedPackages) return
        bubbleController.show()
    }

    private fun updateCurrentPackage(session: RunningSession, packageName: String) {
        if (session.packageName == packageName) return
        session.packageName = packageName
        session.displayName = resolveAppLabel(packageName)
        publishSessionState()
        persistActiveSession(session)
        updateServiceNotification()
    }

    private fun removeBubble() {
        bubbleController.remove()
    }

    private fun startWatchdog() {
        watchdogJob = serviceScope.launch {
            while (isActive) {
                checkAccessibilityService()
                runCatching { heartbeatStore.record() }
                runCatching {
                    (application as SendaApplication).container.reportHistory.recordHeartbeat(
                        System.currentTimeMillis(), SystemClock.elapsedRealtime(), activeSessionStore.currentBootCount(),
                        AppMonitor.accessibilityConnected && permissions.isAccessibilityEnabled() && Settings.canDrawOverlays(this@FocoForegroundService),
                        screenOff = powerManager?.isInteractive == false
                    )
                }
                delay(WATCHDOG_INTERVAL_MS)
            }
        }
    }

    private fun checkAccessibilityService() {
        if (permissions.isAccessibilityEnabled()) {
            if (watchdogNotified) {
                notifications.cancel(ServiceNotifications.NOTIFICATION_ID_WATCHDOG)
                watchdogNotified = false
            }
        } else {
            notifyAccessibilityDisabled()
        }
    }

    private fun notifyAccessibilityDisabled() {
        if (watchdogNotified) return

        notifications.notify(
            ServiceNotifications.NOTIFICATION_ID_WATCHDOG,
            notifications.accessibilityAlert()
        )
        watchdogNotified = true
    }

    @SuppressLint("MissingPermission")
    private fun resolveAppLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)

    private data class RunningSession(
        val groupId: String,
        var packageName: String,
        val targetIdentifier: String,
        val isWebsite: Boolean,
        var displayName: String,
        val mode: SessionCountMode,
        var durationSeconds: Int,
        val counter: SessionUsageCounter,
        var deadlineElapsedRealtime: Long,
        val cooldownMinutes: Int,
        val usageKey: String,
        val allowedPackages: Set<String>,
        var isPaused: Boolean = false,
        val sessionId: String = UUID.randomUUID().toString(),
        var countingPackage: String? = null
    ) {
        val remainingSeconds: Int get() = counter.remainingSeconds
    }

    companion object {
        private const val ACTION_COMPLETE_CHALLENGE = "com.senda.app.action.COMPLETE_CHALLENGE"
        private const val ACTION_CHALLENGE_ABORTED = "com.senda.app.action.CHALLENGE_ABORTED"
        private const val EXTRA_CHALLENGE_ID = "challenge_id"
        private const val EXTRA_CHALLENGE_REASON = "challenge_reason"

        fun completeChallenge(context: Context, id: String, reason: String?) {
            ContextCompat.startForegroundService(context,
                Intent(context, FocoForegroundService::class.java)
                    .setAction(ACTION_COMPLETE_CHALLENGE)
                    .putExtra(EXTRA_CHALLENGE_ID, id)
                    .putExtra(EXTRA_CHALLENGE_REASON, reason))
        }

        fun recordChallengeAborted(context: Context, usageKey: String) {
            ContextCompat.startForegroundService(context,
                Intent(context, FocoForegroundService::class.java)
                    .setAction(ACTION_CHALLENGE_ABORTED)
                    .putExtra(FocoActions.EXTRA_USAGE_KEY, usageKey))
        }

        private const val TICK_INTERVAL_MS = 1000L
        private const val PAUSED_TICK_INTERVAL_MS = 5_000L
        private const val WATCHDOG_INTERVAL_MS = 30_000L
        private const val PERSIST_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_UPDATE_SECONDS = 15
        private const val NOTIFICATION_CRITICAL_SECONDS = 10
        private const val DEFAULT_SESSION_MINUTES = 5
        private const val DEFAULT_COOLDOWN_MINUTES = 10
        private const val MAX_REASON_LENGTH = 500
        private const val MAX_STORED_REASONS = 200
        private const val OVERLAY_DISMISS_GRACE_MS = 450L
        private const val OVERLAY_HEALTH_INTERVAL_MS = 400L
        private const val OVERLAY_IDLE_INTERVAL_MS = 5_000L

        /**
         * Por quanto tempo um bloqueio/desafio continua válido depois de sair da
         * tela (o usuário trocou de app). Serve para reexibir na volta sem
         * precisar de um novo evento de acessibilidade. Depois disso, a próxima
         * abertura é reavaliada normalmente.
         */
        private const val PENDING_INTERVENTION_TTL_MS = 120_000L
        private const val DISMISS_SUPPRESSION_MS = 2_000L
        private val TRANSIENT_FOREGROUND_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "android"
        )

        fun ensureRunning(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FocoForegroundService::class.java)
                    .setAction(FocoActions.ACTION_ENSURE_RUNNING)
            )
        }

        fun startSession(
            context: Context,
            groupId: String,
            packageName: String,
            targetIdentifier: String = packageName,
            isWebsite: Boolean = false,
            durationMinutes: Int,
            cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
            mode: SessionCountMode = SessionCountMode.ACTIVE_SCREEN_TIME,
            usageKey: String? = null
        ) {
            val intent = Intent(context, FocoForegroundService::class.java)
                .setAction(FocoActions.ACTION_START_SESSION)
                .putExtra(FocoActions.EXTRA_GROUP_ID, groupId)
                .putExtra(FocoActions.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(FocoActions.EXTRA_TARGET_IDENTIFIER, targetIdentifier)
                .putExtra(FocoActions.EXTRA_IS_WEBSITE, isWebsite)
                .putExtra(FocoActions.EXTRA_DURATION_MINUTES, durationMinutes)
                .putExtra(FocoActions.EXTRA_COOLDOWN_MINUTES, cooldownMinutes)
                .putExtra(FocoActions.EXTRA_COUNT_MODE, mode.name)
            if (usageKey != null) {
                intent.putExtra(FocoActions.EXTRA_USAGE_KEY, usageKey)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopSession(context: Context) {
            context.startService(
                Intent(context, FocoForegroundService::class.java)
                    .setAction(FocoActions.ACTION_STOP_SESSION)
            )
        }
    }
}
