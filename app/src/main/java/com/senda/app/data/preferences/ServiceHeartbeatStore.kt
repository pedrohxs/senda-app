package com.senda.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Batimento cardíaco do serviço de proteção.
 *
 * O serviço grava o horário atual a cada ciclo do watchdog; o worker de saúde
 * lê esse valor para saber se a proteção continua ativa.
 */
class ServiceHeartbeatStore(private val context: Context) {

    suspend fun record(now: Long = System.currentTimeMillis()) {
        context.sendaSessionDataStore.edit { preferences ->
            preferences[LAST_HEARTBEAT] = now
        }
    }

    suspend fun last(): Long =
        context.sendaSessionDataStore.data
            .map { preferences -> preferences[LAST_HEARTBEAT] ?: 0L }
            .first()

    private companion object {
        val LAST_HEARTBEAT = longPreferencesKey("service_last_heartbeat_at")
    }
}
