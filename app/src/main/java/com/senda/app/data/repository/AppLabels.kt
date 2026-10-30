package com.senda.app.data.repository

import android.content.Context

/**
 * Nome visível dos apps instalados, com cache em memória.
 *
 * A sessão ativa guarda só o pacote; a tela inicial precisa do nome que a
 * pessoa reconhece ("Instagram", não "com.instagram.android").
 */
class AppLabels(private val context: Context) {

    private val cache = mutableMapOf<String, String>()

    fun label(packageName: String): String = cache.getOrPut(packageName) {
        runCatching {
            val manager = context.packageManager
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
}
