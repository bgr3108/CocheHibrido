package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
