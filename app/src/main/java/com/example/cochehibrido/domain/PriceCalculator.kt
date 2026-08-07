package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateUnitPrice(entry: FuelEntry): Double? {
    if (!isValidEconomicEntry(entry)) {
        return null
    }

    return (entry.precio / entry.cantidad)
        .takeIf { it.isFinite() }
}

fun calculateAverageFuelPrice(
    entries: List<FuelEntry>
): Double {

    val litros =
        sumValidEconomicValues(entries, FuelType.GASOLINA) { it.cantidad }

    val gasto =
        sumValidEconomicValues(entries, FuelType.GASOLINA) { it.precio }

    return if (litros > 0)
        (gasto / litros).takeIf { it.isFinite() } ?: 0.0
    else
        0.0
}

fun calculateAverageElectricPrice(
    entries: List<FuelEntry>
): Double {

    val kwh =
        sumValidEconomicValues(entries, FuelType.ELECTRICO) { it.cantidad }

    val gasto =
        sumValidEconomicValues(entries, FuelType.ELECTRICO) { it.precio }

    return if (kwh > 0)
        (gasto / kwh).takeIf { it.isFinite() } ?: 0.0
    else
        0.0
}

fun calculateMinFuelPrice(
    entries: List<FuelEntry>
): Double {

    return validEconomicEntries(entries, FuelType.GASOLINA)
        .mapNotNull(::calculateUnitPrice)
        .minOrNull() ?: 0.0
}

fun calculateMaxFuelPrice(
    entries: List<FuelEntry>
): Double {

    return validEconomicEntries(entries, FuelType.GASOLINA)
        .mapNotNull(::calculateUnitPrice)
        .maxOrNull() ?: 0.0
}

fun calculateMinElectricPrice(
    entries: List<FuelEntry>
): Double {

    return validEconomicEntries(entries, FuelType.ELECTRICO)
        .mapNotNull(::calculateUnitPrice)
        .minOrNull() ?: 0.0
}

fun calculateMaxElectricPrice(
    entries: List<FuelEntry>
): Double {

    return validEconomicEntries(entries, FuelType.ELECTRICO)
        .mapNotNull(::calculateUnitPrice)
        .maxOrNull() ?: 0.0
}

fun calculateFuelRefuels(
    entries: List<FuelEntry>
): Int {

    return validEconomicEntries(entries, FuelType.GASOLINA).size
}

fun calculateElectricCharges(
    entries: List<FuelEntry>
): Int {

    return validEconomicEntries(entries, FuelType.ELECTRICO).size
}
