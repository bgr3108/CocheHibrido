package com.bgr3108.kilonom.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleCurrentKmCalculatorTest {

    @Test
    fun noEntries_returnsInitialKilometers() {
        assertEquals(50_000.0, calculateVehicleCurrentKm(50_000.0, emptyList()), 0.0)
    }

    @Test
    fun highestValidEntry_returnsTheDerivedCurrentKilometers() {
        assertEquals(
            50_360.0,
            calculateVehicleCurrentKm(50_000.0, listOf(50_100.0, 50_360.0, 50_200.0)),
            0.0
        )
    }

    @Test
    fun incoherentHistoricalEntry_neverMovesTheOdometerBelowInitialKilometers() {
        assertEquals(50_000.0, calculateVehicleCurrentKm(50_000.0, listOf(49_000.0)), 0.0)
    }

    @Test
    fun invalidKilometers_areIgnored() {
        assertEquals(
            50_360.0,
            calculateVehicleCurrentKm(
                50_000.0,
                listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 50_360.0)
            ),
            0.0
        )
    }
}
