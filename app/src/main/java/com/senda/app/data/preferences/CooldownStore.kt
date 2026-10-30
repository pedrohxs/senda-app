package com.senda.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val COOLDOWN_PREFIX = "cooldown_until_"

class CooldownStore(private val context: Context) {

    val cooldowns: Flow<Map<String, Long>> = context.sendaSessionDataStore.data.map { preferences ->
        preferences.asMap().mapNotNull { (key, value) ->
            if (key.name.startsWith(COOLDOWN_PREFIX) && value is Long) {
                key.name.removePrefix(COOLDOWN_PREFIX) to value
            } else {
                null
            }
        }.toMap()
    }

    suspend fun startCooldown(
        groupId: String,
        minutes: Int,
        now: Long = System.currentTimeMillis()
    ) {
        if (minutes <= 0) {
            clearCooldown(groupId)
            return
        }

        context.sendaSessionDataStore.edit { preferences ->
            preferences[cooldownKey(groupId)] = now + minutes * 60_000L
        }
    }

    suspend fun clearCooldown(groupId: String) {
        context.sendaSessionDataStore.edit { preferences ->
            preferences.remove(cooldownKey(groupId))
        }
    }

    private fun cooldownKey(groupId: String) =
        longPreferencesKey("$COOLDOWN_PREFIX$groupId")
}
