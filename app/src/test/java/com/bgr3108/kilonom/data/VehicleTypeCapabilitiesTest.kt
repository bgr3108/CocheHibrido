package com.bgr3108.kilonom.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTypeCapabilitiesTest {

    @Test
    fun combustionTypes_supportFuelEntriesOnly() {
        assertTrue(VehicleType.GASOLINA.supportsFuelEntries)
        assertFalse(VehicleType.GASOLINA.supportsElectricEntries)
        assertTrue(VehicleType.DIESEL.supportsFuelEntries)
        assertFalse(VehicleType.DIESEL.supportsElectricEntries)
    }

    @Test
    fun electricType_supportsElectricEntriesOnly() {
        assertFalse(VehicleType.ELECTRICO.supportsFuelEntries)
        assertTrue(VehicleType.ELECTRICO.supportsElectricEntries)
    }

    @Test
    fun plugInHybrid_supportsFuelAndElectricEntries() {
        assertTrue(VehicleType.HIBRIDO_ENCHUFABLE.supportsFuelEntries)
        assertTrue(VehicleType.HIBRIDO_ENCHUFABLE.supportsElectricEntries)
    }

    @Test
    fun nonPlugInHybrid_supportsFuelEntriesOnly() {
        assertTrue(VehicleType.HIBRIDO.supportsFuelEntries)
        assertFalse(VehicleType.HIBRIDO.supportsElectricEntries)
    }
}
