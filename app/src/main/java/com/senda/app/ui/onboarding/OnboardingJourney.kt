package com.senda.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.senda.app.R
import com.senda.app.data.permissions.SendaPermissionState
import com.senda.app.domain.setup.SetupGoal
import com.senda.app.ui.components.SendaMark
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.theme.*

@Composable
fun OnboardingScreen(
    state: SendaPermissionState, onAccessibility: () -> Unit, onOverlay: () -> Unit,
    onNotifications: () -> Unit, onDeviceAdmin: () -> Unit, onBatteryOptimization: () -> Unit,
    onAppDetails: () -> Unit, onContinue: (SetupGoal) -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goalName by rememberSaveable { mutableStateOf(SetupGoal.REDUCE_DISTRACTIONS.name) }
    val goal = SetupGoal.valueOf(goalName)
    BackHandler(enabled = step > 0) { step-- }
    if (step == 1) {
        OnboardingPermissionsScreen(state, onAccessibility, onOverlay, onNotifications, onDeviceAdmin,
            onBatteryOptimization, onAppDetails, onContinue = { step = 2 }, onBack = { step = 0 })
        return
    }
    Surface(Modifier.fillMaxSize(), color = DarkCanvas) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SendaMark(size = 44.dp)
                    Text(stringResource(R.string.setup_step, step + 1), color = TextMutedGrey, style = MaterialTheme.typography.labelMedium)
                }
                Text(stringResource(if (step == 0) R.string.setup_welcome_title else R.string.setup_ready_title),
                    style = MaterialTheme.typography.headlineLarge, color = TextWarmWhite)
                Text(stringResource(if (step == 0) R.string.setup_welcome_description else R.string.setup_ready_description),
                    color = TextMutedGrey, style = MaterialTheme.typography.bodyMedium)
                if (step == 0) {
                    SetupGoal.entries.forEach { choice ->
                        val label = stringResource(when (choice) {
                            SetupGoal.REDUCE_DISTRACTIONS -> R.string.setup_goal_reduce
                            SetupGoal.STUDY -> R.string.setup_goal_study
                            SetupGoal.EVENING_REST -> R.string.setup_goal_rest
                        })
                        val description = stringResource(when (choice) {
                            SetupGoal.REDUCE_DISTRACTIONS -> R.string.setup_goal_reduce_description
                            SetupGoal.STUDY -> R.string.setup_goal_study_description
                            SetupGoal.EVENING_REST -> R.string.setup_goal_rest_description
                        })
                        Surface(onClick = { goalName = choice.name }, modifier = Modifier.fillMaxWidth(),
                            color = CardSurface, shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (choice == goal) AccentSage else BorderMuted)) {
                            Row(Modifier.padding(16.dp)) {
                                RadioButton(selected = choice == goal, onClick = null)
                                Column(Modifier.padding(start = 12.dp)) {
                                    Text(label, color = TextWarmWhite, style = MaterialTheme.typography.titleMedium)
                                    Text(description, color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Surface(color = AccentSage.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.setup_suggestion_title), color = AccentSage, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.setup_suggestion, goal.dailyMinutes, goal.sessionMinutes, goal.cooldownMinutes),
                            color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.setup_review_hint), color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.setup_local_privacy), color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (step == 2) TextButton(onClick = { step = 1 }) { Text(stringResource(R.string.setup_back), color = TextMutedGrey) }
                SendaPrimaryButton(stringResource(if (step == 0) R.string.setup_prepare else R.string.setup_create_group),
                    { if (step == 0) step = 1 else if (state.essentialGranted) onContinue(goal) },
                    enabled = step == 0 || state.essentialGranted)
            }
        }
    }
}
