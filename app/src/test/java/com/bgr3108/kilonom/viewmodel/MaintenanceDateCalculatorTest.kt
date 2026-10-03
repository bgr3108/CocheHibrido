package com.bgr3108.kilonom.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MaintenanceDateCalculatorTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun normalConsecutiveDays_areOneCalendarDayApart() {
        assertEquals(1L, daysBetween(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11)))
    }

    @Test
    fun springDstTransition_keepsConsecutiveDatesOneDayApart() {
        assertEquals(1L, daysBetween(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30)))
    }

    @Test
    fun autumnDstTransition_keepsConsecutiveDatesOneDayApart() {
        assertEquals(1L, daysBetween(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26)))
    }

    @Test
    fun dueTodayTomorrowAndPast_useCalendarDaySigns() {
        val today = LocalDate.of(2026, 3, 29)

        assertEquals(0L, daysBetween(today, today))
        assertEquals(1L, daysBetween(today, today.plusDays(1)))
        assertEquals(-1L, daysBetween(today, today.minusDays(1)))
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = calendarDaysBetween(
        from = from.atStartOfDay(madrid).toInstant().toEpochMilli(),
        to = to.atStartOfDay(madrid).toInstant().toEpochMilli(),
        zoneId = madrid
    )
}
