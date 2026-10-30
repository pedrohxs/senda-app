package com.senda.app.domain.session

import java.time.Instant
import java.time.ZoneId

/** Counts elapsed time, retaining milliseconds across pauses and splitting use at midnight. */
class SessionUsageCounter(
    var remainingMillis: Long,
    private var lastElapsedRealtime: Long,
    private var lastEpochMillis: Long,
    private var counting: Boolean,
    usageMillisByDate: Map<String, Long> = emptyMap(),
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val usage = usageMillisByDate.toMutableMap()
    val remainingSeconds: Int get() = ((remainingMillis + 999L) / 1000L).toInt()
    val usageMillisByDate: Map<String, Long> get() = usage.toMap()
    val checkpointElapsedRealtime: Long get() = lastElapsedRealtime
    val checkpointEpochMillis: Long get() = lastEpochMillis

    fun advance(nowElapsedRealtime: Long, nowEpochMillis: Long, countingNow: Boolean) {
        val elapsed = (nowElapsedRealtime - lastElapsedRealtime).coerceAtLeast(0L)
        if (counting) {
            val consumed = elapsed.coerceAtMost(remainingMillis.coerceAtLeast(0L))
            remainingMillis -= consumed
            addUsage(lastEpochMillis, consumed)
        }
        lastElapsedRealtime = nowElapsedRealtime
        lastEpochMillis = nowEpochMillis
        counting = countingNow
    }

    fun secondsOn(date: String): Int = ((usage[date] ?: 0L) / 1000L).toInt()

    private fun addUsage(startEpochMillis: Long, millis: Long) {
        var cursor = startEpochMillis
        var pending = millis
        while (pending > 0L) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val slice = pending.coerceAtMost(nextMidnight - cursor)
            usage[date.toString()] = (usage[date.toString()] ?: 0L) + slice
            cursor += slice
            pending -= slice
        }
    }
}
