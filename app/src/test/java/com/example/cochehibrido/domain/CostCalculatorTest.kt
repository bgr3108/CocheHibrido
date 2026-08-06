package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    @Test
    fun emptyHistory_hasZeroFuelCost() {
        assertEquals(0.0, calculateTotalFuelCost(emptyList()), 0.0)
    }

    @Test
    fun fuelEntries_sumOnlyFuelCost() {
        assertEquals(50.0, calculateTotalFuelCost(listOf(entry(FuelType.GASOLINA, 20.0), entry(FuelType.GASOLINA, 30.0))), 0.0)
    }

    @Test
    fun electricEntries_sumOnlyElectricCost() {
        assertEquals(12.0, calculateTotalElectricCost(listOf(entry(FuelType.ELECTRICO, 5.0), entry(FuelType.ELECTRICO, 7.0))), 0.0)
    }

    @Test
    fun mixedEntries_keepFuelAndElectricCostsSeparated() {
        assertEquals(
            50.0,
            calculateTotalFuelCost(listOf(entry(FuelType.GASOLINA, 50.0), entry(FuelType.ELECTRICO, 12.0))),
            0.0
        )
    }

    @Test
    fun zeroDistance_keepsCostPerKilometerAtZero() {
        assertEquals(0.0, calculateConsumption(emptyList()).costPerKm, 0.0)
    }

    private fun entry(type: FuelType, price: Double) = FuelEntry(
        fecha = 0L,
        cantidad = 1.0,
        precio = price,
        tipo = type,
        km = 0.0
    )
}
