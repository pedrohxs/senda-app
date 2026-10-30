package com.senda.app.data.preferences

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.senda.app.data.local.entity.SessionCountMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

data class PersistedActiveSession(
    val groupId: String,
    val packageName: String,
    val targetIdentifier: String,
    val isWebsite: Boolean,
    val mode: SessionCountMode,
    val durationSeconds: Int,
    val remainingSeconds: Int,
    val deadlineElapsedRealtime: Long,
    val cooldownMinutes: Int,
    val savedAtElapsedRealtime: Long,
    val bootCount: Int,
    val usageKey: String = groupId,
    val allowedPackages: Set<String> = setOf(packageName),
    val sessionId: String = UUID.randomUUID().toString(),
    val remainingMillis: Long = remainingSeconds * 1000L,
    val savedAtEpochMillis: Long = 0L,
    val usageMillisByDate: Map<String, Long> = emptyMap(),
    val finished: Boolean = false,
    val cooldownUntilEpochMillis: Long? = null
)

class ActiveSessionStore(private val context: Context) {

    suspend fun save(session: PersistedActiveSession) {
        context.sendaSessionDataStore.edit { preferences ->
            preferences[PRESENT] = true
            preferences[GROUP_ID] = session.groupId
            preferences[PACKAGE_NAME] = session.packageName
            preferences[TARGET_IDENTIFIER] = session.targetIdentifier
            preferences[IS_WEBSITE] = session.isWebsite
            preferences[MODE] = session.mode.name
            preferences[DURATION_SECONDS] = session.durationSeconds
            preferences[REMAINING_SECONDS] = session.remainingSeconds
            preferences[DEADLINE_ELAPSED_REALTIME] = session.deadlineElapsedRealtime
            preferences[COOLDOWN_MINUTES] = session.cooldownMinutes
            preferences[SAVED_AT_ELAPSED_REALTIME] = session.savedAtElapsedRealtime
            preferences[BOOT_COUNT] = session.bootCount
            preferences[USAGE_KEY] = session.usageKey
            preferences[ALLOWED_PACKAGES] = session.allowedPackages
            preferences[SESSION_ID] = session.sessionId
            preferences[REMAINING_MILLIS] = session.remainingMillis
            preferences[SAVED_AT_EPOCH_MILLIS] = session.savedAtEpochMillis
            preferences[USAGE_BY_DATE] = session.usageMillisByDate.map { (date, millis) -> "$date=$millis" }.toSet()
            preferences[FINISHED] = session.finished
            if (session.cooldownUntilEpochMillis == null) preferences.remove(COOLDOWN_UNTIL)
            else preferences[COOLDOWN_UNTIL] = session.cooldownUntilEpochMillis
        }
    }

    suspend fun load(): PersistedActiveSession? =
        parse(context.sendaSessionDataStore.data.first())

    /** Sessão atual, observável: a tela inicial acompanha início e fim sem recarregar. */
    fun observe(): Flow<PersistedActiveSession?> =
        context.sendaSessionDataStore.data.map(::parse)

    private fun parse(preferences: Preferences): PersistedActiveSession? {
        if (preferences[PRESENT] != true) return null

        val mode = preferences[MODE]
            ?.let { value -> runCatching { SessionCountMode.valueOf(value) }.getOrNull() }
            ?: return null

        return PersistedActiveSession(
            groupId = preferences[GROUP_ID] ?: return null,
            packageName = preferences[PACKAGE_NAME] ?: return null,
            targetIdentifier = preferences[TARGET_IDENTIFIER] ?: return null,
            isWebsite = preferences[IS_WEBSITE] ?: false,
            mode = mode,
            durationSeconds = preferences[DURATION_SECONDS] ?: return null,
            remainingSeconds = preferences[REMAINING_SECONDS] ?: return null,
            deadlineElapsedRealtime = preferences[DEADLINE_ELAPSED_REALTIME] ?: return null,
            cooldownMinutes = preferences[COOLDOWN_MINUTES] ?: 0,
            savedAtElapsedRealtime = preferences[SAVED_AT_ELAPSED_REALTIME] ?: return null,
            bootCount = preferences[BOOT_COUNT] ?: return null,
            usageKey = preferences[USAGE_KEY] ?: preferences[GROUP_ID] ?: return null,
            allowedPackages = preferences[ALLOWED_PACKAGES]?.takeIf { it.isNotEmpty() }
                ?: setOf(preferences[PACKAGE_NAME] ?: return null),
            // Old versions did not retain accounting checkpoints. Keep their remaining
            // time, but do not replay historical usage whose committed amount is unknown.
            sessionId = preferences[SESSION_ID] ?: "legacy-${preferences[GROUP_ID]}-${preferences[SAVED_AT_ELAPSED_REALTIME]}",
            remainingMillis = preferences[REMAINING_MILLIS]
                ?: (preferences[REMAINING_SECONDS] ?: return null) * 1000L,
            savedAtEpochMillis = preferences[SAVED_AT_EPOCH_MILLIS] ?: 0L,
            usageMillisByDate = preferences[USAGE_BY_DATE].orEmpty().mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                val millis = parts.getOrNull(1)?.toLongOrNull()?.takeIf { it >= 0L }
                if (millis == null) null else parts[0] to millis
            }.toMap(),
            finished = preferences[FINISHED] ?: false,
            cooldownUntilEpochMillis = preferences[COOLDOWN_UNTIL]
        )
    }

    suspend fun clear() {
        context.sendaSessionDataStore.edit { preferences ->
            preferences.remove(PRESENT)
            preferences.remove(GROUP_ID)
            preferences.remove(PACKAGE_NAME)
            preferences.remove(TARGET_IDENTIFIER)
            preferences.remove(IS_WEBSITE)
            preferences.remove(MODE)
            preferences.remove(DURATION_SECONDS)
            preferences.remove(REMAINING_SECONDS)
            preferences.remove(DEADLINE_ELAPSED_REALTIME)
            preferences.remove(COOLDOWN_MINUTES)
            preferences.remove(SAVED_AT_ELAPSED_REALTIME)
            preferences.remove(BOOT_COUNT)
            preferences.remove(USAGE_KEY)
            preferences.remove(ALLOWED_PACKAGES)
            preferences.remove(SESSION_ID)
            preferences.remove(REMAINING_MILLIS)
            preferences.remove(SAVED_AT_EPOCH_MILLIS)
            preferences.remove(USAGE_BY_DATE)
            preferences.remove(FINISHED)
            preferences.remove(COOLDOWN_UNTIL)
        }
    }

    fun currentBootCount(): Int = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        -1
    )

    private companion object {
        val PRESENT = booleanPreferencesKey("active_session_present")
        val GROUP_ID = stringPreferencesKey("active_session_group_id")
        val PACKAGE_NAME = stringPreferencesKey("active_session_package_name")
        val TARGET_IDENTIFIER = stringPreferencesKey("active_session_target_identifier")
        val IS_WEBSITE = booleanPreferencesKey("active_session_is_website")
        val MODE = stringPreferencesKey("active_session_mode")
        val DURATION_SECONDS = intPreferencesKey("active_session_duration_seconds")
        val REMAINING_SECONDS = intPreferencesKey("active_session_remaining_seconds")
        val DEADLINE_ELAPSED_REALTIME = longPreferencesKey("active_session_deadline_elapsed")
        val COOLDOWN_MINUTES = intPreferencesKey("active_session_cooldown_minutes")
        val SAVED_AT_ELAPSED_REALTIME = longPreferencesKey("active_session_saved_at_elapsed")
        val BOOT_COUNT = intPreferencesKey("active_session_boot_count")
        val USAGE_KEY = stringPreferencesKey("active_session_usage_key")
        val ALLOWED_PACKAGES = stringSetPreferencesKey("active_session_allowed_packages")
        val SESSION_ID = stringPreferencesKey("active_session_id")
        val REMAINING_MILLIS = longPreferencesKey("active_session_remaining_millis")
        val SAVED_AT_EPOCH_MILLIS = longPreferencesKey("active_session_saved_at_epoch")
        val USAGE_BY_DATE = stringSetPreferencesKey("active_session_usage_by_date")
        val FINISHED = booleanPreferencesKey("active_session_finished")
        val COOLDOWN_UNTIL = longPreferencesKey("active_session_cooldown_until")
    }
}
