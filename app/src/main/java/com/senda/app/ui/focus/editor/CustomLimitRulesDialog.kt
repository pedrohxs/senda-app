package com.senda.app.ui.focus.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.senda.app.R
import com.senda.app.data.local.entity.*
import com.senda.app.data.repository.InstalledApp
import com.senda.app.ui.components.SendaPrimaryButton
import com.senda.app.ui.theme.*

@Composable
fun CustomLimitRulesDialog(
    initial: LimitRules, apps: List<InstalledApp> = emptyList(),
    onDismiss: () -> Unit, onConfirm: (LimitRules) -> Unit
) {
    var rules by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(emptySet<String>()) }
    var selectAlternative by remember { mutableStateOf(false) }
    fun validity(key: String, valid: Boolean) { invalid = if (valid) invalid - key else invalid + key }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = DarkCanvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.schedule_cancel), color = TextMutedGrey) }
                    Text(stringResource(R.string.custom_rules_title), Modifier.weight(1f).padding(12.dp),
                        style = MaterialTheme.typography.titleLarge, color = TextWarmWhite)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.custom_rules_description), color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
                    RuleSection("Tempo de uso", "Defina quanto espaço este limite terá no seu dia.") {
                    RuleNumber(R.string.custom_daily_label, rules.dailyLimitMinutes, 0..1440, { rules = rules.copy(dailyLimitMinutes = it) }) { validity("daily", it) }
                    RuleNumber(R.string.custom_session_label, rules.sessionLimitMinutes, 1..240, { rules = rules.copy(sessionLimitMinutes = it) }) { validity("session", it) }
                    RuleNumber(R.string.custom_cooldown_label, rules.cooldownMinutes, 0..1440, { rules = rules.copy(cooldownMinutes = it) }) { validity("cooldown", it) }
                    RuleNumber(R.string.custom_opens_label, rules.maxDailyOpens, 0..999, { rules = rules.copy(maxDailyOpens = it) }) { validity("opens", it) }
                    Text(stringResource(R.string.custom_zero_explanation), color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    RuleSection("Como contar", "Escolha como o tempo e as aberturas são compartilhados.") {
                    RuleToggle(stringResource(R.string.custom_shared), rules.sharedLimitAcrossApps) { rules = rules.copy(sharedLimitAcrossApps = it) }
                    RuleToggle(stringResource(R.string.custom_wall_clock), rules.sessionCountMode == SessionCountMode.WALL_CLOCK_WINDOW) {
                        rules = rules.copy(sessionCountMode = if (it) SessionCountMode.WALL_CLOCK_WINDOW else SessionCountMode.ACTIVE_SCREEN_TIME)
                    }
                    Text(stringResource(if (rules.sessionCountMode == SessionCountMode.WALL_CLOCK_WINDOW) R.string.rules_wall_count else R.string.rules_active_count),
                        color = TextMutedGrey, style = MaterialTheme.typography.bodySmall)
                    }
                    RuleSection("Antes de abrir", "Uma pausa para escolher com intenção.") {
                    Text(stringResource(R.string.editor_challenge_sheet_title), color = TextWarmWhite, style = MaterialTheme.typography.titleMedium)
                    InterventionType.entries.forEach { type ->
                        val label = stringResource(when (type) {
                            InterventionType.BREATHING -> R.string.editor_intervention_breathing
                            InterventionType.MATH -> R.string.editor_intervention_math
                            InterventionType.REASON -> R.string.editor_intervention_reason
                        })
                        FilterChip(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), selected = rules.interventionType == type, onClick = {
                            rules = rules.copy(interventionType = type); invalid = invalid - setOf("breathing", "progressive", "math")
                        }, label = { Text(label) })
                    }
                    if (rules.interventionType == InterventionType.BREATHING) {
                        RuleNumber(R.string.custom_breathing, rules.breathingSeconds, 5..600, { rules = rules.copy(breathingSeconds = it) }) { validity("breathing", it) }
                        RuleToggle(stringResource(R.string.custom_progressive), rules.progressiveFrictionEnabled) { rules = rules.copy(progressiveFrictionEnabled = it) }
                        RuleNumber(R.string.custom_progressive_seconds, rules.progressiveSecondsPerOpen, 1..60, { rules = rules.copy(progressiveSecondsPerOpen = it) }) { validity("progressive", it) }
                    }
                    if (rules.interventionType == InterventionType.MATH) {
                        RuleNumber(R.string.custom_math_count, rules.mathEquationCount, 3..20, { rules = rules.copy(mathEquationCount = it) }) { validity("math", it) }
                        MathDifficulty.entries.forEach { difficulty ->
                            FilterChip(selected = rules.mathDifficulty == difficulty, onClick = { rules = rules.copy(mathDifficulty = difficulty) },
                                label = { Text(stringResource(when (difficulty) {
                                    MathDifficulty.EASY -> R.string.editor_math_easy
                                    MathDifficulty.MEDIUM -> R.string.editor_math_medium
                                    MathDifficulty.HARD -> R.string.editor_math_hard
                                })) })
                        }
                    }
                    }
                    RuleSection("Uma alternativa", "Escolha para onde ir quando decidir não continuar.") {
                    TextButton(onClick = { selectAlternative = true }) {
                        Text(stringResource(R.string.custom_alternative,
                            apps.firstOrNull { it.packageName == rules.alternativeAppPackage }?.label ?: stringResource(R.string.editor_alternative_none)), color = AccentSage)
                    }
                    if (rules.alternativeAppPackage != null) TextButton(onClick = { rules = rules.copy(alternativeAppPackage = null) }) {
                        Text(stringResource(R.string.editor_remove_alternative), color = TextMutedGrey)
                    }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Column(Modifier.padding(20.dp)) {
                    if (invalid.isNotEmpty()) Text(stringResource(R.string.custom_invalid_fields), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    SendaPrimaryButton(stringResource(R.string.custom_apply), { onConfirm(rules) }, enabled = invalid.isEmpty() && rules.isValid)
                }
            }
        }
    }
    if (selectAlternative) AppPickerDialog(stringResource(R.string.app_picker_alternative_title), apps,
        setOfNotNull(rules.alternativeAppPackage), false, singleSelect = true,
        onToggle = { rules = rules.copy(alternativeAppPackage = it.packageName); selectAlternative = false },
        onDismiss = { selectAlternative = false })
}

@Composable
private fun RuleNumber(labelRes: Int, value: Int, range: IntRange, onValue: (Int) -> Unit, onValidity: (Boolean) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val valid = text.toIntOrNull()?.let { it in range } == true
    OutlinedTextField(value = text, onValueChange = { input ->
        if (input.length <= 4 && input.all { it.isDigit() }) {
            text = input
            val number = input.toIntOrNull()
            onValidity(number != null && number in range)
            if (number != null && number in range) onValue(number)
        }
    }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(labelRes)) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = BorderMuted, focusedBorderColor = AccentSage),
        supportingText = { Text(if (valid && value == 0 && range.first == 0) (if (labelRes == R.string.custom_cooldown_label) "Sem intervalo" else "Sem limite") else stringResource(R.string.custom_range, range.first, range.last)) },
        isError = !valid, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun RuleToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).toggleable(value = value, role = Role.Switch, onValueChange = onChange), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = TextSoft, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun RuleSection(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = CardSurface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, BorderMuted)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextWarmWhite)
            Text(description, style = MaterialTheme.typography.bodySmall, color = TextMutedGrey)
            content()
        }
    }
}
