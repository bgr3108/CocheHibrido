package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries

const val MIN_TREND_RECENT_SAMPLES = 3
const val MIN_TREND_BASELINE_SAMPLES = 3
const val MAX_TREND_BASELINE_SAMPLES = 5
private const val STABLE_TREND_THRESHOLD_PERCENT = 5.0
private const val RELEVANT_TREND_THRESHOLD_PERCENT = 10.0
private const val TREND_PERCENTAGE_COMPARISON_EPSILON = 0.000001

enum class TrendStatus {
    INSUFFICIENT_DATA,
    STABLE,
    UP,
    DOWN
}

enum class TrendMagnitude {
    NONE,
    SLIGHT,
    RELEVANT
}

data class MetricTrend(
    val baselineValue: Double? = null,
    val recentValue: Double? = null,
    val percentageChange: Double? = null,
    val status: TrendStatus = TrendStatus.INSUFFICIENT_DATA,
    val magnitude: TrendMagnitude = TrendMagnitude.NONE,
    val baselineSampleCount: Int = 0,
    val recentSampleCount: Int = 0
)

enum class OverallTrendStatus {
    INSUFFICIENT_DATA,
    STABLE,
    CONSUMPTION_UP,
    CONSUMPTION_DOWN,
    COST_UP,
    COST_DOWN,
    CHANGES,
    MIXED_CHANGES
}

data class VehicleTrendSummary(
    val fuelConsumption: MetricTrend? = null,
    val electricConsumption: MetricTrend? = null,
    val costPerHundredKm: MetricTrend = MetricTrend(),
    val fuelPrice: MetricTrend? = null,
    val electricPrice: MetricTrend? = null,
    val overallStatus: OverallTrendStatus = OverallTrendStatus.INSUFFICIENT_DATA
)

/**
 * Compares the three most recent valid values with the immediately preceding baseline of up to
 * five values. It intentionally requires at least three values in both groups before describing
 * a change.
 */
fun calculateMetricTrend(values: List<Double>): MetricTrend {
    val validValues = values.filter { it.isFinite() && it > 0.0 }
    val windows = validValues.toTrendWindows() ?: return MetricTrend()

    return calculateTrend(
        baselineValue = windows.baseline.average(),
        recentValue = windows.recent.average(),
        baselineSampleCount = windows.baseline.size,
        recentSampleCount = windows.recent.size
    )
}

fun calculateFuelConsumptionTrend(entries: List<FuelEntry>): MetricTrend =
    calculateMetricTrend(calculateFuelSegments(entries).map { it.consumption })

fun calculateElectricConsumptionTrend(entries: List<FuelEntry>): MetricTrend =
    calculateMetricTrend(calculateElectricSegments(entries).map { it.consumption })

/**
 * Uses the same valid economic entries and cost-per-distance helpers as the existing statistics.
 * Each non-overlapping window requires at least three records and a positive distance between its
 * valid kilometre readings, so a single refuel or charge never defines the economic trend.
 */
fun calculateCostPerHundredKmTrend(entries: List<FuelEntry>): MetricTrend {
    val validEntries = entries
        .filter(::isValidEconomicEntry)
        .filter { it.km.isFinite() && it.km >= 0.0 }
        .sortedWith(compareBy<FuelEntry> { it.fecha }.thenBy { it.id })
    val windows = validEntries.toTrendWindows() ?: return MetricTrend()

    return calculateTrend(
        baselineValue = calculateWindowCostPerHundredKm(windows.baseline),
        recentValue = calculateWindowCostPerHundredKm(windows.recent),
        baselineSampleCount = windows.baseline.size,
        recentSampleCount = windows.recent.size
    )
}

fun calculateFuelPriceTrend(entries: List<FuelEntry>): MetricTrend =
    calculateEnergyPriceTrend(entries, FuelType.GASOLINA)

fun calculateElectricPriceTrend(entries: List<FuelEntry>): MetricTrend =
    calculateEnergyPriceTrend(entries, FuelType.ELECTRICO)

fun calculateVehicleTrends(entries: List<FuelEntry>, vehicle: Vehicle): VehicleTrendSummary {
    // HomeViewModel already supplies entries from the active vehicle context. Keeping this
    // guard here also makes the pure calculator safe if a caller accidentally supplies a
    // broader collection in the future.
    val vehicleEntries = vehicle.id?.let { activeVehicleId ->
        entries.filter { entry -> entry.vehicleId == activeVehicleId }
    } ?: entries
    val fuelTrend = if (vehicle.type.supportsFuelEntries) {
        calculateFuelConsumptionTrend(vehicleEntries)
    } else {
        null
    }
    val electricTrend = if (vehicle.type.supportsElectricEntries) {
        calculateElectricConsumptionTrend(vehicleEntries)
    } else {
        null
    }
    val fuelPriceTrend = if (vehicle.type.supportsFuelEntries) {
        calculateFuelPriceTrend(vehicleEntries)
    } else {
        null
    }
    val electricPriceTrend = if (vehicle.type.supportsElectricEntries) {
        calculateElectricPriceTrend(vehicleEntries)
    } else {
        null
    }
    val costTrend = calculateCostPerHundredKmTrend(vehicleEntries)

    return VehicleTrendSummary(
        fuelConsumption = fuelTrend,
        electricConsumption = electricTrend,
        costPerHundredKm = costTrend,
        fuelPrice = fuelPriceTrend,
        electricPrice = electricPriceTrend,
        overallStatus = calculateOverallTrendStatus(fuelTrend, electricTrend, costTrend)
    )
}

private fun calculateEnergyPriceTrend(entries: List<FuelEntry>, type: FuelType): MetricTrend {
    val validEntries = validEconomicEntries(entries, type)
        .sortedWith(compareBy<FuelEntry> { it.fecha }.thenBy { it.id })
    val windows = validEntries.toTrendWindows() ?: return MetricTrend()

    val baselinePrice = when (type) {
        FuelType.GASOLINA -> calculateAverageFuelPrice(windows.baseline)
        FuelType.ELECTRICO -> calculateAverageElectricPrice(windows.baseline)
    }
    val recentPrice = when (type) {
        FuelType.GASOLINA -> calculateAverageFuelPrice(windows.recent)
        FuelType.ELECTRICO -> calculateAverageElectricPrice(windows.recent)
    }
    return calculateTrend(
        baselineValue = baselinePrice,
        recentValue = recentPrice,
        baselineSampleCount = windows.baseline.size,
        recentSampleCount = windows.recent.size
    )
}

private fun calculateWindowCostPerHundredKm(entries: List<FuelEntry>): Double? {
    val validKilometers = entries.map { it.km }
        .filter { it.isFinite() && it >= 0.0 }
    val distance = (validKilometers.maxOrNull() ?: return null) -
        (validKilometers.minOrNull() ?: return null)
    if (!distance.isFinite() || distance <= 0.0) return null

    val costPerKm = calculateCostPerKilometer(calculateTotalCost(entries), distance)
    return calculateCostPerHundredKilometers(costPerKm, distance)
}

private fun calculateTrend(
    baselineValue: Double?,
    recentValue: Double?,
    baselineSampleCount: Int,
    recentSampleCount: Int
): MetricTrend {
    if (
        baselineSampleCount < MIN_TREND_BASELINE_SAMPLES ||
        recentSampleCount < MIN_TREND_RECENT_SAMPLES ||
        baselineValue == null ||
        recentValue == null ||
        !baselineValue.isFinite() ||
        !recentValue.isFinite() ||
        baselineValue <= 0.0 ||
        recentValue <= 0.0
    ) {
        return MetricTrend(
            baselineSampleCount = baselineSampleCount,
            recentSampleCount = recentSampleCount
        )
    }

    val percentageChange = ((recentValue - baselineValue) / baselineValue * 100.0)
        .takeIf { it.isFinite() }
        ?: return MetricTrend(
            baselineSampleCount = baselineSampleCount,
            recentSampleCount = recentSampleCount
        )
    val absoluteChange = kotlin.math.abs(percentageChange)
    val status = when {
        absoluteChange < STABLE_TREND_THRESHOLD_PERCENT -> TrendStatus.STABLE
        percentageChange > 0.0 -> TrendStatus.UP
        else -> TrendStatus.DOWN
    }
    val magnitude = when {
        status == TrendStatus.STABLE -> TrendMagnitude.NONE
        absoluteChange > RELEVANT_TREND_THRESHOLD_PERCENT + TREND_PERCENTAGE_COMPARISON_EPSILON ->
            TrendMagnitude.RELEVANT
        else -> TrendMagnitude.SLIGHT
    }
    return MetricTrend(
        baselineValue = baselineValue,
        recentValue = recentValue,
        percentageChange = percentageChange,
        status = status,
        magnitude = magnitude,
        baselineSampleCount = baselineSampleCount,
        recentSampleCount = recentSampleCount
    )
}

private fun calculateOverallTrendStatus(
    fuelConsumption: MetricTrend?,
    electricConsumption: MetricTrend?,
    costPerHundredKm: MetricTrend
): OverallTrendStatus {
    val consumptionTrends = listOfNotNull(fuelConsumption, electricConsumption)
    val allTrends = consumptionTrends + costPerHundredKm
    if (allTrends.none { it.status != TrendStatus.INSUFFICIENT_DATA }) {
        return OverallTrendStatus.INSUFFICIENT_DATA
    }

    val relevantConsumptionUp = consumptionTrends.any {
        it.status == TrendStatus.UP && it.magnitude == TrendMagnitude.RELEVANT
    }
    val relevantConsumptionDown = consumptionTrends.any {
        it.status == TrendStatus.DOWN && it.magnitude == TrendMagnitude.RELEVANT
    }
    val relevantCostUp = costPerHundredKm.status == TrendStatus.UP &&
        costPerHundredKm.magnitude == TrendMagnitude.RELEVANT
    val relevantCostDown = costPerHundredKm.status == TrendStatus.DOWN &&
        costPerHundredKm.magnitude == TrendMagnitude.RELEVANT

    if (
        (relevantConsumptionUp && relevantCostDown) ||
        (relevantConsumptionDown && relevantCostUp)
    ) {
        return OverallTrendStatus.MIXED_CHANGES
    }
    if (relevantConsumptionUp) return OverallTrendStatus.CONSUMPTION_UP
    if (relevantConsumptionDown) return OverallTrendStatus.CONSUMPTION_DOWN
    if (relevantCostDown) return OverallTrendStatus.COST_DOWN
    if (relevantCostUp) return OverallTrendStatus.COST_UP
    if (allTrends.all { it.status == TrendStatus.STABLE || it.status == TrendStatus.INSUFFICIENT_DATA }) {
        return OverallTrendStatus.STABLE
    }
    return OverallTrendStatus.CHANGES
}

private data class TrendWindows<T>(
    val baseline: List<T>,
    val recent: List<T>
)

private fun <T> List<T>.toTrendWindows(): TrendWindows<T>? {
    if (size < MIN_TREND_RECENT_SAMPLES + MIN_TREND_BASELINE_SAMPLES) return null

    val recent = takeLast(MIN_TREND_RECENT_SAMPLES)
    val baseline = dropLast(MIN_TREND_RECENT_SAMPLES).takeLast(MAX_TREND_BASELINE_SAMPLES)
    return TrendWindows(baseline = baseline, recent = recent)
}
