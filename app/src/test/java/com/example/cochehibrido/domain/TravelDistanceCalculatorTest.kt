package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelDistanceCalculatorTest {

    @Test
    fun noEntries_returnsZeroKilometers() {
        assertEquals(0.0, calculateTravelledKilometers(emptyList(), 50_000.0), 0.0)
    }

    @Test
    fun lastKilometersEqualToInitial_returnsZeroKilometers() {
        assertEquals(0.0, calculateTravelledKilometers(listOf(entry(50_000.0)), 50_000.0), 0.0)
    }

    @Test
    fun lastKilometersLowerThanInitial_returnsZeroKilometers() {
        assertEquals(0.0, calculateTravelledKilometers(listOf(entry(49_900.0)), 50_000.0), 0.0)
    }

    @Test
    fun higherLastKilometers_returnsTravelledDistance() {
        assertEquals(360.0, calculateTravelledKilometers(listOf(entry(50_360.0)), 50_000.0), 0.0)
    }

    @Test
    fun changedInitialKilometers_recalculatesTravelledDistance() {
        assertEquals(260.0, calculateTravelledKilometers(listOf(entry(50_360.0)), 50_100.0), 0.0)
    }

    @Test
    fun unorderedEntries_usesHighestValidOdometer() {
        assertEquals(
            360.0,
            calculateTravelledKilometers(listOf(entry(50_120.0), entry(50_360.0), entry(50_050.0)), 50_000.0),
            0.0
        )
    }

    @Test
    fun negativeKilometers_areIgnored() {
        assertEquals(
            360.0,
            calculateTravelledKilometers(listOf(entry(-1.0), entry(50_360.0)), 50_000.0),
            0.0
        )
    }

    @Test
    fun nonFiniteKilometers_areIgnored() {
        assertEquals(
            360.0,
            calculateTravelledKilometers(
                listOf(entry(Double.NaN), entry(Double.POSITIVE_INFINITY), entry(50_360.0)),
                50_000.0
            ),
            0.0
        )
    }

    private fun entry(km: Double) = FuelEntry(
        fecha = 0L,
        cantidad = 1.0,
        precio = 1.0,
        tipo = FuelType.GASOLINA,
        km = km
    )
}
