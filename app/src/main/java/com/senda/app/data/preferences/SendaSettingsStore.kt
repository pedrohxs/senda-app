package com.senda.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sendaSettingsDataStore by preferencesDataStore(name = "senda_settings")

class SendaSettingsStore(private val context: Context) {

    val setupCompleted: Flow<Boolean> = context.sendaSettingsDataStore.data.map { it[SETUP_COMPLETED] ?: false }

    suspend fun completeSetup() {
        context.sendaSettingsDataStore.edit { it[SETUP_COMPLETED] = true }
    }

    /** Quando foi o último backup manual concluído (epoch millis). */
    val lastBackupAt: Flow<Long?> = context.sendaSettingsDataStore.data.map { prefs ->
        prefs[LAST_BACKUP_AT]
    }

    suspend fun setLastBackupAt(millis: Long) {
        context.sendaSettingsDataStore.edit { preferences ->
            preferences[LAST_BACKUP_AT] = millis
        }
    }

    /** Quando foi a última cópia automática em Download/Senda. */
    val lastAutoBackupAt: Flow<Long?> = context.sendaSettingsDataStore.data.map { prefs ->
        prefs[LAST_AUTO_BACKUP_AT]
    }

    suspend fun setLastAutoBackupAt(millis: Long) {
        context.sendaSettingsDataStore.edit { preferences ->
            preferences[LAST_AUTO_BACKUP_AT] = millis
        }
    }

    private companion object {
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
    }
}
