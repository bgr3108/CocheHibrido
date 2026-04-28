package com.example.cochehibrido.domain

data class ConsumptionStats(
    val totalKm: Double,
    val totalCost: Double,
    val costPerKm: Double,
    val totalGasolina: Double,
    val totalElectrico: Double
)