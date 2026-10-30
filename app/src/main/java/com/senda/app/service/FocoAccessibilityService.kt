package com.senda.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.senda.app.SendaApplication
import com.senda.app.core.ForegroundPackages
import com.senda.app.core.FocoPackages
import com.senda.app.core.UrlMatcher
import com.senda.app.core.UsageKey
import com.senda.app.domain.policy.BlockReason
import com.senda.app.domain.policy.InterventionDecision
import com.senda.app.domain.policy.InterventionEngine
import com.senda.app.domain.policy.PolicySnapshot
import com.senda.app.domain.policy.SessionOverridePolicy
import com.senda.app.ui.overlay.ChallengeActivity
import com.senda.app.domain.scan.ScanThrottle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.util.Collections
import java.util.IdentityHashMap

class FocoAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transientWindowPackages = setOf(
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
    )
    private val interventionEngine = InterventionEngine()
    private val container by lazy { (application as SendaApplication).container }
    private val dailyLimitLatch by lazy { container.dailyLimitLatch }
    private var policySnapshot: PolicySnapshot? = null
    private var lastUrlSignature: String? = null
    private var lastKeywordSignature: String? = null
    private val scanThrottle = ScanThrottle()
    private var appLockReceiverRegistered = false
    private var pendingPackage: String? = null
    private var windowLookupJob: Job? = null
    private var eventGeneration = 0L
    private val blockDedup = InterventionPresentation.DedupWindow(BLOCK_DEDUP_MS)

    private val appLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FocoActions.ACTION_APP_LOCK -> {
                    val lockedPackage = intent.getStringExtra(FocoActions.EXTRA_PACKAGE_NAME)
                    val foregroundPackage = AppMonitor.foregroundPackage.value
                    if (lockedPackage == null || foregroundPackage == lockedPackage) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }

                FocoActions.ACTION_REQUEST_HOME -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }

                FocoActions.ACTION_LEAVE_BLOCKED_PAGE -> {
                    // Sai da página bloqueada antes de ir para a Home. Sem
                    // isso o navegador reabre na mesma aba e o bloqueio volta.
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Handler(Looper.getMainLooper()).postDelayed(
                        { performGlobalAction(GLOBAL_ACTION_HOME) },
                        LEAVE_BLOCKED_PAGE_DELAY_MS
                    )
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppMonitor.setAccessibilityConnected(true)

        registerAppLockReceiver()

        serviceScope.launch {
            container.policyRepository.observeSnapshot().collect { snapshot ->
                val firstSnapshot = policySnapshot == null
                policySnapshot = snapshot
                if (firstSnapshot) {
                    val packageToEvaluate = pendingPackage
                    pendingPackage = null
                    if (packageToEvaluate != null && AppMonitor.foregroundPackage.value == packageToEvaluate) {
                        evaluateForegroundPackage(
                            packageName = packageToEvaluate,
                            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        )
                    }
                }
            }
        }

        runCatching { FocoForegroundService.ensureRunning(this) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) eventGeneration++
        val generation = eventGeneration
        val windowChange = event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        if (windowChange && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            event.windowChanges and (AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or
                AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or AccessibilityEvent.WINDOWS_CHANGE_ADDED or
                AccessibilityEvent.WINDOWS_CHANGE_REMOVED or AccessibilityEvent.WINDOWS_CHANGE_LAYER) == 0) return
        val sourcePackage = event.packageName?.toString()
        if (windowChange && (sourcePackage == null || sourcePackage in transientWindowPackages || sourcePackage == "android")) {
            // System window events need resolution, but never block the accessibility callback on root IPC.
            if (windowLookupJob?.isActive == true) return
            windowLookupJob = serviceScope.launch {
                val focused = withContext(Dispatchers.Default) { focusedApplicationPackage() }
                if (eventGeneration == generation && focused != null) {
                    processPackage(focused, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null, true)
                }
            }
            return
        }
        val packageName = sourcePackage ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && packageName in transientWindowPackages) {
            // Only an identifiable overview task click is evidence of a target launch.
            if (event.className?.toString()?.contains("TaskView") == true) {
                val labels = event.text.map { it.toString() } + listOfNotNull(event.contentDescription?.toString())
                val targets = policySnapshot?.groups.orEmpty().flatMap { it.items }
                    .filter { it.targetType == com.senda.app.data.local.entity.TargetType.APP &&
                        (it.targetIdentifier in labels || it.displayName in labels) }
                    .map { it.targetIdentifier }.distinct()
                targets.singleOrNull()?.let { processPackage(it, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, null, false) }
            }
            return
        }
        processPackage(packageName, if (windowChange || event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED else event.eventType, event.className?.toString(), windowChange)
    }

    private fun processPackage(packageName: String, eventType: Int, className: String?, windowChange: Boolean) {
        if (packageName != this.packageName && ChallengeActivity.ownsInputFocus) return
        if (packageName == this.packageName) {
            // Floating notices also use our package; only native activities leave the target.
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                className in setOf("com.senda.app.MainActivity", ChallengeActivity::class.java.name)) {
                lastUrlSignature = null
                lastKeywordSignature = null
                AppMonitor.onForegroundPackage(packageName)
            }
            return
        }
        if (packageName in transientWindowPackages) {
            if (windowChange) AppMonitor.onForegroundPackage(packageName)
            return
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && !FocoPackages.isBrowser(packageName)) return

        // Aba de notificações e painéis de fabricante não são apps do usuário.
        if (!ForegroundPackages.isUserApp(packageManager, packageName)) {
            // A Home e a visão geral são o contrário: o usuário saiu do app de
            // verdade. Registrar o pacote faz a sobreposição sair da frente.
            if (ForegroundPackages.isHome(packageManager, packageName)) {
                pendingPackage = null
                lastUrlSignature = null
                lastKeywordSignature = null
                // Voltou para a Home: a próxima abertura é uma intervenção nova.
                blockDedup.reset()
                AppMonitor.onForegroundPackage(packageName)
            }
            return
        }

        if (FocoPackages.isSensitive(packageName)) {
            AppMonitor.onForegroundPackage(packageName)
            AppMonitor.onSensitivePackage(packageName)
            return
        }

        AppMonitor.onForegroundPackage(packageName)

        if (policySnapshot == null) {
            pendingPackage = packageName
            runCatching { FocoForegroundService.ensureRunning(this) }
            return
        }

        evaluateForegroundPackage(
            packageName = packageName,
            eventType = eventType
        )
    }

    private fun focusedApplicationPackage(): String? {
        val currentWindows = runCatching { windows }.getOrNull() ?: return null
        try {
            val focused = currentWindows.firstOrNull { it.isFocused }
                ?: currentWindows.firstOrNull { it.isActive }
                ?: return null
            if (focused.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD ||
                focused.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return null
            val root = runCatching { focused.root }.getOrNull() ?: return null
            return try { root.packageName?.toString() } finally { NodeRecycler().recycle(root) }
        } finally {
            currentWindows.forEach(::recycleWindow)
        }
    }

    private fun evaluateForegroundPackage(
        packageName: String,
        eventType: Int
    ) {
        val clockStatus = container.clockGuard.check()
        val snapshot = (policySnapshot ?: return).copy(
            clockTamperRemainingSeconds = clockStatus.remainingSeconds
        )
        val appPolicies = snapshot.groupsForPackage(packageName)
        val shouldInspectBrowser = FocoPackages.isBrowser(packageName) &&
            (snapshot.hasBlockedDomains || snapshot.hasKeywords)

        if (appPolicies.isEmpty() && !shouldInspectBrowser) return

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (appPolicies.isNotEmpty()) {
                    val decision = interventionEngine.evaluateApp(packageName, snapshot)
                    val sessionAllows = activeSessionAllowsPackage(packageName)

                    // A sessão libera o app, exceto se o bloqueio rígido começou.
                    if (!sessionAllows || SessionOverridePolicy.overridesActiveSession(decision)) {
                        AppMonitor.onMonitoredPackage(packageName)
                        handleDecision(decision)
                        if (decision !is InterventionDecision.Allow) return
                    }
                }
                if (shouldInspectBrowser) {
                    inspectBrowser(packageName, snapshot, isNavigation = true)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (shouldInspectBrowser) {
                    inspectBrowser(packageName, snapshot, isNavigation = false)
                }
            }

            else -> Unit
        }
    }

    private fun inspectBrowser(
        packageName: String,
        snapshot: PolicySnapshot,
        isNavigation: Boolean
    ) {
        val now = SystemClock.elapsedRealtime()
        val allowed = if (isNavigation) {
            scanThrottle.allowNavigation(now)
        } else {
            scanThrottle.allowContent(now)
        }
        if (!allowed) return

        val recycler = NodeRecycler()
        val roots = currentRoots(recycler)
        if (roots.isEmpty()) return

        val snapshots = mutableListOf<BrowserSnapshot>()
        try {
            val needWebContentRoot = snapshot.hasKeywords
            for (root in roots) {
                snapshots += scanTree(root, needWebContentRoot, recycler)
            }

            for (browserSnapshot in snapshots) {
                if (inspectUrl(packageName, snapshot, browserSnapshot.rawUrl)) return
            }

            if (needWebContentRoot) {
                for (browserSnapshot in snapshots) {
                    val blocked = inspectKeywords(
                        packageName = packageName,
                        snapshot = snapshot,
                        webContentRoot = browserSnapshot.webContentRoot,
                        recycler = recycler
                    )
                    if (blocked) return
                }
            }
        } finally {
            roots.forEach(recycler::recycle)
            snapshots.forEach { recycler.recycle(it.webContentRoot) }
        }
    }

    private fun currentRoots(recycler: NodeRecycler): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        runCatching { rootInActiveWindow }.getOrNull()?.let(roots::add)
        runCatching {
            windows.forEach { window ->
                try {
                    runCatching { window.root }.getOrNull()?.let(roots::add)
                } finally {
                    recycleWindow(window)
                }
            }
        }

        val uniqueRoots = mutableListOf<AccessibilityNodeInfo>()
        for (root in roots) {
            if (uniqueRoots.any { it.windowId == root.windowId }) {
                recycler.recycle(root)
            } else {
                uniqueRoots.add(root)
            }
        }
        return uniqueRoots
    }

    @Suppress("DEPRECATION")
    private fun recycleWindow(window: AccessibilityWindowInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { window.recycle() }
        }
    }

    private fun inspectUrl(
        packageName: String,
        snapshot: PolicySnapshot,
        rawUrl: String?
    ): Boolean {
        val url = rawUrl ?: return false
        val host = UrlMatcher.extractHost(url) ?: return false

        if (activeSessionAllowsHost(packageName, host)) {
            lastUrlSignature = null
            return false
        }

        val decision = interventionEngine.evaluateDomain(host, packageName, snapshot)
        if (decision is InterventionDecision.Allow) {
            lastUrlSignature = null
            return false
        }

        val signature = "$packageName|$host|${decisionKey(decision)}"
        if (signature == lastUrlSignature) return true
        lastUrlSignature = signature

        notifyBlockedSite(packageName, url, host)
        handleDecision(decision)
        return true
    }

    private fun inspectKeywords(
        packageName: String,
        snapshot: PolicySnapshot,
        webContentRoot: AccessibilityNodeInfo?,
        recycler: NodeRecycler
    ): Boolean {
        val keywords = snapshot.allKeywords
        if (keywords.isEmpty()) return false

        val contentRoot = webContentRoot ?: return false
        val visibleText = collectVisibleText(contentRoot, recycler)
        if (visibleText.isBlank()) return false

        for (keyword in keywords) {
            if (!visibleText.contains(keyword, ignoreCase = true)) continue
            if (activeSessionAllowsKeyword(packageName, keyword)) continue

            val decision = interventionEngine.evaluateKeyword(keyword, packageName, snapshot)
            if (decision is InterventionDecision.Allow) continue

            val signature = "$packageName|$keyword|${decisionKey(decision)}"
            if (signature == lastKeywordSignature) return true
            lastKeywordSignature = signature

            handleDecision(decision)
            return true
        }

        return false
    }

    private fun notifyBlockedSite(packageName: String, rawUrl: String, host: String) {
        AppMonitor.onBlockedSite(packageName = packageName, url = rawUrl, domain = host)
        sendBroadcast(
            Intent(FocoActions.ACTION_BLOCKED_SITE_DETECTED)
                .setPackage(this.packageName)
                .putExtra(FocoActions.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(FocoActions.EXTRA_URL, rawUrl)
                .putExtra(FocoActions.EXTRA_DOMAIN, host)
        )
    }

    private fun collectVisibleText(
        root: AccessibilityNodeInfo,
        recycler: NodeRecycler
    ): String {
        val builder = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0

        try {
            while (queue.isNotEmpty() && visited < MAX_NODES_TO_INSPECT) {
                val node = queue.removeFirst()
                visited++

                try {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        builder.append(text).append(' ')
                        if (builder.length >= MAX_KEYWORD_TEXT_LENGTH) break
                    }

                    for (index in 0 until node.childCount) {
                        runCatching { node.getChild(index) }
                            .getOrNull()
                            ?.let(queue::addLast)
                    }
                } finally {
                    recycler.recycle(node)
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                recycler.recycle(queue.removeFirst())
            }
        }

        return builder.toString()
    }

    /**
     * Percorre a árvore uma única vez e extrai a primeira URL de barra de
     * endereço e, quando necessário, o primeiro nó de conteúdo web.
     *
     * O nó de conteúdo web é transferido para o chamador e reciclado depois da
     * leitura das palavras-chave ou no finally de `inspectBrowser`.
     */
    private fun scanTree(
        root: AccessibilityNodeInfo,
        needWebContentRoot: Boolean,
        recycler: NodeRecycler
    ): BrowserSnapshot {
        var rawUrl: String? = null
        var webContentRoot: AccessibilityNodeInfo? = null
        var completed = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)

        try {
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES_TO_INSPECT) {
                val node = queue.removeFirst()
                visited++

                try {
                    if (rawUrl == null) {
                        rawUrl = urlFromNode(node)
                    }

                    if (needWebContentRoot &&
                        webContentRoot == null &&
                        isWebContentNode(node)
                    ) {
                        webContentRoot = node
                    }

                    if (rawUrl != null &&
                        (!needWebContentRoot || webContentRoot != null)
                    ) {
                        break
                    }

                    for (index in 0 until node.childCount) {
                        runCatching { node.getChild(index) }
                            .getOrNull()
                            ?.let(queue::addLast)
                    }
                } finally {
                    if (node !== root && node !== webContentRoot) {
                        recycler.recycle(node)
                    }
                }
            }

            completed = true
            return BrowserSnapshot(rawUrl, webContentRoot)
        } finally {
            if (!completed) recycler.recycle(webContentRoot)
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node !== webContentRoot) recycler.recycle(node)
            }
        }
    }

    private fun isWebContentNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        return className.contains("WebView", ignoreCase = true) ||
            className.contains("GeckoView", ignoreCase = true)
    }

    private fun urlFromNode(node: AccessibilityNodeInfo): String? {
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val looksLikeAddressBar = node.isEditable ||
            className == "android.widget.EditText" ||
            viewId.contains("url") ||
            viewId.contains("omnibox") ||
            viewId.contains("location") ||
            viewId.contains("address") ||
            viewId.contains("search_box") ||
            description.contains("search or type url", ignoreCase = true) ||
            description.contains("pesquisar ou digitar", ignoreCase = true) ||
            description.contains("endereço", ignoreCase = true) ||
            description.contains("address", ignoreCase = true)
        if (!looksLikeAddressBar) return null

        node.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && UrlMatcher.extractHost(it) != null }
            ?.let { return it }

        node.contentDescription?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && UrlMatcher.extractHost(it) != null }
            ?.let { return it }

        return null
    }

    private class BrowserSnapshot(
        val rawUrl: String?,
        val webContentRoot: AccessibilityNodeInfo?
    )

    private class NodeRecycler {
        private val recycled: MutableSet<AccessibilityNodeInfo> =
            Collections.newSetFromMap(IdentityHashMap<AccessibilityNodeInfo, Boolean>())

        @Suppress("DEPRECATION")
        fun recycle(node: AccessibilityNodeInfo?) {
            if (node == null || !recycled.add(node)) return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { node.recycle() }
            }
        }
    }

    private fun handleDecision(decision: InterventionDecision) {
        when (val effective = applyDailyLimitLatch(decision)) {
            is InterventionDecision.Allow -> Unit

            is InterventionDecision.Challenge -> {
                com.senda.app.core.DiagnosticsLog.add(
                    "acessibilidade",
                    "desafio ${effective.target.packageName} ${effective.interventionType.name}"
                )
                if (!ChallengeActivity.show(this, effective)) {
                    com.senda.app.core.DiagnosticsLog.add(
                        "acessibilidade",
                        "falha ao abrir desafio; indo para a Home"
                    )
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }

            is InterventionDecision.Block -> {
                if (isDuplicateBlock(effective)) {
                    com.senda.app.core.DiagnosticsLog.add(
                        "acessibilidade",
                        "bloqueio duplicado ignorado ${effective.target.packageName}"
                    )
                    return
                }
                com.senda.app.core.DiagnosticsLog.add(
                    "acessibilidade",
                    InterventionPresentation.describe(effective)
                )
                // Eject first; notification, service startup and Compose are secondary.
                performGlobalAction(GLOBAL_ACTION_HOME)
                ChallengeIntervention.store.clear()
                sendBlockedBroadcast(effective)
                if (Settings.canDrawOverlays(this)) {
                    AppMonitor.onAppBlocked(effective)
                    runCatching { FocoForegroundService.ensureRunning(this) }
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
    }

    /**
     * Abrir um app gera vários eventos de acessibilidade (splash, atividade
     * principal, diálogos). Sem essa janela cada evento repetia a ejeção para a
     * Home e pedia um novo aviso, que o serviço recriava — o bloqueio piscava.
     */
    private fun isDuplicateBlock(block: InterventionDecision.Block): Boolean =
        blockDedup.isDuplicate(
            key = InterventionPresentation.blockSignature(block),
            nowMillis = SystemClock.elapsedRealtime()
        )

    /**
     * Uma vez atingido o limite diário de um grupo, ele não pode voltar a
     * oferecer desafio no mesmo dia. Sem isso, uma defasagem de segundos entre o
     * uso gravado e o uso real trocava a tela de "limite diário" pela tela de
     * respiração e liberava o app por engano.
     */
    private fun applyDailyLimitLatch(decision: InterventionDecision): InterventionDecision {
        val snapshot = policySnapshot ?: return decision
        if (decision is InterventionDecision.Block && decision.reason == BlockReason.DAILY_LIMIT) {
            dailyLimitLatch.markReached(decision.usageKey, decision.dailyLimitMinutes)
            return decision
        }
        if (decision !is InterventionDecision.Challenge) return decision
        val target = decision.target
        val now = java.time.LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        val latched = snapshot.groupsForPackage(target.packageName).map { policy ->
            policy to com.senda.app.domain.policy.GroupRuleResolver.resolve(policy, now)
        }.firstOrNull { (_, rule) ->
            val key = if (rule.group.sharedLimitAcrossApps) rule.scopeKey else UsageKey.forTarget(rule.scopeKey, target.identifier)
            rule.group.isEnabled && (rule.group.temporaryDisableUntil ?: 0L) <= nowMillis &&
                rule.scheduleType == com.senda.app.data.local.entity.ScheduleType.LIMITED &&
                dailyLimitLatch.isReached(key, rule.group.dailyLimitMinutes)
        } ?: return decision
        val rule = latched.second
        val key = if (rule.group.sharedLimitAcrossApps) rule.scopeKey else UsageKey.forTarget(rule.scopeKey, target.identifier)
        return InterventionDecision.Block(target, rule.group.id, rule.group.name, BlockReason.DAILY_LIMIT,
            usageKey = key, dailyLimitMinutes = rule.group.dailyLimitMinutes)
    }
    private fun sendBlockedBroadcast(block: InterventionDecision.Block) {
        sendBroadcast(
            Intent(FocoActions.ACTION_APP_BLOCKED)
                .setPackage(this.packageName)
                .putExtra(FocoActions.EXTRA_GROUP_ID, block.groupId)
                .putExtra(FocoActions.EXTRA_PACKAGE_NAME, block.target.packageName)
                .putExtra(FocoActions.EXTRA_DISPLAY_NAME, block.target.displayName)
                .putExtra(FocoActions.EXTRA_BLOCK_REASON, block.reason.name)
                .putExtra(
                    FocoActions.EXTRA_COOLDOWN_SECONDS,
                    block.cooldownRemainingSeconds
                )
        )
    }

    private fun decisionKey(decision: InterventionDecision): String = when (decision) {
        is InterventionDecision.Allow -> "allow"
        is InterventionDecision.Challenge -> "challenge:${decision.groupId}"
        is InterventionDecision.Block -> "block:${decision.reason}"
    }

    private fun activeSessionAllowsPackage(packageName: String): Boolean {
        val session = FocoSessionState.session.value ?: return false
        val policy = policySnapshot?.groups?.firstOrNull { it.group.id == session.groupId } ?: return false
        val rule = com.senda.app.domain.policy.GroupRuleResolver.resolve(policy, java.time.LocalDateTime.now())
        return session.remainingSeconds > 0 && packageName in session.allowedPackages &&
            UsageKey.scopeKey(session.usageKey) == rule.scopeKey &&
            rule.scheduleType == com.senda.app.data.local.entity.ScheduleType.LIMITED
    }

    private fun activeSessionAllowsHost(packageName: String, host: String): Boolean {
        val session = FocoSessionState.session.value ?: return false
        return session.remainingSeconds > 0 &&
            session.packageName == packageName &&
            session.isWebsite &&
            UrlMatcher.isBlocked(host, setOf(session.targetIdentifier))
    }

    private fun activeSessionAllowsKeyword(packageName: String, keyword: String): Boolean {
        val session = FocoSessionState.session.value ?: return false
        return session.remainingSeconds > 0 &&
            session.packageName == packageName &&
            !session.isWebsite &&
            session.targetIdentifier.equals(keyword, ignoreCase = true)
    }

    private fun registerAppLockReceiver() {
        if (appLockReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            appLockReceiver,
            IntentFilter().apply {
                addAction(FocoActions.ACTION_APP_LOCK)
                addAction(FocoActions.ACTION_REQUEST_HOME)
                addAction(FocoActions.ACTION_LEAVE_BLOCKED_PAGE)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        appLockReceiverRegistered = true
    }

    override fun onInterrupt() {
        AppMonitor.clearForegroundPackage()
    }

    override fun onDestroy() {
        AppMonitor.setAccessibilityConnected(false)
        AppMonitor.clearForegroundPackage()
        if (appLockReceiverRegistered) {
            runCatching { unregisterReceiver(appLockReceiver) }
            appLockReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MAX_NODES_TO_INSPECT = 200
        private const val MAX_KEYWORD_TEXT_LENGTH = 20_000
        private const val LEAVE_BLOCKED_PAGE_DELAY_MS = 250L

        /** Janela para agrupar os vários eventos de uma mesma abertura de app. */
        private const val BLOCK_DEDUP_MS = 1_200L
    }
}