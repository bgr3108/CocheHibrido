package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateAverageFuelPrice(
    entries: List<FuelEntry>
): Double {

    val fuelEntries = entries.filter {
        it.tipo == FuelType.GASOLINA
    }

    val litros =
        fuelEntries.sumOf { it.cantidad }

    val gasto =
        fuelEntries.sumOf { it.precio }

    return if (litros > 0)
        gasto / litros
    else
        0.0
}

fun calculateAverageElectricPrice(
    entries: List<FuelEntry>
): Double {

    val electricEntries = entries.filter {
        it.tipo == FuelType.ELECTRICO
    }

    val kwh =
        electricEntries.sumOf { it.cantidad }

    val gasto =
        electricEntries.sumOf { it.precio }

    return if (kwh > 0)
        gasto / kwh
    else
        0.0
}

fun calculateMinFuelPrice(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.GASOLINA
        }
        .minOfOrNull {
            it.precio / it.cantidad
        } ?: 0.0
}

fun calculateMaxFuelPrice(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.GASOLINA
        }
        .maxOfOrNull {
            it.precio / it.cantidad
        } ?: 0.0
}

fun calculateMinElectricPrice(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.ELECTRICO
        }
        .minOfOrNull {
            it.precio / it.cantidad
        } ?: 0.0
}

fun calculateMaxElectricPrice(
    entries: List<FuelEntry>
): Double {

    return entries
        .filter {
            it.tipo == FuelType.ELECTRICO
        }
        .maxOfOrNull {
            it.precio / it.cantidad
        } ?: 0.0
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