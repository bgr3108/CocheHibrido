package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry

fun calculateTravelledKilometers(
    entries: List<FuelEntry>,
    initialKilometers: Double
): Double {
    val safeInitialKilometers = initialKilometers
        .takeIf { it.isFinite() && it >= 0.0 }
        ?: 0.0

    val lastKilometers = entries
        .asSequence()
        .map { it.km }
        .filter { it.isFinite() && it >= 0.0 }
        .maxOrNull()
        ?: return 0.0

    return (lastKilometers - safeInitialKilometers)
        .takeIf { it.isFinite() && it > 0.0 }
        ?: 0.0
}
