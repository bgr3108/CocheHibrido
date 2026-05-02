package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateConsumption(entries: List<FuelEntry>): ConsumptionStats {

    if (entries.isEmpty()) {
        return ConsumptionStats(
            totalKm = 0.0,
            totalCost = 0.0,
            costPerKm = 0.0,
            totalGasolina = 0.0,
            totalElectrico = 0.0
        )
    }

    val totalCost = entries.sumOf { it.precio }

    val totalGasolina = entries
        .filter { it.tipo == FuelType.GASOLINA }
        .sumOf { it.cantidad }

    val totalElectrico = entries
        .filter { it.tipo == FuelType.ELECTRICO }
        .sumOf { it.cantidad }

    return ConsumptionStats(
        totalKm = 0.0, // ❌ ya no usamos km aquí
        totalCost = totalCost,
        costPerKm = 0.0, // ❌ lo quitamos de aquí
        totalGasolina = totalGasolina,
        totalElectrico = totalElectrico
    )
}