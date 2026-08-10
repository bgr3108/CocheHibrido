package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCalculatorTest {

    @Test
    fun zeroQuantity_hasNoUnitPrice() {
        assertNull(calculateUnitPrice(entry(quantity = 0.0, price = 50.0)))
    }

    @Test
    fun zeroAmount_hasZeroUnitPrice() {
        assertEquals(0.0, requireNotNull(calculateUnitPrice(entry(quantity = 20.0, price = 0.0))), 0.0)
    }

    @Test
    fun freeElectricCharge_hasZeroAveragePrice() {
        assertEquals(
            0.0,
            calculateAverageElectricPrice(listOf(entry(FuelType.ELECTRICO, quantity = 20.0, price = 0.0))),
            0.0
        )
    }

    @Test
    fun nanEntry_isIgnoredByFuelAverage() {
        assertEquals(
            1.5,
            calculateAverageFuelPrice(listOf(entry(quantity = 10.0, price = 15.0), entry(quantity = Double.NaN, price = 10.0))),
            0.0
        )
    }

    @Test
    fun infiniteEntry_isIgnoredByFuelAverage() {
        assertEquals(
            1.5,
            calculateAverageFuelPrice(
                listOf(entry(quantity = 10.0, price = 15.0), entry(quantity = 10.0, price = Double.POSITIVE_INFINITY))
            ),
            0.0
        )
    }

    @Test
    fun negativeQuantity_hasNoUnitPrice() {
        assertNull(calculateUnitPrice(entry(quantity = -1.0, price = 1.0)))
    }

    @Test
    fun negativeAmount_hasNoUnitPrice() {
        assertNull(calculateUnitPrice(entry(quantity = 1.0, price = -1.0)))
    }

    @Test
    fun multipleValidFuelEntries_calculateWeightedAveragePrice() {
        assertEquals(
            8.0 / 3.0,
            calculateAverageFuelPrice(listOf(entry(quantity = 10.0, price = 20.0), entry(quantity = 20.0, price = 60.0))),
            0.0
        )
    }

    @Test
    fun multipleValidFuelEntries_calculateMinimumUnitPrice() {
        assertEquals(
            2.0,
            calculateMinFuelPrice(listOf(entry(quantity = 10.0, price = 20.0), entry(quantity = 20.0, price = 60.0))),
            0.0
        )
    }

    @Test
    fun multipleValidFuelEntries_calculateMaximumUnitPrice() {
        assertEquals(
            3.0,
            calculateMaxFuelPrice(listOf(entry(quantity = 10.0, price = 20.0), entry(quantity = 20.0, price = 60.0))),
            0.0
        )
    }

    @Test
    fun allPriceMetrics_areFiniteForInvalidInput() {
        val entries = listOf(
            entry(quantity = 0.0, price = 1.0),
            entry(quantity = Double.NaN, price = 1.0),
            entry(quantity = 1.0, price = Double.POSITIVE_INFINITY),
            entry(quantity = -1.0, price = 1.0)
        )

        assertTrue(
            listOf(
                calculateAverageFuelPrice(entries),
                calculateMinFuelPrice(entries),
                calculateMaxFuelPrice(entries),
                calculateAverageElectricPrice(entries),
                calculateMinElectricPrice(entries),
                calculateMaxElectricPrice(entries)
            ).all { it.isFinite() }
        )
    }

    private fun entry(
        type: FuelType = FuelType.GASOLINA,
        quantity: Double,
        price: Double
    ) = FuelEntry(
        fecha = 0L,
        cantidad = quantity,
        precio = price,
        tipo = type,
        km = 0.0
    )
}
