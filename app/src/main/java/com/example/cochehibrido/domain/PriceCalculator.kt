package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateUnitPrice(entry: FuelEntry): Double? {
    if (
        !entry.cantidad.isFinite() ||
        entry.cantidad <= 0.0 ||
        !entry.precio.isFinite() ||
        entry.precio < 0.0
    ) {
        return null
    }

    return (entry.precio / entry.cantidad)
        .takeIf { it.isFinite() }
}

private fun validPriceEntries(
    entries: List<FuelEntry>,
    type: FuelType
): List<FuelEntry> = entries.filter {
    it.tipo == type && calculateUnitPrice(it) != null
}

private fun sumFinite(
    entries: List<FuelEntry>,
    value: (FuelEntry) -> Double
): Double = entries.fold(0.0) { total, entry ->
    (total + value(entry)).takeIf { it.isFinite() } ?: total
}

fun calculateAverageFuelPrice(
    entries: List<FuelEntry>
): Double {

    val fuelEntries = validPriceEntries(entries, FuelType.GASOLINA)

    val litros =
        sumFinite(fuelEntries) { it.cantidad }

    val gasto =
        sumFinite(fuelEntries) { it.precio }

    return if (litros > 0)
        (gasto / litros).takeIf { it.isFinite() } ?: 0.0
    else
        0.0
}

fun calculateAverageElectricPrice(
    entries: List<FuelEntry>
): Double {

    val electricEntries = validPriceEntries(entries, FuelType.ELECTRICO)

    val kwh =
        sumFinite(electricEntries) { it.cantidad }

    val gasto =
        sumFinite(electricEntries) { it.precio }

    return if (kwh > 0)
        (gasto / kwh).takeIf { it.isFinite() } ?: 0.0
    else
        0.0
}

fun calculateMinFuelPrice(
    entries: List<FuelEntry>
): Double {

    return validPriceEntries(entries, FuelType.GASOLINA)
        .mapNotNull(::calculateUnitPrice)
        .minOrNull() ?: 0.0
}

fun calculateMaxFuelPrice(
    entries: List<FuelEntry>
): Double {

    return validPriceEntries(entries, FuelType.GASOLINA)
        .mapNotNull(::calculateUnitPrice)
        .maxOrNull() ?: 0.0
}

fun calculateMinElectricPrice(
    entries: List<FuelEntry>
): Double {

    return validPriceEntries(entries, FuelType.ELECTRICO)
        .mapNotNull(::calculateUnitPrice)
        .minOrNull() ?: 0.0
}

fun calculateMaxElectricPrice(
    entries: List<FuelEntry>
): Double {

    return validPriceEntries(entries, FuelType.ELECTRICO)
        .mapNotNull(::calculateUnitPrice)
        .maxOrNull() ?: 0.0
}

fun calculateFuelRefuels(
    entries: List<FuelEntry>
): Int {

    return entries.count {
        it.tipo == FuelType.GASOLINA
    }
}

fun calculateElectricCharges(
    entries: List<FuelEntry>
): Int {

    return entries.count {
        it.tipo == FuelType.ELECTRICO
    }
}
