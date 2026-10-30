package com.senda.app.ui.security

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senda.app.R
import com.senda.app.data.preferences.EditUnlockState
import com.senda.app.data.preferences.GuardMode
import com.senda.app.data.preferences.PinVerification
import com.senda.app.data.preferences.ProtectionStore
import com.senda.app.ui.theme.CardSurface
import com.senda.app.ui.theme.TextMutedGrey
import com.senda.app.ui.theme.TextWarmWhite
import kotlinx.coroutines.delay
import java.util.Locale

enum class PinDialogMode {
    SET,
    CHANGE,
    REMOVE
}

@Composable
fun CriticalActionGuardDialog(
    protectionStore: ProtectionStore,
    target: String,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onAuthorized: () -> Unit
) {
    val resources = LocalResources.current
    val protection by protectionStore.state.collectAsStateWithLifecycle()
    val mode = when {
        protection.guardianPinSet -> GuardMode.PIN
        protection.editDelayEnabled -> GuardMode.DELAY
        else -> GuardMode.NONE
    }

    if (mode == GuardMode.NONE) {
        LaunchedEffect(Unit) { onAuthorized() }
        return
    }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(mode) {
        if (mode == GuardMode.DELAY) {
            while (true) {
                nowElapsed = SystemClock.elapsedRealtime()
                delay(1_000L)
            }
        }
    }

    val unlockState = if (mode == GuardMode.DELAY) {
        protectionStore.editUnlockState(target, nowElapsed)
    } else {
        EditUnlockState.NotStarted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                if (mode == GuardMode.PIN) {
                    stringResource(R.string.guard_pin_title)
                } else {
                    title
                }
            )
        },
        text = {
            Column {
                Text(
                    if (mode == GuardMode.PIN) {
                        stringResource(R.string.guard_pin_message)
                    } else {
                        description
                    }
                )

                if (mode == GuardMode.PIN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMutedGrey
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PinField(
                        value = pin,
                        label = stringResource(R.string.pin_current_label),
                        onValueChange = {
                            pin = it
                            error = null
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    when (unlockState) {
                        EditUnlockState.NotStarted -> Text(
                            stringResource(
                                R.string.guard_delay_message,
                                protectionStore.editDelayMinutes
                            )
                        )

                        is EditUnlockState.Waiting -> {
                            Text(stringResource(R.string.guard_delay_started))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.guard_delay_remaining,
                                    formatRemaining(unlockState.remainingSeconds)
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWarmWhite
                            )
                        }

                        EditUnlockState.Ready -> Text(stringResource(R.string.guard_delay_ready))
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when (mode) {
                GuardMode.PIN -> TextButton(
                    enabled = pin.length == PIN_LENGTH,
                    onClick = {
                        when (val result = protectionStore.verifyGuardianPin(pin)) {
                            PinVerification.Success -> onAuthorized()
                            is PinVerification.Wrong -> {
                                error = resources.getString(
                                    R.string.guard_pin_wrong,
                                    result.attemptsLeft
                                )
                                pin = ""
                            }

                            is PinVerification.Locked -> {
                                error = resources.getString(
                                    R.string.guard_pin_locked,
                                    result.remainingSeconds
                                )
                                pin = ""
                            }

                            PinVerification.NotConfigured -> onAuthorized()
                        }
                    }
                ) {
                    Text(stringResource(R.string.guard_pin_confirm))
                }

                GuardMode.DELAY -> when (unlockState) {
                    EditUnlockState.NotStarted -> TextButton(
                        onClick = {
                            protectionStore.requestEditDelay(target)
                            nowElapsed = SystemClock.elapsedRealtime()
                        }
                    ) {
                        Text(stringResource(R.string.guard_delay_start))
                    }

                    is EditUnlockState.Waiting -> TextButton(
                        enabled = false,
                        onClick = {}
                    ) {
                        Text(
                            stringResource(
                                R.string.guard_delay_remaining,
                                formatRemaining(unlockState.remainingSeconds)
                            )
                        )
                    }

                    EditUnlockState.Ready -> TextButton(
                        onClick = {
                            protectionStore.consumeEditUnlock(target)
                            onAuthorized()
                        }
                    ) {
                        Text(stringResource(R.string.guard_delay_continue))
                    }
                }

                GuardMode.NONE -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (mode == GuardMode.DELAY) {
                        stringResource(R.string.guard_delay_close)
                    } else {
                        stringResource(R.string.settings_cancel)
                    },
                    color = TextMutedGrey
                )
            }
        }
    )
}

@Composable
fun GuardianPinDialog(
    mode: PinDialogMode,
    protectionStore: ProtectionStore,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val resources = LocalResources.current
    val requiresCurrent = mode == PinDialogMode.CHANGE || mode == PinDialogMode.REMOVE
    val requiresNew = mode == PinDialogMode.SET || mode == PinDialogMode.CHANGE

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }

    // O código de recuperação só aparece uma vez; a tela de confirmação vem antes
    // de fechar o diálogo para o usuário não perder o acesso ao PIN.
    newRecoveryCode?.let { code ->
        RecoveryCodeDialog(
            code = code,
            onAcknowledge = {
                newRecoveryCode = null
                onChanged()
            }
        )
        return
    }

    fun validateCurrentPin(): Boolean {
        if (!requiresCurrent) return true

        return when (val result = protectionStore.verifyGuardianPin(currentPin)) {
            PinVerification.Success -> true
            is PinVerification.Wrong -> {
                error = resources.getString(R.string.pin_current_wrong)
                false
            }

            is PinVerification.Locked -> {
                error = resources.getString(R.string.guard_pin_locked, result.remainingSeconds)
                false
            }

            PinVerification.NotConfigured -> true
        }
    }

    val title = when (mode) {
        PinDialogMode.SET -> R.string.pin_dialog_set_title
        PinDialogMode.CHANGE -> R.string.pin_dialog_change_title
        PinDialogMode.REMOVE -> R.string.pin_dialog_remove_title
    }
    val message = when (mode) {
        PinDialogMode.SET -> R.string.pin_dialog_set_message
        PinDialogMode.CHANGE -> R.string.pin_dialog_change_message
        PinDialogMode.REMOVE -> R.string.pin_dialog_remove_message
    }
    val confirmLabel = when (mode) {
        PinDialogMode.SET -> R.string.settings_guardian_pin_set
        PinDialogMode.CHANGE -> R.string.settings_guardian_pin_change
        PinDialogMode.REMOVE -> R.string.settings_guardian_pin_remove
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                Text(stringResource(message))

                if (requiresCurrent) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PinField(
                        value = currentPin,
                        label = stringResource(R.string.pin_current_label),
                        onValueChange = {
                            currentPin = it
                            error = null
                        }
                    )
                }

                if (requiresNew) {
                    Spacer(modifier = Modifier.height(12.dp))
                    PinField(
                        value = newPin,
                        label = stringResource(R.string.pin_new_label),
                        onValueChange = {
                            newPin = it
                            error = null
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PinField(
                        value = confirmPin,
                        label = stringResource(R.string.pin_confirm_label),
                        onValueChange = {
                            confirmPin = it
                            error = null
                        }
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (requiresCurrent && currentPin.length != PIN_LENGTH) {
                        error = resources.getString(R.string.pin_invalid)
                        return@TextButton
                    }
                    if (requiresNew && (newPin.length != PIN_LENGTH ||
                            confirmPin.length != PIN_LENGTH)
                    ) {
                        error = resources.getString(R.string.pin_invalid)
                        return@TextButton
                    }
                    if (requiresNew && newPin != confirmPin) {
                        error = resources.getString(R.string.pin_mismatch)
                        return@TextButton
                    }
                    if (!validateCurrentPin()) return@TextButton

                    when (mode) {
                        PinDialogMode.SET, PinDialogMode.CHANGE -> {
                            newRecoveryCode = protectionStore.setGuardianPin(newPin)
                        }

                        PinDialogMode.REMOVE -> {
                            when (val result = protectionStore.removeGuardianPin(currentPin)) {
                                PinVerification.Success, PinVerification.NotConfigured -> {
                                    onChanged()
                                }

                                is PinVerification.Wrong -> {
                                    error = resources.getString(R.string.pin_current_wrong)
                                }

                                is PinVerification.Locked -> {
                                    error = resources.getString(
                                        R.string.guard_pin_locked,
                                        result.remainingSeconds
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_cancel),
                    color = TextMutedGrey
                )
            }
        }
    )
}

@Composable
private fun PinField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            onValueChange(candidate.filter(Char::isDigit).take(PIN_LENGTH))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation()
    )
}

/** Mostra o código de recuperação recém-criado, uma única vez. */
@Composable
fun RecoveryCodeDialog(code: String, onAcknowledge: () -> Unit) {
    AlertDialog(
        onDismissRequest = onAcknowledge,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = { Text(stringResource(R.string.pin_recovery_code_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_recovery_code_message))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium,
                    color = com.senda.app.ui.theme.AccentSage
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.pin_recovery_code_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedGrey
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(stringResource(R.string.pin_recovery_code_ack))
            }
        }
    )
}

/**
 * Redefine o PIN com o código de recuperação (esqueci o PIN).
 *
 * A verificação usa o mesmo contador de tentativas do PIN, então o código também
 * tem limite de erros e bloqueio temporário.
 */
@Composable
fun PinRecoveryDialog(
    protectionStore: ProtectionStore,
    onDismiss: () -> Unit,
    onRecovered: () -> Unit
) {
    val resources = LocalResources.current
    var code by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }

    newRecoveryCode?.let { generated ->
        RecoveryCodeDialog(
            code = generated,
            onAcknowledge = {
                newRecoveryCode = null
                onRecovered()
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        titleContentColor = TextWarmWhite,
        textContentColor = TextMutedGrey,
        title = {
            Text(
                stringResource(
                    if (verified) R.string.pin_recovery_verified_title
                    else R.string.pin_recovery_title
                )
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (verified) R.string.pin_recovery_verified_message
                        else R.string.pin_recovery_message
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))
                if (!verified) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { candidate ->
                            code = candidate.uppercase().take(MAX_RECOVERY_INPUT)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pin_recovery_label)) },
                        placeholder = { Text("ABCD-EFGH") },
                        singleLine = true
                    )
                } else {
                    PinField(
                        value = newPin,
                        label = stringResource(R.string.pin_new_label),
                        onValueChange = {
                            newPin = it
                            error = null
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PinField(
                        value = confirmPin,
                        label = stringResource(R.string.pin_confirm_label),
                        onValueChange = {
                            confirmPin = it
                            error = null
                        }
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!verified) {
                        when (val result = protectionStore.verifyRecoveryCode(code)) {
                            PinVerification.Success -> {
                                verified = true
                                error = null
                            }

                            is PinVerification.Wrong -> {
                                error = resources.getString(
                                    R.string.guard_pin_wrong,
                                    result.attemptsLeft
                                )
                                code = ""
                            }

                            is PinVerification.Locked -> {
                                error = resources.getString(
                                    R.string.guard_pin_locked,
                                    result.remainingSeconds
                                )
                                code = ""
                            }

                            PinVerification.NotConfigured -> {
                                error = resources.getString(R.string.pin_recovery_missing)
                            }
                        }
                        return@TextButton
                    }

                    if (newPin.length != PIN_LENGTH || confirmPin.length != PIN_LENGTH) {
                        error = resources.getString(R.string.pin_invalid)
                        return@TextButton
                    }
                    if (newPin != confirmPin) {
                        error = resources.getString(R.string.pin_mismatch)
                        return@TextButton
                    }

                    newRecoveryCode = protectionStore.setGuardianPin(newPin)
                }
            ) {
                Text(
                    stringResource(
                        if (verified) R.string.settings_guardian_pin_set
                        else R.string.pin_recovery_confirm
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_cancel),
                    color = TextMutedGrey
                )
            }
        }
    )
}

private fun formatRemaining(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private const val PIN_LENGTH = 4
private const val MAX_RECOVERY_INPUT = 9
