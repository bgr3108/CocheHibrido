package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MaintenanceRecurrenceTest {

    @Test
    fun kilometreInterval_isAddedToPerformedKilometres() {
        assertEquals(30_000L, due(performedKm = 20_000L, intervalKm = 10_000L).nextDueKm)
    }

    @Test
    fun earlyCompletion_startsTheNewKilometreCycleFromThePerformedReading() {
        assertEquals(88_300L, due(performedKm = 58_300L, intervalKm = 30_000L).nextDueKm)
    }

    @Test
    fun lateCompletion_startsTheNewKilometreCycleFromThePerformedReading() {
        assertEquals(92_000L, due(performedKm = 62_000L, intervalKm = 30_000L).nextDueKm)
    }

    @Test
    fun missingPerformedKilometres_neverInventsTheNextKilometreLimit() {
        assertNull(due(performedKm = null, intervalKm = 30_000L).nextDueKm)
    }

    @Test
    fun yearlyInterval_usesCalendarSemantics() {
        assertEquals(date(2028, 9, 4), due(date(2026, 9, 4), intervalValue = 2, unit = MaintenanceTimeUnit.YEARS).nextDueDate)
    }

    @Test
    fun monthlyAndDailyIntervals_useCalendarSemantics() {
        assertEquals(date(2027, 9, 4), due(date(2026, 9, 4), intervalValue = 12, unit = MaintenanceTimeUnit.MONTHS).nextDueDate)
        assertEquals(date(2026, 9, 7), due(date(2026, 9, 4), intervalValue = 3, unit = MaintenanceTimeUnit.DAYS).nextDueDate)
    }

    @Test
    fun leapDayYearlyInterval_delegatesToLocalDate() {
        assertEquals(date(2029, 2, 28), due(date(2028, 2, 29), intervalValue = 1, unit = MaintenanceTimeUnit.YEARS).nextDueDate)
    }

    @Test
    fun kilometreAndTimeIntervals_calculateIndependentConcreteLimits() {
        val result = due(date(2026, 9, 4), 28_500L, 30_000L, 2, MaintenanceTimeUnit.YEARS)
        assertEquals(58_500L, result.nextDueKm)
        assertEquals(date(2028, 9, 4), result.nextDueDate)
    }

    @Test
    fun nextDueUpdate_canCalculateOverrideOrClearExplicitly() {
        val automatic = resolveNextMaintenanceDueUpdate(
            MaintenanceNextDueUpdate.AutomaticFromInterval,
            performedDate = date(2026, 9, 4),
            performedKm = 28_500L,
            intervalKm = 30_000L,
            intervalTimeValue = 2,
            intervalTimeUnit = MaintenanceTimeUnit.YEARS,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(58_500L, automatic.nextDueKm)
        assertEquals(date(2028, 9, 4), automatic.nextDueDate)

        val manual = resolveNextMaintenanceDueUpdate(
            MaintenanceNextDueUpdate.Manual(60_000L, null),
            null, null, null, null, null, ZoneOffset.UTC
        )
        assertEquals(60_000L, manual.nextDueKm)
        assertNull(manual.nextDueDate)

        val cleared = resolveNextMaintenanceDueUpdate(
            MaintenanceNextDueUpdate.Clear,
            null, null, null, null, null, ZoneOffset.UTC
        )
        assertNull(cleared.nextDueKm)
        assertNull(cleared.nextDueDate)
    }

    @Test
    fun kilometreAdditionAcceptsLongMaxButRejectsOverflow() {
        assertEquals(Long.MAX_VALUE, due(performedKm = Long.MAX_VALUE - 1, intervalKm = 1).nextDueKm)
        assertInvalid { due(performedKm = Long.MAX_VALUE, intervalKm = 1) }
        assertInvalid { due(performedKm = 42_000, intervalKm = Long.MAX_VALUE) }
        assertInvalid { due(performedKm = Long.MIN_VALUE, intervalKm = 1) }
    }

    @Test
    fun yearsOutsideLocalDateAndEpochMillisRangesAreControlledValidationErrors() {
        assertInvalid { due(date(2026, 1, 1), intervalValue = Int.MAX_VALUE, unit = MaintenanceTimeUnit.YEARS) }
        // LocalDate represents this year; Long epoch milliseconds cannot.
        assertInvalid { due(date(2026, 1, 1), intervalValue = 400_000_000, unit = MaintenanceTimeUnit.YEARS) }
    }

    @Test
    fun extremeEpochDateCannotOverflowWhenAnIntervalIsApplied() {
        MaintenanceTimeUnit.entries.forEach { unit ->
            assertInvalid { due(Long.MAX_VALUE, intervalValue = 1, unit = unit) }
        }
        val result = due(Long.MIN_VALUE, intervalValue = 2, unit = MaintenanceTimeUnit.DAYS)
        assertTrue(requireNotNull(result.nextDueDate) > Long.MIN_VALUE)
    }

    @Test
    fun largeRepresentableDayAndMonthIntervalsAreStillAllowed() {
        val base = date(2026, 1, 1)
        assertEquals(
            LocalDate.of(2026, 1, 1).plusDays(Int.MAX_VALUE.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            due(base, intervalValue = Int.MAX_VALUE, unit = MaintenanceTimeUnit.DAYS).nextDueDate
        )
        assertEquals(
            LocalDate.of(2026, 1, 1).plusMonths(Int.MAX_VALUE.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            due(base, intervalValue = Int.MAX_VALUE, unit = MaintenanceTimeUnit.MONTHS).nextDueDate
        )
    }

    @Test
    fun invalidHistoricalIntervalValuesAreRejectedWithoutWrappedResults() {
        assertInvalid { due(intervalKm = Long.MIN_VALUE) }
        assertInvalid { due(intervalValue = -1, unit = MaintenanceTimeUnit.YEARS) }
        assertInvalid { due(intervalValue = 1, unit = null) }
        assertInvalid { due(intervalValue = null, unit = MaintenanceTimeUnit.DAYS) }
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected a controlled recurrence validation error")
        } catch (_: MaintenanceRecurrenceException) {
            // Unexpected arithmetic/date exceptions must never escape the domain boundary.
        }
    }

    private fun due(
        performedDate: Long? = null,
        performedKm: Long? = null,
        intervalKm: Long? = null,
        intervalValue: Int? = null,
        unit: MaintenanceTimeUnit? = null
    ) = calculateNextMaintenanceDue(
        performedDate = performedDate,
        performedKm = performedKm,
        intervalKm = intervalKm,
        intervalTimeValue = intervalValue,
        intervalTimeUnit = unit,
        zoneId = ZoneOffset.UTC
    )

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
