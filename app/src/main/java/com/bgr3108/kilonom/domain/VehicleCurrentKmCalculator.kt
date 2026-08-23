package com.bgr3108.kilonom.domain

import kotlin.math.max

/** Single source of truth for the current odometer value displayed by the app. */
fun calculateVehicleCurrentKm(initialKm: Double, entryKilometers: Iterable<Double>): Double {
    val safeInitialKm = initialKm.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    val highestRecordedKm = entryKilometers
        .filter { it.isFinite() && it >= 0.0 }
        .maxOrNull()
        ?: safeInitialKm

    // Historical data can predate a corrected initial kilometre value. Never move the odometer
    // backwards just because such an entry exists.
    return max(safeInitialKm, highestRecordedKm)
}
