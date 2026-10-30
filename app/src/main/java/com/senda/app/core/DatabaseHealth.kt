package com.senda.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resultado do `PRAGMA quick_check` na abertura do banco.
 *
 * Corrupção é rara, mas quando acontece o app precisa dizer isso de forma
 * amigável em vez de estourar exceções de SQLite em cada tela.
 */
object DatabaseHealth {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun markHealthy() {
        _error.value = null
    }

    fun markCorrupted(detail: String?) {
        _error.value = detail?.takeIf { it.isNotBlank() } ?: "quick_check falhou"
        DiagnosticsLog.add("database", "corrupção detectada: ${_error.value}")
    }
}
