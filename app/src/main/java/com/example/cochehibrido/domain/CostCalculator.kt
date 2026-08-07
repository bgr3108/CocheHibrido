package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateTotalCost(
    entries: List<FuelEntry>
): Double = sumValidEconomicValues(entries) { it.precio }

fun calculateCostPerKilometer(
    totalCost: Double,
    totalKilometers: Double
): Double {
    if (
        !totalCost.isFinite() ||
        totalCost < 0.0 ||
        !totalKilometers.isFinite() ||
        totalKilometers <= 0.0
    ) {
        return 0.0
    }

    return (totalCost / totalKilometers)
        .takeIf { it.isFinite() }
        ?: 0.0
}

fun calculateTotalFuelCost(
    entries: List<FuelEntry>
): Double = sumValidEconomicValues(entries, FuelType.GASOLINA) { it.precio }

fun calculateMostExpensiveRefuel(
    entries: List<FuelEntry>
): Double = validEconomicEntries(entries, FuelType.GASOLINA)
    .maxOfOrNull { it.precio }
    ?: 0.0

fun calculateCheapestRefuel(
    entries: List<FuelEntry>
): Double = validEconomicEntries(entries, FuelType.GASOLINA)
    .minOfOrNull { it.precio }
    ?: 0.0

fun calculateTotalElectricCost(
    entries: List<FuelEntry>
): Double = sumValidEconomicValues(entries, FuelType.ELECTRICO) { it.precio }

fun calculateMostExpensiveCharge(
    entries: List<FuelEntry>
): Double = validEconomicEntries(entries, FuelType.ELECTRICO)
    .maxOfOrNull { it.precio }
    ?: 0.0

fun calculateCheapestPaidCharge(
    entries: List<FuelEntry>
): Double = validEconomicEntries(entries, FuelType.ELECTRICO)
    .filter { it.precio > 0.0 }
    .minOfOrNull { it.precio }
    ?: 0.0

fun calculateFreeCharges(
    entries: List<FuelEntry>
): Int = validEconomicEntries(entries, FuelType.ELECTRICO)
    .count { it.precio == 0.0 }
