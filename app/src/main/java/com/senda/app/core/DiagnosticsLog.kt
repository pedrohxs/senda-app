package com.senda.app.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro curto e circular do que o app decidiu fazer.
 *
 * Existe para diagnosticar problemas que só aparecem no aparelho (overlay que
 * pisca, bloqueio que não sobe, OEM que mata o serviço) sem precisar de adb.
 * Fica só na memória: nada é enviado para lugar nenhum, e o usuário decide
 * quando exportar.
 */
object DiagnosticsLog {

    data class Event(
        val atMillis: Long,
        val source: String,
        val message: String
    )

    private const val MAX_EVENTS = 300
    private val events = ArrayDeque<Event>(MAX_EVENTS)
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun add(source: String, message: String, nowMillis: Long = System.currentTimeMillis()) {
        val event = Event(
            atMillis = nowMillis,
            source = source,
            message = message.take(MAX_MESSAGE_LENGTH)
        )
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
        }
    }

    /** Mais recente primeiro. */
    fun snapshot(): List<Event> = synchronized(lock) { events.reversed() }

    fun clear() {
        synchronized(lock) { events.clear() }
    }

    fun formatTime(atMillis: Long): String = timeFormat.format(Date(atMillis))

    private const val MAX_MESSAGE_LENGTH = 200
}
