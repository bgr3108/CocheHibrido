package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelEntryDraftTest {

    @Test
    fun editingFuelEntry_preservesEveryStoredValue() {
        val draft = FuelEntryDraft(
            id = 8,
            fecha = 1_725_000_123_456L,
            cantidad = 42.5,
            precio = 71.32,
            tipo = FuelType.GASOLINA,
            km = 123_456.0,
            fullTank = false,
            fuelLevelAfter = 0.625,
            electricChargeStartPercentage = null,
            electricChargeEndPercentage = null,
            originalVehicleId = 4L
        )

        val entry = draft.toFuelEntry(activeVehicleId = 4L)

        assertEquals(8, entry.id)
        assertEquals(1_725_000_123_456L, entry.fecha)
        assertEquals(42.5, entry.cantidad, 0.0)
        assertEquals(71.32, entry.precio, 0.0)
        assertEquals(FuelType.GASOLINA, entry.tipo)
        assertEquals(123_456.0, entry.km, 0.0)
        assertEquals(false, entry.fullTank)
        assertEquals(0.625, entry.fuelLevelAfter!!, 0.0)
        assertNull(entry.electricChargeStartPercentage)
        assertNull(entry.electricChargeEndPercentage)
    }

    @Test
    fun editingElectricEntry_preservesPercentagesAndDoesNotRecalculateAmount() {
        val draft = FuelEntryDraft(
            id = 9,
            fecha = 1_725_000_123_456L,
            cantidad = 8.7,
            precio = 4.35,
            tipo = FuelType.ELECTRICO,
            km = 123_480.0,
            fullTank = true,
            fuelLevelAfter = null,
            electricChargeStartPercentage = 20.0,
            electricChargeEndPercentage = 80.0,
            originalVehicleId = 4L
        )

        val entry = draft.toFuelEntry(activeVehicleId = 4L)

        assertEquals(8.7, entry.cantidad, 0.0)
        assertEquals(4.35, entry.precio, 0.0)
        assertEquals(20.0, entry.electricChargeStartPercentage!!, 0.0)
        assertEquals(80.0, entry.electricChargeEndPercentage!!, 0.0)
    }

    @Test
    fun historicalElectricEntryWithoutPercentages_remainsEditable() {
        val entry = FuelEntryDraft(
            id = 10,
            fecha = 1_725_000_123_456L,
            cantidad = 6.2,
            precio = 2.79,
            tipo = FuelType.ELECTRICO,
            km = 123_500.0,
            fullTank = true,
            fuelLevelAfter = null,
            electricChargeStartPercentage = null,
            electricChargeEndPercentage = null,
            originalVehicleId = 4L
        ).toFuelEntry(activeVehicleId = 4L)

        assertEquals(6.2, entry.cantidad, 0.0)
        assertNull(entry.electricChargeStartPercentage)
        assertNull(entry.electricChargeEndPercentage)
    }
}
