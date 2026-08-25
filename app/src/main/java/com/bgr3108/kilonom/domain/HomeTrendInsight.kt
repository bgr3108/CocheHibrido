package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.isPlugInHybrid
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries

enum class HomeTrendMetric {
    INSUFFICIENT_DATA,
    STABLE,
    FUEL_CONSUMPTION,
    ELECTRIC_CONSUMPTION,
    PHEV_ENERGY_CHARGED,
    COST_PER_HUNDRED_KM
}

/**
 * A deliberately compact, deterministic summary for Home. Detailed baselines and all secondary
 * trends remain in Statistics > Trends.
 */
data class HomeTrendInsight(
    val metric: HomeTrendMetric,
    val status: TrendStatus,
    val magnitude: TrendMagnitude = TrendMagnitude.NONE,
    val percentageChange: Double? = null
)

/**
 * Selects exactly one conclusion using this priority:
 * relevant consumption, relevant cost, slight consumption, slight cost, stable, insufficient.
 * When fuel and electric consumption have the same priority, fuel is deliberately first.
 */
fun selectHomeTrendInsight(
    summary: VehicleTrendSummary,
    vehicleType: VehicleType?
): HomeTrendInsight {
    val consumptionCandidates = buildList {
        if (vehicleType.supportsFuelEntries) {
            summary.fuelConsumption?.toHomeInsight(HomeTrendMetric.FUEL_CONSUMPTION)?.let(::add)
        }
        if (vehicleType.supportsElectricEntries) {
            summary.electricConsumption?.toHomeInsight(
                if (vehicleType.isPlugInHybrid) {
                    HomeTrendMetric.PHEV_ENERGY_CHARGED
                } else {
                    HomeTrendMetric.ELECTRIC_CONSUMPTION
                }
            )?.let(::add)
        }
    }
    val costCandidate = summary.costPerHundredKm.toHomeInsight(HomeTrendMetric.COST_PER_HUNDRED_KM)

    return consumptionCandidates.firstOrNull(::isRelevantChange)
        ?: costCandidate.takeIf(::isRelevantChange)
        ?: consumptionCandidates.firstOrNull(::isSlightChange)
        ?: costCandidate.takeIf(::isSlightChange)
        ?: stableInsight(consumptionCandidates, costCandidate)
        ?: HomeTrendInsight(
            metric = HomeTrendMetric.INSUFFICIENT_DATA,
            status = TrendStatus.INSUFFICIENT_DATA
        )
}

private fun MetricTrend.toHomeInsight(metric: HomeTrendMetric) = HomeTrendInsight(
    metric = metric,
    status = status,
    magnitude = magnitude,
    percentageChange = percentageChange
)

private fun isRelevantChange(insight: HomeTrendInsight): Boolean =
    insight.magnitude == TrendMagnitude.RELEVANT &&
        (insight.status == TrendStatus.UP || insight.status == TrendStatus.DOWN)

private fun isSlightChange(insight: HomeTrendInsight): Boolean =
    insight.magnitude == TrendMagnitude.SLIGHT &&
        (insight.status == TrendStatus.UP || insight.status == TrendStatus.DOWN)

private fun stableInsight(
    consumptionCandidates: List<HomeTrendInsight>,
    costCandidate: HomeTrendInsight
): HomeTrendInsight? {
    val availableInsights = consumptionCandidates + costCandidate
    return if (
        availableInsights.any { it.status == TrendStatus.STABLE } &&
        availableInsights.all {
            it.status == TrendStatus.STABLE || it.status == TrendStatus.INSUFFICIENT_DATA
        }
    ) {
        HomeTrendInsight(
            metric = HomeTrendMetric.STABLE,
            status = TrendStatus.STABLE
        )
    } else {
        null
    }
}
