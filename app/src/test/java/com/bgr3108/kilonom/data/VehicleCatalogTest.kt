package com.bgr3108.kilonom.data

import org.junit.Assert.assertFalse
import org.junit.Test

class VehicleCatalogTest {

    @Test
    fun runtimeCatalogUsesTheBundledSeed() {
        assertFalse(RUNTIME_CATALOG_ASSET.isBlank())
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
