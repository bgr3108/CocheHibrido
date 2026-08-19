package com.bgr3108.kilonom.domain

data class FuelConsumptionSegment(

    val startKm: Double,

    val endKm: Double,

    val distance: Double,

    val fuelUsed: Double,

    val consumption: Double

)

/**
 * A provisional consumption estimate from the latest confirmed full tank to the latest
 * partial refuel with a selected tank level. It is intentionally separate from historical
 * full-to-full segments.
 */
data class CurrentFuelConsumptionEstimate(
    val startKm: Double,
    val endKm: Double,
    val distance: Double,
    val fuelUsed: Double,
    val consumption: Double
)
