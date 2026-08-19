package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelLevelAfterTest {

    @Test
    fun fullLevelMarksTheEntryAsFullTank() {
        assertTrue(isFullTankLevel(1.0))
    }

    @Test
    fun newFuelEntryDefaultsToFullLevel() {
        assertEquals(1.0, requireNotNull(initialFuelLevelAfter(null)), 0.0)
    }

    @Test
    fun lowerLevelMarksTheEntryAsPartial() {
        assertFalse(isFullTankLevel(0.875))
    }

    @Test
    fun historicalPartialEntryKeepsItsUnknownLevel() {
        val entry = sampleHistoricalEntry(fullTank = false)

        assertNull(initialFuelLevelAfter(entry))
    }

    @Test
    fun historicalFullEntryUsesTheSafelyEquivalentFullLevel() {
        val entry = sampleHistoricalEntry(fullTank = true)

        assertEquals(1.0, requireNotNull(initialFuelLevelAfter(entry)), 0.0)
    }

    @Test
    fun onlyTheNineSupportedLevelsAreAccepted() {
        assertTrue(isSupportedFuelLevelAfter(0.625))
        assertFalse(isSupportedFuelLevelAfter(0.6))
    }

    @Test
    fun selectedPartialLevel_hasAClearHistoryLabel() {
        assertEquals("62,5 %", fuelLevelAfterPercentageText(0.625))
    }

    private fun sampleHistoricalEntry(fullTank: Boolean) = FuelEntry(
        fecha = 1L,
        cantidad = 10.0,
        precio = 10.0,
        tipo = FuelType.GASOLINA,
        km = 100.0,
        fullTank = fullTank,
        fuelLevelAfter = null
    )
}
