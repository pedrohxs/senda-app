package com.senda.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.sendaSessionDataStore by preferencesDataStore(name = "senda_session_state")
