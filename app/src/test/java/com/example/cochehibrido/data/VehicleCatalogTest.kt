package com.example.cochehibrido.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleCatalogTest {

    @Test
    fun cars_useTheVehiclesCatalog() {
        assertEquals("vehicles.json", catalogFileNameFor(VehicleCategory.COCHE))
    }

    @Test
    fun motorcycles_useTheMotorcyclesCatalog() {
        assertEquals("motorcycles.json", catalogFileNameFor(VehicleCategory.MOTO))
    }

    @Test
    fun categoryChange_makesAnIncompatibleVehicleSelectionInvalid() {
        val car = VehicleInfo(
            brand = "Marca",
            model = "Modelo",
            year = 2024,
            category = VehicleCategory.COCHE,
            type = VehicleType.GASOLINA,
            batteryCapacity = 0.0,
            fuelTankCapacity = 40.0
        )

        assertFalse(isVehicleSelectionCompatible(car, VehicleCategory.MOTO))
    }
}
