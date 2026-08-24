package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PeriodSummaryCalculatorTest {

    private val timeZone = TimeZone.getTimeZone("UTC")
    private val vehicle = Vehicle(type = VehicleType.HIBRIDO_ENCHUFABLE, initialKm = 1_000.0)

    @Test
    fun monthWithFuel_calculatesWeightedFuelMetrics() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 20.0, 30.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.GASOLINA, 40.0, 80.0, 1_200.0)
            )
        )

        assertEquals(110.0, summary.fuel.totalCost, 0.0)
        assertEquals(60.0, summary.fuel.quantity, 0.0)
        assertEquals(110.0 / 60.0, summary.fuel.averagePrice ?: 0.0, 0.0)
    }

    @Test
    fun electricFreeCharge_isIncludedInQuantityAndWeightedPrice() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.ELECTRICO, 10.0, 0.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.ELECTRICO, 10.0, 4.0, 1_200.0)
            )
        )

        assertEquals(20.0, summary.electric.quantity, 0.0)
        assertEquals(4.0, summary.electric.totalCost, 0.0)
        assertEquals(0.2, summary.electric.averagePrice ?: 0.0, 0.0)
    }

    @Test
    fun phevCombinesFuelAndElectricCosts() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.ELECTRICO, 5.0, 2.0, 1_200.0)
            )
        )

        assertEquals(22.0, summary.totalCost, 0.0)
        assertEquals(
            22.0,
            requireNotNull(calculateCostPerHundredKilometers(summary.costPerKilometer)),
            0.0
        )
    }

    @Test
    fun periodWithoutRecords_isEmpty() {
        val summary = summary(entries = emptyList())

        assertFalse(summary.hasRecords)
    }

    @Test
    fun oneMileage_doesNotProducePeriodDistance() {
        val summary = summary(
            entries = listOf(entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_100.0))
        )

        assertNull(summary.distanceKilometers)
        assertNull(calculateCostPerHundredKilometers(summary.costPerKilometer))
    }

    @Test
    fun twoValidMileages_calculateDistanceBetweenRecords() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 30.0, 1_350.0)
            )
        )

        assertEquals(250.0, summary.distanceKilometers ?: 0.0, 0.0)
        assertEquals(0.2, summary.costPerKilometer ?: 0.0, 0.0)
    }

    @Test
    fun monthlyCostPerHundredKilometers_derivesFromTheExistingCostPerKilometer() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 12.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.ELECTRICO, 10.0, 15.0, 1_300.0)
            )
        )

        assertEquals(0.135, summary.costPerKilometer ?: 0.0, 0.0)
        assertEquals(
            13.5,
            requireNotNull(calculateCostPerHundredKilometers(summary.costPerKilometer)),
            0.0
        )
    }

    @Test
    fun equalOrDecreasingMileage_doesNotProduceDistance() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_300.0),
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 30.0, 1_300.0)
            )
        )

        assertNull(summary.distanceKilometers)
    }

    @Test
    fun allPeriod_usesVehicleInitialMileageAndLastValidMileage() {
        val summary = calculatePeriodSummary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_360.0),
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, Double.NaN)
            ),
            vehicle = vehicle,
            period = StatisticsPeriod.All,
            timeZone = timeZone
        )

        assertEquals(360.0, summary.distanceKilometers ?: 0.0, 0.0)
        assertEquals(
            100.0 / 9.0,
            requireNotNull(calculateCostPerHundredKilometers(summary.costPerKilometer)),
            0.0
        )
    }

    @Test
    fun yearlyPeriod_keepsDistanceBetweenItsRecords() {
        val summary = calculatePeriodSummary(
            entries = listOf(
                entry(Calendar.JANUARY, FuelType.GASOLINA, 10.0, 20.0, 1_100.0),
                entry(Calendar.DECEMBER, FuelType.ELECTRICO, 10.0, 40.0, 1_500.0)
            ),
            vehicle = vehicle,
            period = StatisticsPeriod.Year(2026),
            timeZone = timeZone
        )

        assertEquals(400.0, summary.distanceKilometers ?: 0.0, 0.0)
        assertEquals(
            15.0,
            requireNotNull(calculateCostPerHundredKilometers(summary.costPerKilometer)),
            0.0
        )
    }

    @Test
    fun periodBoundaries_areStartInclusiveAndEndExclusiveAcrossDecember() {
        val december = entry(Calendar.DECEMBER, FuelType.GASOLINA, 10.0, 20.0, 1_100.0, day = 31)
        val january = entry(Calendar.JANUARY, FuelType.GASOLINA, 10.0, 30.0, 1_200.0, year = 2027)
        val summary = calculatePeriodSummary(
            entries = listOf(december, january),
            vehicle = vehicle,
            period = StatisticsPeriod.Month(2026, Calendar.DECEMBER),
            timeZone = timeZone
        )

        assertEquals(1, summary.entryCount)
        assertEquals(20.0, summary.totalCost, 0.0)
    }

    @Test
    fun leapYearFebruaryRange_hasCorrectExclusiveEnd() {
        val range = requireNotNull(periodRange(StatisticsPeriod.Month(2024, Calendar.FEBRUARY), timeZone))
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = range.endExclusive }

        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun comparisonWithPreviousPeriod_calculatesDifferenceAndPercentage() {
        val entries = listOf(
            entry(Calendar.JULY, FuelType.GASOLINA, 10.0, 100.0, 1_100.0),
            entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 80.0, 1_200.0)
        )

        val comparison = requireNotNull(
            calculatePeriodComparison(
                entries,
                vehicle,
                StatisticsPeriod.Month(2026, Calendar.AUGUST),
                timeZone
            )
        )

        assertEquals(-20.0, comparison.totalCost.difference ?: 0.0, 0.0)
        assertEquals(-20.0, comparison.totalCost.percentage ?: 0.0, 0.0)
    }

    @Test
    fun comparisonWithoutPreviousRecords_reportsNoPreviousData() {
        val comparison = requireNotNull(
            calculatePeriodComparison(
                listOf(entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 80.0, 1_200.0)),
                vehicle,
                StatisticsPeriod.Month(2026, Calendar.AUGUST),
                timeZone
            )
        )

        assertEquals(PeriodComparisonStatus.NO_PREVIOUS_DATA, comparison.totalCost.status)
    }

    @Test
    fun comparisonWithFreePreviousPeriod_reportsNoComparableBase() {
        val comparison = requireNotNull(
            calculatePeriodComparison(
                listOf(
                    entry(Calendar.JULY, FuelType.ELECTRICO, 10.0, 0.0, 1_100.0),
                    entry(Calendar.AUGUST, FuelType.ELECTRICO, 10.0, 8.0, 1_200.0)
                ),
                vehicle,
                StatisticsPeriod.Month(2026, Calendar.AUGUST),
                timeZone
            )
        )

        assertEquals(PeriodComparisonStatus.NO_COMPARABLE_BASE, comparison.totalCost.status)
    }

    @Test
    fun invalidEconomicAndMileageValues_areIgnored() {
        val summary = summary(
            entries = listOf(
                entry(Calendar.AUGUST, FuelType.GASOLINA, Double.NaN, 20.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                entry(Calendar.AUGUST, FuelType.GASOLINA, -1.0, 10.0, -1.0)
            )
        )

        assertTrue(summary.totalCost.isFinite())
        assertEquals(0.0, summary.totalCost, 0.0)
        assertNull(summary.distanceKilometers)
    }

    @Test
    fun monthlyExpensePoints_requireTwoMonthsForChartVisibility() {
        val oneMonth = summary(
            entries = listOf(entry(Calendar.AUGUST, FuelType.GASOLINA, 10.0, 20.0, 1_100.0))
        )
        val allMonths = calculatePeriodSummary(
            entries = listOf(
                entry(Calendar.JULY, FuelType.GASOLINA, 10.0, 20.0, 1_100.0),
                entry(Calendar.AUGUST, FuelType.ELECTRICO, 10.0, 10.0, 1_200.0)
            ),
            vehicle = vehicle,
            period = StatisticsPeriod.All,
            timeZone = timeZone
        )

        assertEquals(1, oneMonth.monthlyExpenses.size)
        assertEquals(2, allMonths.monthlyExpenses.size)
    }

    @Test
    fun currentFuelEstimateData_doesNotParticipateInPeriodSummary() {
        val withEstimatedTankLevel = entry(
            Calendar.AUGUST,
            FuelType.GASOLINA,
            20.0,
            30.0,
            1_100.0
        ).copy(fuelLevelAfter = 0.5)
        val withoutEstimatedTankLevel = withEstimatedTankLevel.copy(fuelLevelAfter = null)

        val withEstimate = summary(listOf(withEstimatedTankLevel))
        val withoutEstimate = summary(listOf(withoutEstimatedTankLevel))

        assertEquals(withoutEstimate.fuel, withEstimate.fuel)
    }

    private fun summary(entries: List<FuelEntry>): PeriodSummary =
        calculatePeriodSummary(entries, vehicle, StatisticsPeriod.Month(2026, Calendar.AUGUST), timeZone)

    private fun entry(
        month: Int,
        type: FuelType,
        quantity: Double,
        price: Double,
        kilometers: Double,
        year: Int = 2026,
        day: Int = 15
    ): FuelEntry = FuelEntry(
        fecha = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, 12, 0, 0)
        }.timeInMillis,
        cantidad = quantity,
        precio = price,
        tipo = type,
        km = kilometers,
        vehicleId = 1L
    )
}
