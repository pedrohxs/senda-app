package com.senda.app.domain.scan

/**
 * Controla a frequência de varredura da árvore de acessibilidade nos browsers.
 *
 * Navegação (`TYPE_WINDOW_STATE_CHANGED`) precisa de resposta rápida: é o
 * momento em que o usuário troca de página. Já `TYPE_WINDOW_CONTENT_CHANGED`
 * dispara dezenas de vezes por segundo durante scroll e carregamento, então
 * tem uma janela mais folgada para não gastar bateria.
 */
class ScanThrottle(
    private val navigationIntervalMillis: Long = DEFAULT_NAVIGATION_INTERVAL_MILLIS,
    private val contentIntervalMillis: Long = DEFAULT_CONTENT_INTERVAL_MILLIS
) {

    private var lastNavigationAt = 0L
    private var lastContentAt = 0L

    fun allowNavigation(nowMillis: Long): Boolean {
        if (nowMillis - lastNavigationAt < navigationIntervalMillis) return false

        lastNavigationAt = nowMillis
        // A navegação já cobre o conteúdo; evita varrer duas vezes seguidas.
        lastContentAt = nowMillis
        return true
    }

    fun allowContent(nowMillis: Long): Boolean {
        if (nowMillis - lastContentAt < contentIntervalMillis) return false

        lastContentAt = nowMillis
        return true
    }

    fun reset() {
        lastNavigationAt = 0L
        lastContentAt = 0L
    }

    companion object {
        const val DEFAULT_NAVIGATION_INTERVAL_MILLIS = 350L
        const val DEFAULT_CONTENT_INTERVAL_MILLIS = 750L
    }
}
