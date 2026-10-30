package com.senda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.senda.app.R
import com.senda.app.data.local.entity.GroupItemEntity
import com.senda.app.data.preferences.GuardMode
import com.senda.app.di.AppContainer
import com.senda.app.service.FocoForegroundService
import com.senda.app.ui.blocks.BlocksScreen
import com.senda.app.ui.blocks.BlocksViewModel
import com.senda.app.ui.focus.FocusScreen
import com.senda.app.ui.focus.FocusViewModel
import com.senda.app.ui.focus.editor.GroupEditorRoute
import com.senda.app.ui.security.CriticalActionGuardDialog
import com.senda.app.ui.settings.SettingsRoute
import com.senda.app.ui.theme.AccentSage
import com.senda.app.ui.theme.BorderMuted
import com.senda.app.ui.theme.DarkCanvas
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.usage.UsageScreen
import com.senda.app.ui.usage.UsageViewModel
import com.senda.app.ui.components.protectionRepairAction

private enum class MainTab(
    val labelRes: Int,
    val icon: ImageVector
) {
    FOCUS(R.string.tab_home, Icons.Outlined.Security),
    BLOCKS(R.string.tab_blocks, Icons.Outlined.Lock),
    USAGE(R.string.tab_reports, Icons.Outlined.BarChart),
    SETTINGS(R.string.tab_options, Icons.Outlined.Tune)
}

@Composable
fun SendaMainShell(container: AppContainer, starterGoal: com.senda.app.domain.setup.SetupGoal? = null) {
    var selectedTabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.FOCUS.ordinal) }
    var showEditor by rememberSaveable { mutableStateOf(starterGoal != null) }
    var editorGoal by rememberSaveable { mutableStateOf(starterGoal?.name) }
    var editingGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorSessionKey by rememberSaveable { mutableIntStateOf(0) }

    val closeEditor = {
        showEditor = false
        editorGoal = null
    }

    val selectedTab = MainTab.entries.getOrElse(selectedTabOrdinal) { MainTab.FOCUS }
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModel.factory(container)
    )
    val focusState by focusViewModel.uiState.collectAsStateWithLifecycle()

    // Voltar a partir de uma aba secundária retorna ao Início antes de sair do app.
    BackHandler(enabled = selectedTab != MainTab.FOCUS && !showEditor) {
        selectedTabOrdinal = MainTab.FOCUS.ordinal
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = DarkCanvas,
            bottomBar = {
                SendaBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { tab -> selectedTabOrdinal = tab.ordinal }
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        )).togetherWith(fadeOut(tween(140)))
                },
                label = "TabTransition",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (tab) {
                        MainTab.USAGE -> UsageRoute(
                            container = container,
                            onOpenHome = { selectedTabOrdinal = MainTab.FOCUS.ordinal }
                        )

                        MainTab.FOCUS -> FocusScreen(
                            state = focusState,
                            onCreateGroup = {
                                editorGoal = null
                                editingGroupId = null
                                editorSessionKey++
                                showEditor = true
                            },
                            onEditGroup = { groupId ->
                                editorGoal = null
                                editingGroupId = groupId
                                editorSessionKey++
                                showEditor = true
                            },
                            onOpenSettings = {
                                selectedTabOrdinal = MainTab.SETTINGS.ordinal
                            },
                            onRepairProtection = protectionRepairAction(container, focusState.protection) {
                                selectedTabOrdinal = MainTab.SETTINGS.ordinal
                            },
                            onEndSession = {
                                FocoForegroundService.stopSession(context.applicationContext)
                            },
                            onRetry = focusViewModel::retry
                        )

                        MainTab.BLOCKS -> BlocksRoute(container = container)

                        MainTab.SETTINGS -> SettingsRoute(container = container)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showEditor,
            enter = slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it / 6 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(160)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            GroupEditorRoute(
                container = container,
                groupId = editingGroupId,
                sessionKey = editorSessionKey,
                onDone = closeEditor,
                starterGoal = editorGoal?.let { com.senda.app.domain.setup.SetupGoal.valueOf(it) }
            )
        }
    }
}

@Composable
private fun SendaBottomBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit
) {
    Surface(color = DarkCanvas) {
        Column {
            HorizontalDivider(color = BorderMuted, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MainTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    val icon = if (selected) {
                        when (tab) {
                            MainTab.FOCUS -> Icons.Rounded.Security
                            MainTab.BLOCKS -> Icons.Rounded.Lock
                            MainTab.USAGE -> Icons.Rounded.BarChart
                            MainTab.SETTINGS -> Icons.Rounded.Tune
                        }
                    } else {
                        tab.icon
                    }
                    val tint by animateColorAsState(
                        targetValue = if (selected) AccentSage else TextMutedGrey,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tabTint"
                    )
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) {
                            AccentSage.copy(alpha = 0.12f)
                        } else {
                            Color.Transparent
                        },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tabPill"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = if (selected) 22.dp else 21.dp,
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        label = "tabIconSize"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(tab) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillColor)
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRoute(container: AppContainer, onOpenHome: () -> Unit) {
    val viewModel: UsageViewModel = viewModel(
        factory = UsageViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current

    UsageScreen(
        state = state,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onCurrentWeek = viewModel::currentWeek,
        onRetry = viewModel::retry,
        onConfigureGroup = onOpenHome,
        onShareCsv = {
            val csv = com.senda.app.ui.usage.ReportsCsv.build(state)
            val message = if (csv == null) {
                resources.getString(R.string.reports_export_empty)
            } else {
                runCatching {
                    com.senda.app.ui.usage.ReportShare.share(
                        context = context,
                        csv = csv,
                        subject = resources.getString(R.string.reports_share_title)
                    )
                }.exceptionOrNull()?.let { resources.getString(R.string.reports_export_failed) }
            }
            message?.let {
                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    )
}

private data class BlocksGuardRequest(
    val target: String,
    val title: String,
    val description: String,
    val onAuthorized: () -> Unit
)

@Composable
private fun BlocksRoute(container: AppContainer) {
    val viewModel: BlocksViewModel = viewModel(
        factory = BlocksViewModel.factory(container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val protectionStore = container.protectionStore
    var guardRequest by remember { mutableStateOf<BlocksGuardRequest?>(null) }
    val deleteTitle = stringResource(R.string.guard_action_block_delete_title)
    val deleteMessage = stringResource(R.string.guard_action_block_delete_message)

    fun requestGuarded(
        target: String,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        if (protectionStore.guardMode() == GuardMode.NONE) {
            action()
        } else {
            guardRequest = BlocksGuardRequest(target, title, description, action)
        }
    }

    BlocksScreen(
        state = state,
        onSelectGroup = viewModel::selectGroup,
        onSelectType = viewModel::selectType,
        onAddWebsite = viewModel::addWebsite,
        onAddKeyword = viewModel::addKeyword,
        onDeleteItem = { item: GroupItemEntity ->
            requestGuarded(
                target = "blocks:${item.id}",
                title = deleteTitle,
                description = deleteMessage
            ) {
                viewModel.deleteItem(item)
            }
        },
        onRetry = viewModel::retry
    )

    guardRequest?.let { request ->
        CriticalActionGuardDialog(
            protectionStore = protectionStore,
            target = request.target,
            title = request.title,
            description = request.description,
            onDismiss = { guardRequest = null },
            onAuthorized = {
                guardRequest = null
                request.onAuthorized()
            }
        )
    }
}

