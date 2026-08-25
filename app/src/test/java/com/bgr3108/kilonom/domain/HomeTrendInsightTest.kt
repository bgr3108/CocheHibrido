package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTrendInsightTest {

    @Test
    fun relevantFuelConsumption_hasPriorityOverStableCost() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.UP, TrendMagnitude.RELEVANT, 15.0),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.GASOLINA
        )

        assertEquals(HomeTrendMetric.FUEL_CONSUMPTION, insight.metric)
        assertEquals(15.0, insight.percentageChange)
    }

    @Test
    fun relevantCost_hasPriorityOverStableConsumption() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.STABLE),
                cost = trend(TrendStatus.DOWN, TrendMagnitude.RELEVANT, -12.0)
            ),
            VehicleType.DIESEL
        )

        assertEquals(HomeTrendMetric.COST_PER_HUNDRED_KM, insight.metric)
        assertEquals(TrendStatus.DOWN, insight.status)
    }

    @Test
    fun relevantCost_hasPriorityOverSlightConsumptionChange() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.UP, TrendMagnitude.SLIGHT, 7.0),
                cost = trend(TrendStatus.DOWN, TrendMagnitude.RELEVANT, -15.0)
            ),
            VehicleType.GASOLINA
        )

        assertEquals(HomeTrendMetric.COST_PER_HUNDRED_KM, insight.metric)
    }

    @Test
    fun allAvailableMetricsStable_returnsStableInsight() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.STABLE),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.HIBRIDO
        )

        assertEquals(HomeTrendMetric.STABLE, insight.metric)
        assertEquals(TrendStatus.STABLE, insight.status)
    }

    @Test
    fun noAvailableTrend_returnsInsufficientDataInsight() {
        val insight = selectHomeTrendInsight(VehicleTrendSummary(), VehicleType.GASOLINA)

        assertEquals(HomeTrendMetric.INSUFFICIENT_DATA, insight.metric)
    }

    @Test
    fun phevElectricTrend_usesEnergyChargedConcept() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.STABLE),
                electric = trend(TrendStatus.UP, TrendMagnitude.RELEVANT, 12.0),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.HIBRIDO_ENCHUFABLE
        )

        assertEquals(HomeTrendMetric.PHEV_ENERGY_CHARGED, insight.metric)
        assertEquals(TrendStatus.UP, insight.status)
    }

    @Test
    fun phevSlightEnergyChargedTrend_hasPriorityOverStableFuel() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.STABLE),
                electric = trend(TrendStatus.DOWN, TrendMagnitude.SLIGHT, -7.0),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.HIBRIDO_ENCHUFABLE
        )

        assertEquals(HomeTrendMetric.PHEV_ENERGY_CHARGED, insight.metric)
        assertEquals(-7.0, insight.percentageChange)
    }

    @Test
    fun electricVehicle_usesElectricConsumptionConcept() {
        val insight = selectHomeTrendInsight(
            summary(
                electric = trend(TrendStatus.DOWN, TrendMagnitude.SLIGHT, -8.0),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.ELECTRICO
        )

        assertEquals(HomeTrendMetric.ELECTRIC_CONSUMPTION, insight.metric)
    }

    @Test
    fun hevNeverSelectsElectricInsight() {
        val insight = selectHomeTrendInsight(
            summary(
                fuel = trend(TrendStatus.STABLE),
                electric = trend(TrendStatus.UP, TrendMagnitude.RELEVANT, 20.0),
                cost = trend(TrendStatus.STABLE)
            ),
            VehicleType.HIBRIDO
        )

        assertEquals(HomeTrendMetric.STABLE, insight.metric)
    }

    @Test
    fun switchingVehicleUsesTheSummaryForTheNewActiveVehicle() {
        val vehicleAInsight = selectHomeTrendInsight(
            summary(fuel = trend(TrendStatus.UP, TrendMagnitude.RELEVANT, 16.0)),
            VehicleType.GASOLINA
        )
        val vehicleBInsight = selectHomeTrendInsight(
            summary(electric = trend(TrendStatus.DOWN, TrendMagnitude.SLIGHT, -8.0)),
            VehicleType.ELECTRICO
        )

        assertEquals(HomeTrendMetric.FUEL_CONSUMPTION, vehicleAInsight.metric)
        assertEquals(HomeTrendMetric.ELECTRIC_CONSUMPTION, vehicleBInsight.metric)
    }

    private fun summary(
        fuel: MetricTrend? = null,
        electric: MetricTrend? = null,
        cost: MetricTrend = MetricTrend()
    ) = VehicleTrendSummary(
        fuelConsumption = fuel,
        electricConsumption = electric,
        costPerHundredKm = cost
    )

    private fun trend(
        status: TrendStatus,
        magnitude: TrendMagnitude = TrendMagnitude.NONE,
        percentageChange: Double? = null
    ) = MetricTrend(
        baselineValue = if (status == TrendStatus.INSUFFICIENT_DATA) null else 1.0,
        recentValue = if (status == TrendStatus.INSUFFICIENT_DATA) null else 1.0,
        percentageChange = percentageChange,
        status = status,
        magnitude = magnitude
    )
}
