package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateTotalFuelCost(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.GASOLINA
        }
        .sumOf {
            it.precio
        }

}

fun calculateMostExpensiveRefuel(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.GASOLINA
        }
        .maxOfOrNull {
            it.precio
        } ?: 0.0

}

fun calculateCheapestRefuel(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.GASOLINA
        }
        .minOfOrNull {
            it.precio
        } ?: 0.0

}

fun calculateTotalElectricCost(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.ELECTRICO
        }
        .sumOf {
            it.precio
        }

}

fun calculateMostExpensiveCharge(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.ELECTRICO
        }
        .maxOfOrNull {
            it.precio
        } ?: 0.0

}

fun calculateCheapestPaidCharge(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.ELECTRICO
        }
        .filter {
            it.precio > 0.0
        }
        .minOfOrNull {
            it.precio
        } ?: 0.0

}

fun calculateFreeCharges(
    entries: List<FuelEntry>
): Int {

    return entries.count {

        it.tipo == FuelType.ELECTRICO &&
                it.precio == 0.0

    }

}