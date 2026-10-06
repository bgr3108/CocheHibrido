package com.bgr3108.kilonom.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleDataSourceRuntimeCatalogTest {

    @Test
    fun bundledRuntimeSeedIsReadableThroughTheProductionDataSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = VehicleDataSource(context)

        val cars = source.loadVehicles(VehicleCategory.COCHE)
        val motorcycles = source.loadVehicles(VehicleCategory.MOTO)
        val selector = source.loadSelectionCatalog(VehicleCategory.COCHE)

        assertTrue(cars.any { it.brand == "SEAT" && it.model.contains("e-Hybrid 1.5") && it.year == 2025 })
        assertTrue(motorcycles.any { it.type == VehicleType.ELECTRICO })
        assertTrue(selector.modelsFor(selector.brands().single { it.displayName == "Opel" }.id).any { it.displayName == "Corsa" })
    }
}
