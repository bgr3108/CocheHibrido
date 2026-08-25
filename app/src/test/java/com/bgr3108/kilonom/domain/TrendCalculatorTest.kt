package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TrendCalculatorTest {

    @Test
    fun metricTrend_smallChangeIsStable() {
        val trend = calculateMetricTrend(listOf(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.1, 8.1, 8.1))

        assertEquals(TrendStatus.STABLE, trend.status)
        assertEquals(1.25, requireNotNull(trend.percentageChange), 0.000001)
    }

    @Test
    fun metricTrend_tenPercentIncreaseIsAnUpwardTrend() {
        val trend = calculateMetricTrend(List(6) { 8.0 } + List(3) { 8.8 })

        assertEquals(TrendStatus.UP, trend.status)
        assertEquals(TrendMagnitude.SLIGHT, trend.magnitude)
        assertEquals(10.0, requireNotNull(trend.percentageChange), 0.000001)
    }

    @Test
    fun metricTrend_tenPercentDecreaseIsADownwardTrend() {
        val trend = calculateMetricTrend(List(6) { 8.0 } + List(3) { 7.2 })

        assertEquals(TrendStatus.DOWN, trend.status)
        assertEquals(-10.0, requireNotNull(trend.percentageChange), 0.000001)
    }

    @Test
    fun metricTrend_requiresThreeRecentAndThreeBaselineValues() {
        val trend = calculateMetricTrend(List(5) { 8.0 })

        assertEquals(TrendStatus.INSUFFICIENT_DATA, trend.status)
        assertNull(trend.baselineValue)
        assertNull(trend.recentValue)
    }

    @Test
    fun metricTrend_ignoresInvalidValues() {
        val trend = calculateMetricTrend(
            listOf(Double.NaN, -3.0, Double.POSITIVE_INFINITY) + List(6) { 8.0 } + List(3) { 8.8 }
        )

        assertEquals(TrendStatus.UP, trend.status)
        assertEquals(5, trend.baselineSampleCount)
        assertEquals(3, trend.recentSampleCount)
    }

    @Test
    fun fuelTrend_usesOnlyConfirmedFullToFullSegments_notCurrentEstimate() {
        val confirmedFullTanks = (0..6).map { index ->
            fuelEntry(km = index * 100.0, quantity = 8.0, fullTank = true)
        }
        val provisionalPartial = fuelEntry(
            km = 700.0,
            quantity = 30.0,
            fullTank = false,
            fuelLevelAfter = 0.25
        )

        val trend = calculateFuelConsumptionTrend(confirmedFullTanks + provisionalPartial)

        assertEquals(TrendStatus.STABLE, trend.status)
        assertEquals(8.0, requireNotNull(trend.recentValue), 0.000001)
    }

    @Test
    fun electricVehicle_hasElectricTrendWithoutFuelTrend() {
        val entries = electricEntries(List(3) { 8.0 } + List(3) { 8.0 } + List(3) { 8.8 })

        val summary = calculateVehicleTrends(
            entries,
            Vehicle(id = 1L, type = VehicleType.ELECTRICO)
        )

        assertNull(summary.fuelConsumption)
        assertNotNull(summary.electricConsumption)
        assertEquals(TrendStatus.UP, summary.electricConsumption?.status)
    }

    @Test
    fun plugInHybrid_hasFuelAndElectricTrends() {
        val entries = fuelEntries(List(6) { 8.0 } + List(3) { 8.8 }) +
            electricEntries(List(6) { 6.0 } + List(3) { 6.6 })

        val summary = calculateVehicleTrends(
            entries,
            Vehicle(id = 1L, type = VehicleType.HIBRIDO_ENCHUFABLE)
        )

        assertEquals(TrendStatus.UP, summary.fuelConsumption?.status)
        assertEquals(TrendStatus.UP, summary.electricConsumption?.status)
    }

    @Test
    fun nonPlugInHybrid_doesNotExposeElectricTrend() {
        val summary = calculateVehicleTrends(
            fuelEntries(List(9) { 8.0 }) + electricEntries(List(9) { 6.0 }),
            Vehicle(id = 1L, type = VehicleType.HIBRIDO)
        )

        assertNotNull(summary.fuelConsumption)
        assertNull(summary.electricConsumption)
    }

    @Test
    fun costTrend_usesSameCostPerDistanceFormulaAndCombinesPhevEnergyCosts() {
        val entries = (0..5).map { index ->
            economicEntry(
                km = index * 100.0,
                price = 4.0,
                type = if (index % 2 == 0) FuelType.GASOLINA else FuelType.ELECTRICO
            )
        } + (6..8).map { index ->
            economicEntry(
                km = index * 100.0,
                price = 6.0,
                type = if (index % 2 == 0) FuelType.GASOLINA else FuelType.ELECTRICO
            )
        }

        val trend = calculateCostPerHundredKmTrend(entries)

        assertEquals(TrendStatus.UP, trend.status)
        assertEquals(5.0, requireNotNull(trend.baselineValue), 0.000001)
        assertEquals(9.0, requireNotNull(trend.recentValue), 0.000001)
    }

    @Test
    fun costTrend_requiresPositiveDistanceInBothWindows() {
        val trend = calculateCostPerHundredKmTrend(
            List(6) { economicEntry(km = 0.0, price = 4.0) }
        )

        assertEquals(TrendStatus.INSUFFICIENT_DATA, trend.status)
    }

    @Test
    fun fuelPriceTrend_comparesWeightedAveragePrices() {
        val entries = (0..5).map { index ->
            economicEntry(km = index * 100.0, price = 15.0, quantity = 10.0)
        } + (6..8).map { index ->
            economicEntry(km = index * 100.0, price = 18.0, quantity = 10.0)
        }

        val trend = calculateFuelPriceTrend(entries)

        assertEquals(TrendStatus.UP, trend.status)
        assertEquals(1.5, requireNotNull(trend.baselineValue), 0.000001)
        assertEquals(1.8, requireNotNull(trend.recentValue), 0.000001)
    }

    @Test
    fun vehicleTrend_filtersEntriesToTheVehicleBeingAnalysed() {
        val entries = fuelEntries(List(9) { 8.0 }, vehicleId = 1L) +
            fuelEntries(List(6) { 8.0 } + List(3) { 12.0 }, vehicleId = 2L)

        val vehicleOne = calculateVehicleTrends(
            entries,
            Vehicle(id = 1L, type = VehicleType.GASOLINA)
        )
        val vehicleTwo = calculateVehicleTrends(
            entries,
            Vehicle(id = 2L, type = VehicleType.GASOLINA)
        )

        assertEquals(TrendStatus.STABLE, vehicleOne.fuelConsumption?.status)
        assertEquals(TrendStatus.UP, vehicleTwo.fuelConsumption?.status)
    }

    private fun fuelEntries(values: List<Double>, vehicleId: Long = 1L): List<FuelEntry> =
        listOf(fuelEntry(km = 0.0, quantity = values.first(), vehicleId = vehicleId)) +
            values.mapIndexed { index, value ->
                fuelEntry(km = (index + 1) * 100.0, quantity = value, vehicleId = vehicleId)
            }

    private fun electricEntries(values: List<Double>, vehicleId: Long = 1L): List<FuelEntry> =
        listOf(electricEntry(km = 0.0, quantity = values.first(), vehicleId = vehicleId)) +
            values.mapIndexed { index, value ->
                electricEntry(km = (index + 1) * 100.0, quantity = value, vehicleId = vehicleId)
            }

    private fun fuelEntry(
        km: Double,
        quantity: Double,
        fullTank: Boolean = true,
        fuelLevelAfter: Double? = null,
        vehicleId: Long = 1L
    ) = FuelEntry(
        fecha = km.toLong(),
        cantidad = quantity,
        precio = quantity,
        tipo = FuelType.GASOLINA,
        km = km,
        fullTank = fullTank,
        fuelLevelAfter = fuelLevelAfter,
        vehicleId = vehicleId
    )

    private fun electricEntry(km: Double, quantity: Double, vehicleId: Long = 1L) = FuelEntry(
        fecha = km.toLong(),
        cantidad = quantity,
        precio = quantity,
        tipo = FuelType.ELECTRICO,
        km = km,
        vehicleId = vehicleId
    )

    private fun economicEntry(
        km: Double,
        price: Double,
        quantity: Double = 1.0,
        type: FuelType = FuelType.GASOLINA
    ) = FuelEntry(
        fecha = km.toLong(),
        cantidad = quantity,
        precio = price,
        tipo = type,
        km = km,
        vehicleId = 1L
    )
}
