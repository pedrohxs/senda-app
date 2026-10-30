package com.senda.app.ui.usage
import com.senda.app.data.repository.DailyUsageBar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UsageChartTest {
    @Test fun todayAppearsImmediatelyAfterYesterdayWithoutChangingHistoricalWindow() {
        val today = LocalDate.of(2026, 10, 30)
        val week = (7 downTo 1).map { DailyUsageBar(today.minusDays(it.toLong()).toString(), "dia", 60, false) }
        val state = UsageUiState(todaySeconds = 120, week = week)
        val chart = state.chartDays()
        assertEquals(7, chart.size)
        assertEquals(today.minusDays(1).toString(), chart[5].date)
        assertEquals(today.toString(), chart.last().date)
        assertEquals("Hoje", chart.last().label)
        assertTrue(chart.last().isToday)
        assertEquals(120, chart.last().seconds)
        assertEquals(week, state.week)
    }
}
