package com.senda.app.data.preferences

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.senda.app.domain.security.ClockDriftDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

enum class GuardMode {
    NONE,
    PIN,
    DELAY
}

sealed interface EditUnlockState {
    data object NotStarted : EditUnlockState
    data class Waiting(val remainingSeconds: Long) : EditUnlockState
    data object Ready : EditUnlockState
}

sealed interface PinVerification {
    data object Success : PinVerification
    data object NotConfigured : PinVerification
    data class Wrong(val attemptsLeft: Int) : PinVerification
    data class Locked(val remainingSeconds: Long) : PinVerification
}

data class ProtectionState(
    val clockGuardEnabled: Boolean,
    val editDelayEnabled: Boolean,
    val guardianPinSet: Boolean,
    val clockTamperRemainingSeconds: Long
)

class ProtectionStore(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    val clockGuardEnabled: Boolean
        get() = preferences.getBoolean(KEY_CLOCK_GUARD_ENABLED, true)

    val editDelayEnabled: Boolean
        get() = preferences.getBoolean(KEY_EDIT_DELAY_ENABLED, false)

    val guardianPinSet: Boolean
        get() = !preferences.getString(KEY_PIN_HASH, null).isNullOrBlank()

    val editDelayMinutes: Int
        get() = EDIT_DELAY_MINUTES

    fun guardMode(): GuardMode = when {
        guardianPinSet -> GuardMode.PIN
        editDelayEnabled -> GuardMode.DELAY
        else -> GuardMode.NONE
    }

    fun refreshState() {
        _state.value = buildState()
    }

    fun setClockGuardEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_CLOCK_GUARD_ENABLED, enabled)
            .apply()
        clearClockTamper()
        saveClockAnchor(
            ClockDriftDetector.Anchor(
                wallMillis = System.currentTimeMillis(),
                elapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                bootCount = currentBootCount(),
                zoneOffsetSeconds = currentZoneOffsetSeconds()
            )
        )
        refreshState()
    }

    fun setEditDelayEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_EDIT_DELAY_ENABLED, enabled)
            .apply()
        if (!enabled) {
            clearPendingUnlocks()
        }
        refreshState()
    }

    fun currentBootCount(): Int = Settings.Global.getInt(
        appContext.contentResolver,
        Settings.Global.BOOT_COUNT,
        -1
    )

    fun clockAnchor(): ClockDriftDetector.Anchor? {
        if (!preferences.contains(KEY_ANCHOR_WALL)) return null
        val wallMillis = preferences.getLong(KEY_ANCHOR_WALL, 0L)
        // Âncoras antigas não têm fuso; usar o fuso atual mantém o comportamento
        // anterior (qualquer salto de relógio continua sendo detectado).
        val zoneOffset = if (preferences.contains(KEY_ANCHOR_ZONE)) {
            preferences.getInt(KEY_ANCHOR_ZONE, 0)
        } else {
            currentZoneOffsetSeconds(wallMillis)
        }
        return ClockDriftDetector.Anchor(
            wallMillis = wallMillis,
            elapsedRealtime = preferences.getLong(KEY_ANCHOR_ELAPSED, 0L),
            bootCount = preferences.getInt(KEY_ANCHOR_BOOT, -1),
            zoneOffsetSeconds = zoneOffset
        )
    }

    fun saveClockAnchor(anchor: ClockDriftDetector.Anchor) {
        preferences.edit()
            .putLong(KEY_ANCHOR_WALL, anchor.wallMillis)
            .putLong(KEY_ANCHOR_ELAPSED, anchor.elapsedRealtime)
            .putInt(KEY_ANCHOR_BOOT, anchor.bootCount)
            .putInt(KEY_ANCHOR_ZONE, anchor.zoneOffsetSeconds)
            .apply()
    }

    /** Offset do fuso atual em segundos, para descontar viagens/DST da trava. */
    fun currentZoneOffsetSeconds(
        wallMillis: Long = System.currentTimeMillis()
    ): Int = java.time.ZoneId.systemDefault()
        .rules
        .getOffset(java.time.Instant.ofEpochMilli(wallMillis))
        .totalSeconds

    fun clockTamperRemainingSeconds(
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        bootCount: Int = currentBootCount()
    ): Long {
        val tamperBoot = preferences.getInt(KEY_TAMPER_BOOT, -1)
        if (tamperBoot != bootCount) {
            removeClockTamper()
            return 0L
        }

        val until = preferences.getLong(KEY_TAMPER_UNTIL, 0L)
        if (until <= 0L) return 0L

        val remaining = ((until - nowElapsedRealtime + 999L) / 1000L).coerceAtLeast(0L)
        if (remaining <= 0L) {
            removeClockTamper()
        }
        return remaining
    }

    fun startClockTamper(
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        bootCount: Int = currentBootCount(),
        durationSeconds: Long = CLOCK_TAMPER_DURATION_SECONDS
    ) {
        preferences.edit()
            .putLong(KEY_TAMPER_UNTIL, nowElapsedRealtime + durationSeconds * 1000L)
            .putInt(KEY_TAMPER_BOOT, bootCount)
            .apply()
        refreshState()
    }

    fun clearClockTamper() {
        removeClockTamper()
        refreshState()
    }

    private fun removeClockTamper() {
        if (!preferences.contains(KEY_TAMPER_UNTIL)) return
        preferences.edit()
            .remove(KEY_TAMPER_UNTIL)
            .remove(KEY_TAMPER_BOOT)
            .apply()
    }

    fun requestEditDelay(
        target: String,
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        bootCount: Int = currentBootCount()
    ) {
        if (editUnlockState(target, nowElapsedRealtime, bootCount) != EditUnlockState.NotStarted) {
            return
        }

        val until = nowElapsedRealtime + editDelayMinutes * 60_000L
        preferences.edit()
            .putString(unlockKey(target), "$until|$bootCount")
            .apply()
    }

    fun editUnlockState(
        target: String,
        nowElapsedRealtime: Long = android.os.SystemClock.elapsedRealtime(),
        bootCount: Int = currentBootCount()
    ): EditUnlockState {
        val raw = preferences.getString(unlockKey(target), null)
            ?: return EditUnlockState.NotStarted
        val parts = raw.split('|')
        val until = parts.getOrNull(0)?.toLongOrNull()
            ?: return EditUnlockState.NotStarted
        val storedBoot = parts.getOrNull(1)?.toIntOrNull()
            ?: return EditUnlockState.NotStarted

        if (storedBoot != bootCount) {
            consumeEditUnlock(target)
            return EditUnlockState.NotStarted
        }

        val remaining = until - nowElapsedRealtime
        return if (remaining > 0L) {
            EditUnlockState.Waiting((remaining + 999L) / 1000L)
        } else {
            EditUnlockState.Ready
        }
    }

    fun consumeEditUnlock(target: String) {
        preferences.edit()
            .remove(unlockKey(target))
            .apply()
    }

    @Synchronized
    fun verifyGuardianPin(pin: String): PinVerification {
        val storedHash = preferences.getString(KEY_PIN_HASH, null)
        val storedSalt = preferences.getString(KEY_PIN_SALT, null)
        if (storedHash.isNullOrBlank() || storedSalt.isNullOrBlank()) {
            return PinVerification.NotConfigured
        }

        lockRemainingSeconds()?.let { return PinVerification.Locked(it) }

        val salt = runCatching { decode(storedSalt) }.getOrNull()
            ?: return PinVerification.NotConfigured

        val valid = runCatching {
            val expected = decode(storedHash)
            val actual = when (pinScheme()) {
                SCHEME_PBKDF2 -> pbkdf2(
                    value = pin,
                    salt = salt,
                    iterations = preferences.getInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
                )

                // Hashes antigos (SHA-256 de uma passada) continuam válidos e são
                // atualizados no primeiro acerto.
                else -> legacySha256(pin, salt)
            }
            MessageDigest.isEqual(expected, actual) to (pinScheme() != SCHEME_PBKDF2)
        }.getOrElse { false to false }

        if (valid.first) {
            if (valid.second) upgradePinHash(pin, salt)
            clearPinFailures()
            return PinVerification.Success
        }

        return registerPinFailure()
    }

    /** Há um código de recuperação guardado para este PIN? */
    val recoveryConfigured: Boolean
        get() = !preferences.getString(KEY_RECOVERY_HASH, null).isNullOrBlank() &&
            !preferences.getString(KEY_RECOVERY_SALT, null).isNullOrBlank()

    @Synchronized
    fun verifyRecoveryCode(code: String): PinVerification {
        val storedHash = preferences.getString(KEY_RECOVERY_HASH, null)
        val storedSalt = preferences.getString(KEY_RECOVERY_SALT, null)
        if (storedHash.isNullOrBlank() || storedSalt.isNullOrBlank()) {
            return PinVerification.NotConfigured
        }

        lockRemainingSeconds()?.let { return PinVerification.Locked(it) }

        val salt = runCatching { decode(storedSalt) }.getOrNull()
            ?: return PinVerification.NotConfigured

        val normalized = com.senda.app.domain.security.RecoveryCode.normalize(code)
        val valid = com.senda.app.domain.security.RecoveryCode.isWellFormed(normalized) &&
            runCatching {
                MessageDigest.isEqual(
                    decode(storedHash),
                    pbkdf2(
                        value = normalized,
                        salt = salt,
                        iterations = preferences.getInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
                    )
                )
            }.getOrDefault(false)

        if (valid) {
            clearPinFailures()
            return PinVerification.Success
        }

        return registerPinFailure()
    }

    @Synchronized
    fun setGuardianPin(pin: String): String {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit)) {
            "O PIN deve ter 4 dígitos."
        }

        val random = SecureRandom()
        val salt = ByteArray(PIN_SALT_BYTES).also(random::nextBytes)
        val recoveryCode = com.senda.app.domain.security.RecoveryCode.generateSecure(random)
        val recoverySalt = ByteArray(PIN_SALT_BYTES).also(random::nextBytes)

        preferences.edit()
            .putString(KEY_PIN_SALT, encode(salt))
            .putString(KEY_PIN_HASH, encode(pbkdf2(pin, salt, PBKDF2_ITERATIONS)))
            .putString(KEY_PIN_KDF, SCHEME_PBKDF2)
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .putString(
                KEY_RECOVERY_HASH,
                encode(
                    pbkdf2(
                        value = com.senda.app.domain.security.RecoveryCode.normalize(recoveryCode),
                        salt = recoverySalt,
                        iterations = PBKDF2_ITERATIONS
                    )
                )
            )
            .putString(KEY_RECOVERY_SALT, encode(recoverySalt))
            .putInt(KEY_PIN_FAILURES, 0)
            .remove(KEY_PIN_LOCKED_UNTIL)
            .remove(KEY_PIN_LOCKED_BOOT)
            .apply()
        refreshState()
        return com.senda.app.domain.security.RecoveryCode.format(recoveryCode)
    }

    @Synchronized
    fun removeGuardianPin(pin: String): PinVerification {
        return when (val result = verifyGuardianPin(pin)) {
            PinVerification.Success, PinVerification.NotConfigured -> {
                preferences.edit()
                    .remove(KEY_PIN_HASH)
                    .remove(KEY_PIN_SALT)
                    .remove(KEY_PIN_KDF)
                    .remove(KEY_PIN_ITERATIONS)
                    .remove(KEY_RECOVERY_HASH)
                    .remove(KEY_RECOVERY_SALT)
                    .putInt(KEY_PIN_FAILURES, 0)
                    .remove(KEY_PIN_LOCKED_UNTIL)
                    .remove(KEY_PIN_LOCKED_BOOT)
                    .apply()
                refreshState()
                PinVerification.Success
            }

            else -> result
        }
    }

    private fun pinScheme(): String = preferences.getString(KEY_PIN_KDF, null) ?: SCHEME_LEGACY

    private fun upgradePinHash(pin: String, salt: ByteArray) {
        preferences.edit()
            .putString(KEY_PIN_HASH, encode(pbkdf2(pin, salt, PBKDF2_ITERATIONS)))
            .putString(KEY_PIN_KDF, SCHEME_PBKDF2)
            .putInt(KEY_PIN_ITERATIONS, PBKDF2_ITERATIONS)
            .apply()
    }

    private fun lockRemainingSeconds(): Long? {
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val bootCount = currentBootCount()
        val lockedUntil = preferences.getLong(KEY_PIN_LOCKED_UNTIL, 0L)
        val lockedBoot = preferences.getInt(KEY_PIN_LOCKED_BOOT, -1)
        if (lockedBoot != bootCount || lockedUntil <= nowElapsed) return null
        return ((lockedUntil - nowElapsed + 999L) / 1000L).coerceAtLeast(1L)
    }

    private fun clearPinFailures() {
        preferences.edit()
            .putInt(KEY_PIN_FAILURES, 0)
            .remove(KEY_PIN_LOCKED_UNTIL)
            .remove(KEY_PIN_LOCKED_BOOT)
            .apply()
    }

    private fun registerPinFailure(): PinVerification {
        val failures = preferences.getInt(KEY_PIN_FAILURES, 0) + 1
        if (failures >= MAX_PIN_ATTEMPTS) {
            val nowElapsed = android.os.SystemClock.elapsedRealtime()
            preferences.edit()
                .putInt(KEY_PIN_FAILURES, 0)
                .putLong(KEY_PIN_LOCKED_UNTIL, nowElapsed + PIN_LOCK_SECONDS * 1000L)
                .putInt(KEY_PIN_LOCKED_BOOT, currentBootCount())
                .apply()
            return PinVerification.Locked(PIN_LOCK_SECONDS)
        }

        preferences.edit()
            .putInt(KEY_PIN_FAILURES, failures)
            .apply()
        return PinVerification.Wrong(MAX_PIN_ATTEMPTS - failures)
    }

    private fun initialState(): ProtectionState {
        migrateEditDelayDefault()
        return buildState()
    }

    private fun migrateEditDelayDefault() {
        if (preferences.contains(KEY_EDIT_DELAY_DEFAULT_MIGRATED)) return

        // A versão anterior ativava o atraso por padrão. Nesta versão ele é opt-in.
        preferences.edit()
            .putBoolean(KEY_EDIT_DELAY_ENABLED, false)
            .putBoolean(KEY_EDIT_DELAY_DEFAULT_MIGRATED, true)
            .apply()
    }

    private fun buildState(): ProtectionState = ProtectionState(
        clockGuardEnabled = clockGuardEnabled,
        editDelayEnabled = editDelayEnabled,
        guardianPinSet = guardianPinSet,
        clockTamperRemainingSeconds = clockTamperRemainingSeconds()
    )

    private fun clearPendingUnlocks() {
        val unlockKeys = preferences.all.keys.filter { key ->
            key.startsWith(KEY_UNLOCK_PREFIX)
        }
        if (unlockKeys.isEmpty()) return

        preferences.edit().apply {
            unlockKeys.forEach(::remove)
        }.apply()
    }

    private fun unlockKey(target: String): String = "$KEY_UNLOCK_PREFIX$target"

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest()
    }

    private fun legacySha256(pin: String, salt: ByteArray): ByteArray = hashPin(pin, salt)

    /** PBKDF2-HMAC-SHA256: bem mais caro de atacar do que um SHA-256 de uma passada. */
    private fun pbkdf2(value: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(
            value.toCharArray(),
            salt,
            iterations.coerceAtLeast(MIN_PBKDF2_ITERATIONS),
            PBKDF2_KEY_BITS
        )
        return try {
            javax.crypto.SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFERENCES_NAME = "senda_protection"
        const val PIN_LENGTH = 4
        const val PIN_SALT_BYTES = 16
        const val MAX_PIN_ATTEMPTS = 5
        const val PIN_LOCK_SECONDS = 60L
        const val EDIT_DELAY_MINUTES = 15
        const val CLOCK_TAMPER_DURATION_SECONDS = 30L * 60L

        /** Custo do KDF. Alto o bastante para atrapalhar força bruta, baixo para não travar a UI. */
        const val PBKDF2_ITERATIONS = 120_000
        const val MIN_PBKDF2_ITERATIONS = 10_000
        const val PBKDF2_KEY_BITS = 256
        const val SCHEME_PBKDF2 = "pbkdf2v1"
        const val SCHEME_LEGACY = "sha256v1"

        const val KEY_CLOCK_GUARD_ENABLED = "clock_guard_enabled"
        const val KEY_EDIT_DELAY_ENABLED = "edit_delay_enabled"
        const val KEY_EDIT_DELAY_DEFAULT_MIGRATED = "edit_delay_default_migrated_v2"
        const val KEY_PIN_HASH = "guardian_pin_hash"
        const val KEY_PIN_SALT = "guardian_pin_salt"
        const val KEY_PIN_KDF = "guardian_pin_kdf"
        const val KEY_PIN_ITERATIONS = "guardian_pin_iterations"
        const val KEY_RECOVERY_HASH = "guardian_recovery_hash"
        const val KEY_RECOVERY_SALT = "guardian_recovery_salt"
        const val KEY_PIN_FAILURES = "guardian_pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "guardian_pin_locked_until"
        const val KEY_PIN_LOCKED_BOOT = "guardian_pin_locked_boot"
        const val KEY_ANCHOR_WALL = "clock_anchor_wall"
        const val KEY_ANCHOR_ELAPSED = "clock_anchor_elapsed"
        const val KEY_ANCHOR_BOOT = "clock_anchor_boot"
        const val KEY_ANCHOR_ZONE = "clock_anchor_zone"
        const val KEY_TAMPER_UNTIL = "clock_tamper_until"
        const val KEY_TAMPER_BOOT = "clock_tamper_boot"
        const val KEY_UNLOCK_PREFIX = "edit_unlock_"
    }
}
