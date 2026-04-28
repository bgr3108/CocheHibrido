package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateConsumption(entries: List<FuelEntry>): ConsumptionStats {

    if (entries.size < 2) {
        return ConsumptionStats(
            totalKm = 0.0,
            totalCost = 0.0,
            costPerKm = 0.0,
            totalGasolina = 0.0,
            totalElectrico = 0.0
        )
    }

    val sorted = entries.sortedBy { it.km }

    val totalKm = sorted.last().km - sorted.first().km

    val totalCost = sorted.sumOf { it.cantidad * it.precio }

    val totalGasolina = sorted
        .filter { it.tipo == FuelType.GASOLINA }
        .sumOf { it.cantidad }

    val totalElectrico = sorted
        .filter { it.tipo == FuelType.ELECTRICO }
        .sumOf { it.cantidad }

    val costPerKm = if (totalKm > 0) totalCost / totalKm else 0.0

    return ConsumptionStats(
        totalKm = totalKm,
        totalCost = totalCost,
        costPerKm = costPerKm,
        totalGasolina = totalGasolina,
        totalElectrico = totalElectrico
    )
}