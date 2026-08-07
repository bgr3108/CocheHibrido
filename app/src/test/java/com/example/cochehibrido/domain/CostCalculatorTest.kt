package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun freeElectricCharge_isIncludedAsAValidEconomicEntry() {
        assertEquals(
            1,
            calculateFreeCharges(
                listOf(entry(FuelType.ELECTRICO, price = 0.0, quantity = 20.0))
            )
        )
    }

    @Test
    fun zeroQuantity_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(entry(FuelType.GASOLINA, price = 50.0, quantity = 0.0))
            ),
            0.0
        )
    }

    @Test
    fun negativeQuantity_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(entry(FuelType.GASOLINA, price = 50.0, quantity = -1.0))
            ),
            0.0
        )
    }

    @Test
    fun nanQuantity_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(entry(FuelType.GASOLINA, price = 50.0, quantity = Double.NaN))
            ),
            0.0
        )
    }

    @Test
    fun infiniteQuantity_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(
                    entry(
                        FuelType.GASOLINA,
                        price = 50.0,
                        quantity = Double.POSITIVE_INFINITY
                    )
                )
            ),
            0.0
        )
    }

    @Test
    fun negativePrice_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(entry(FuelType.GASOLINA, price = -1.0, quantity = 10.0))
            ),
            0.0
        )
    }

    @Test
    fun nanPrice_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(entry(FuelType.GASOLINA, price = Double.NaN, quantity = 10.0))
            ),
            0.0
        )
    }

    @Test
    fun infinitePrice_isIgnoredByFuelCost() {
        assertEquals(
            0.0,
            calculateTotalFuelCost(
                listOf(
                    entry(
                        FuelType.GASOLINA,
                        price = Double.POSITIVE_INFINITY,
                        quantity = 10.0
                    )
                )
            ),
            0.0
        )
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
    fun mixedValidAndInvalidEntries_sumOnlyValidEconomicCosts() {
        assertEquals(
            32.0,
            calculateTotalCost(
                listOf(
                    entry(FuelType.GASOLINA, price = 20.0, quantity = 10.0),
                    entry(FuelType.ELECTRICO, price = 12.0, quantity = 20.0),
                    entry(FuelType.GASOLINA, price = 50.0, quantity = 0.0),
                    entry(FuelType.ELECTRICO, price = Double.NaN, quantity = 10.0)
                )
            ),
            0.0
        )
    }

    @Test
    fun invalidFreeCharge_isNotCounted() {
        assertEquals(
            1,
            calculateFreeCharges(
                listOf(
                    entry(FuelType.ELECTRICO, price = 0.0, quantity = 20.0),
                    entry(FuelType.ELECTRICO, price = 0.0, quantity = 0.0)
                )
            )
        )
    }

    @Test
    fun zeroDistance_keepsCostPerKilometerAtZero() {
        assertEquals(
            0.0,
            calculateCostPerKilometer(totalCost = 36.0, totalKilometers = 0.0),
            0.0
        )
    }

    @Test
    fun overflowingCostPerKilometer_returnsAFiniteValue() {
        assertTrue(
            calculateCostPerKilometer(
                totalCost = Double.MAX_VALUE,
                totalKilometers = Double.MIN_VALUE
            ).isFinite()
        )
    }

    private fun entry(
        type: FuelType,
        price: Double,
        quantity: Double = 1.0
    ) = FuelEntry(
        fecha = 0L,
        cantidad = quantity,
        precio = price,
        tipo = type,
        km = 0.0
    )
}
