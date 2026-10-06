package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleSelectionCatalogTest {

    private val cars by lazy { parseRuntimeVehicleCatalog(readRuntime()).selectionFor(VehicleCategory.COCHE) }
    private val motorcycles by lazy { parseRuntimeVehicleCatalog(readRuntime()).selectionFor(VehicleCategory.MOTO) }

    @Test
    fun selectorGroupsLegacyCarsByFamiliarBaseModelBeforeChoosingYear() {
        val opel = cars.brands().single { it.displayName == "Opel" }
        val corsa = cars.modelsFor(opel.id).single { it.displayName == "Corsa" }

        assertTrue(2008 in cars.yearsFor(opel.id, corsa.id))
        assertTrue(2012 in cars.yearsFor(opel.id, corsa.id))
        assertTrue(2024 in cars.yearsFor(opel.id, corsa.id))
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2008).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2020).map { it.displayName })
        assertEquals(listOf("Híbrido"), cars.variantsFor(opel.id, corsa.id, 2024).map { it.displayName })
        assertTrue(cars.variantsFor(opel.id, corsa.id, 2012).isEmpty())
    }

    @Test
    fun selectorShowsOnlyFunctionallyNecessaryHumanVariants() {
        val seat = cars.brandId("SEAT")
        val leon = cars.modelId(seat, "León")
        assertEquals(listOf("Híbrido enchufable"), cars.variantsFor(seat, leon, 2021).map { it.displayName })
        assertEquals(listOf("Híbrido enchufable"), cars.variantsFor(seat, leon, 2024).map { it.displayName })
        val leon2025 = cars.variantsFor(seat, leon, 2025)
        assertEquals(listOf("Híbrido enchufable"), leon2025.map { it.displayName })
        assertEquals("e-Hybrid 1.5", leon2025.single().automaticDisplayName)
        assertEquals(listOf("Híbrido enchufable"), cars.variantsFor(seat, leon, 2026).map { it.displayName })

        val nissan = cars.brandId("Nissan")
        assertEquals(
            listOf("Diésel", "Gasolina"),
            cars.variantsFor(nissan, cars.modelId(nissan, "Qashqai"), 2015).map { it.displayName }
        )
        val citroen = cars.brandId("Citroën")
        assertEquals(
            listOf("Diésel", "Gasolina"),
            cars.variantsFor(citroen, cars.modelId(citroen, "Berlingo"), 2018).map { it.displayName }
        )
        val toyota = cars.brandId("Toyota")
        assertEquals(
            listOf("Híbrido"),
            cars.variantsFor(toyota, cars.modelId(toyota, "Yaris"), 2017).map { it.displayName }
        )
        val volkswagen = cars.brandId("Volkswagen")
        assertEquals(
            listOf("Diésel", "Híbrido enchufable"),
            cars.variantsFor(volkswagen, cars.modelId(volkswagen, "Golf"), 2023).map { it.displayName }
        )
    }

    @Test
    fun motorcyclesKeepTheirSimpleModelNamesAndAutoSelectSingleFunctionalOption() {
        assertSingleMotorcycle("Honda", "CB500F", 2020)
        assertSingleMotorcycle("Yamaha", "MT-07", 2022)
        assertSingleMotorcycle("Kawasaki", "Versys 650", 2018)
        assertSingleMotorcycle("Zero", "SR/F", 2023)
    }

    @Test
    fun runtimeMarketCodesAreStrictStringValues() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        assertTrue(runtime.vehicles.all { it.marketCodes == listOf("ES") })
    }

    @Test
    fun everyLegacyKeyStillResolvesToOneRuntimeSelection() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val activeLegacyKeys = runtime.vehicles.flatMap { it.legacyKeys }.map { it.toStableKey() }
        assertEquals(565, activeLegacyKeys.distinct().size)
        assertEquals(activeLegacyKeys.size, activeLegacyKeys.distinct().size)
    }

    private fun assertSingleMotorcycle(brand: String, model: String, year: Int) {
        val brandId = motorcycles.brandId(brand)
        val variants = motorcycles.variantsFor(brandId, motorcycles.modelId(brandId, model), year)
        assertEquals("$brand $model $year", 1, variants.size)
        assertFalse(variants.single().displayName.isBlank())
    }

    private fun VehicleSelectionCatalog.brandId(displayName: String): String =
        brands().single { it.displayName == displayName }.id

    private fun VehicleSelectionCatalog.modelId(brandId: String, displayName: String): String =
        modelsFor(brandId).single { it.displayName == displayName }.id

    private fun RuntimeLegacyKey.toStableKey(): String = "$category|$brand|$model|$year"

    private fun readRuntime(): String = findRuntimeAsset().readText()

    private fun findRuntimeAsset(): File = sequenceOf(
        File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
        File("src/main/assets", RUNTIME_CATALOG_ASSET),
        File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $RUNTIME_CATALOG_ASSET")
}
