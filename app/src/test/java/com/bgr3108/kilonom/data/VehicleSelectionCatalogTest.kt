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
        assertEquals(
            listOf(
                "Diésel · modelo anterior",
                "Diésel · modelo nuevo",
                "Gasolina · modelo anterior",
                "Gasolina · modelo nuevo"
            ),
            cars.variantsFor(opel.id, corsa.id, 2006).map { it.displayName }
        )
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2008).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2012).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2016).map { it.displayName })
        assertEquals(
            listOf(
                "Diésel · modelo anterior",
                "Diésel · modelo nuevo",
                "Gasolina · modelo anterior",
                "Gasolina · modelo nuevo"
            ),
            cars.variantsFor(opel.id, corsa.id, 2019).map { it.displayName }
        )
        assertEquals(listOf("Diésel", "Eléctrico", "Gasolina"), cars.variantsFor(opel.id, corsa.id, 2020).map { it.displayName })
        assertEquals(listOf("Eléctrico", "Gasolina", "Híbrido"), cars.variantsFor(opel.id, corsa.id, 2024).map { it.displayName })
        assertEquals(listOf("Eléctrico", "Gasolina", "Híbrido"), cars.variantsFor(opel.id, corsa.id, 2026).map { it.displayName })
    }

    @Test
    fun selectorShowsOnlyFunctionallyNecessaryHumanVariants() {
        val seat = cars.brandId("SEAT")
        val leon = cars.modelId(seat, "León")
        assertEquals(listOf("Diésel", "Gasolina", "Híbrido enchufable"), cars.variantsFor(seat, leon, 2021).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina", "Híbrido enchufable"), cars.variantsFor(seat, leon, 2024).map { it.displayName })
        val leon2025 = cars.variantsFor(seat, leon, 2025)
        assertEquals(listOf("Diésel", "Gasolina", "Híbrido enchufable"), leon2025.map { it.displayName })
        assertEquals("e-Hybrid 1.5", leon2025.single { it.displayName == "Híbrido enchufable" }.automaticDisplayName)
        assertEquals(listOf("Diésel", "Gasolina", "Híbrido enchufable"), cars.variantsFor(seat, leon, 2026).map { it.displayName })

        val nissan = cars.brandId("Nissan")
        assertEquals(
            listOf("Diésel", "Gasolina"),
            cars.variantsFor(nissan, cars.modelId(nissan, "Qashqai"), 2015).map { it.displayName }
        )
        val citroen = cars.brandId("Citroën")
        assertEquals(
            listOf("Diésel", "Eléctrico", "Gasolina"),
            cars.variantsFor(citroen, cars.modelId(citroen, "Berlingo"), 2018).map { it.displayName }
        )
        assertEquals(
            listOf("Diésel", "Gasolina"),
            cars.variantsFor(citroen, cars.modelId(citroen, "Berlingo"), 2008).map { it.displayName }
        )
        assertEquals(
            listOf("Diésel", "Eléctrico"),
            cars.variantsFor(citroen, cars.modelId(citroen, "Berlingo"), 2025).map { it.displayName }
        )
        assertEquals(
            listOf("Diésel", "Eléctrico", "Gasolina"),
            cars.variantsFor(citroen, cars.modelId(citroen, "Berlingo"), 2022).map { it.displayName }
        )
        val toyota = cars.brandId("Toyota")
        assertEquals(
            listOf("Gasolina", "Híbrido"),
            cars.variantsFor(toyota, cars.modelId(toyota, "Yaris"), 2017).map { it.displayName }
        )
        val volkswagen = cars.brandId("Volkswagen")
        assertEquals(
            listOf("Diésel", "Híbrido enchufable"),
            cars.variantsFor(volkswagen, cars.modelId(volkswagen, "Golf"), 2023).map { it.displayName }
        )
    }

    @Test
    fun researchedDenseFamiliesExposeOnlyFunctionalChoicesForTheirYears() {
        val nissan = cars.brandId("Nissan")
        val qashqai = cars.modelId(nissan, "Qashqai")

        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(nissan, qashqai, 2008).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(nissan, qashqai, 2015).map { it.displayName })
        assertEquals(listOf("Diésel", "Gasolina"), cars.variantsFor(nissan, qashqai, 2019).map { it.displayName })
        assertEquals(
            setOf("e-POWER híbrido", "Mild Hybrid híbrido"),
            cars.variantsFor(nissan, qashqai, 2024).map { it.displayName }.toSet()
        )
    }

    @Test
    fun additionalDenseFamiliesExposeHumanFunctionalChoicesForTheirYears() {
        val toyota = cars.brandId("Toyota")
        assertVariants(toyota, "RAV4", 2015, "Diésel", "Gasolina")
        assertVariants(toyota, "RAV4", 2018, "Diésel", "Gasolina", "Híbrido")
        assertVariants(toyota, "RAV4", 2021, "Híbrido", "Híbrido enchufable")
        assertVariants(toyota, "RAV4", 2025, "Híbrido", "Híbrido enchufable")

        assertVariants(toyota, "Yaris", 2010, "Diésel", "Gasolina")
        assertVariants(toyota, "Yaris", 2017, "Gasolina", "Híbrido")
        assertVariants(toyota, "Yaris", 2020, "Híbrido")
        assertVariants(toyota, "Yaris", 2024, "Híbrido")

        val seat = cars.brandId("SEAT")
        assertVariants(seat, "León", 2021, "Diésel", "Gasolina", "Híbrido enchufable")
        val leon2025 = cars.variantsFor(seat, cars.modelId(seat, "León"), 2025)
        assertEquals(listOf("Diésel", "Gasolina", "Híbrido enchufable"), leon2025.map { it.displayName })
        assertEquals("e-Hybrid 1.5", leon2025.single { it.displayName == "Híbrido enchufable" }.automaticDisplayName)
        assertVariants(seat, "León", 2026, "Diésel", "Gasolina", "Híbrido enchufable")

        val hyundai = cars.brandId("Hyundai")
        assertVariants(hyundai, "ix35", 2010, "Diésel", "Gasolina")
        assertVariants(hyundai, "ix35", 2012, "Diésel", "Gasolina")
        assertVariants(hyundai, "ix35", 2015, "Diésel", "Gasolina")
    }

    @Test
    fun additionalDenseFamiliesKeepTechnicalNamesOutOfTheSelector() {
        listOf(
            "Toyota" to "RAV4",
            "Toyota" to "Yaris",
            "SEAT" to "León",
            "Hyundai" to "ix35"
        ).forEach { (brandName, modelName) ->
            val brandId = cars.brandId(brandName)
            val modelId = cars.modelId(brandId, modelName)
            cars.yearsFor(brandId, modelId).forEach { year ->
                val labels = cars.variantsFor(brandId, modelId, year).map { it.displayName }
                assertTrue("$brandName $modelName $year", labels.none {
                    it.contains(Regex("(?i)\\b(xa20|xa30|xa40|xp10|xp90|xp130|xp210|1p|5f|kl|lm|facelift|my)\\b"))
                })
            }
        }
    }

    @Test
    fun transitionYearsUseHumanUniqueLabelsAndKeepTheirFunctionalSnapshots() {
        val opel = cars.brandId("Opel")
        val corsa = cars.modelId(opel, "Corsa")

        val expectedTanks = mapOf(
            2006 to mapOf(
                "Gasolina · modelo anterior" to 44.0,
                "Gasolina · modelo nuevo" to 45.0,
                "Diésel · modelo anterior" to 44.0,
                "Diésel · modelo nuevo" to 45.0
            ),
            2019 to mapOf(
                "Gasolina · modelo anterior" to 45.0,
                "Gasolina · modelo nuevo" to 44.0,
                "Diésel · modelo anterior" to 45.0,
                "Diésel · modelo nuevo" to 41.0
            )
        )

        expectedTanks.forEach { (year, tanksByLabel) ->
            val variants = cars.variantsFor(opel, corsa, year)
            assertEquals(variants.size, variants.map { it.displayName }.distinct().size)
            assertEquals(tanksByLabel, variants.associate { it.displayName to it.vehicle.fuelTankCapacity })
            assertTrue(variants.none { it.displayName.contains(Regex("(?i)\\b(corsa [cdef]|facelift|my|[a-z]\\d{2})\\b")) })
        }
    }

    @Test
    fun normalizedDenseFamiliesHaveNoDuplicateHumanVariantLabels() {
        listOf(
            "Opel" to "Corsa",
            "Citroën" to "Berlingo",
            "Nissan" to "Qashqai"
        ).forEach { (brandName, modelName) ->
            val brandId = cars.brandId(brandName)
            val modelId = cars.modelId(brandId, modelName)
            cars.yearsFor(brandId, modelId).forEach { year ->
                val labels = cars.variantsFor(brandId, modelId, year).map { it.displayName }
                assertEquals("$brandName $modelName $year", labels.size, labels.distinct().size)
            }
        }
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
    fun everyRuntimeVehicleProvidesAnExplicitStructuredBrandAndBaseModel() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())

        assertTrue(runtime.vehicles.all {
            it.brand.id.isNotBlank() &&
                it.brand.displayName.isNotBlank() &&
                it.model.id.isNotBlank() &&
                it.model.displayName.isNotBlank()
        })
    }

    @Test
    fun selectorUsesTheStructuredModelOfANewCatalogVehicleWithoutAndroidMappings() {
        val astra = RuntimeVehicle(
            catalogId = "car-opel-astra-1-2-turbo",
            category = VehicleCategory.COCHE,
            brand = RuntimeIdentity(id = "opel", displayName = "Opel"),
            model = RuntimeIdentity(id = "astra", displayName = "Astra"),
            generation = RuntimeIdentity(id = "l", displayName = "L"),
            variant = RuntimeIdentity(id = "1-2-turbo", displayName = "1.2 Turbo"),
            yearFrom = 2026,
            yearTo = 2026,
            powertrain = RuntimePowertrain(
                kind = RuntimePowertrainKind.ICE,
                primaryFuel = RuntimeFuel.GASOLINA,
                hybridSystem = RuntimeHybridSystem.NONE
            ),
            fuelTankLitres = 52.0,
            battery = RuntimeBattery(null, null, null, null),
            bodyStyle = null,
            drivetrain = null,
            marketCodes = listOf("ES"),
            legacyKeys = emptyList()
        )

        val catalog = RuntimeVehicleCatalog(
            schemaVersion = 1,
            catalogVersion = 1,
            generatedAt = "2026-10-06T00:00:00Z",
            vehicles = listOf(astra)
        ).selectionFor(VehicleCategory.COCHE)

        val opel = catalog.brands().single()
        val model = catalog.modelsFor(opel.id).single()
        assertEquals("Opel", opel.displayName)
        assertEquals("astra", model.id)
        assertEquals("Astra", model.displayName)
        assertEquals(listOf("Gasolina"), catalog.variantsFor(opel.id, model.id, 2026).map { it.displayName })
        assertEquals("1.2 Turbo", catalog.variantsFor(opel.id, model.id, 2026).single().automaticDisplayName)
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

    private fun assertVariants(brandId: String, modelName: String, year: Int, vararg expected: String) {
        val variants = cars.variantsFor(brandId, cars.modelId(brandId, modelName), year)
        assertEquals(expected.toList(), variants.map { it.displayName })
        assertEquals(variants.size, variants.map { it.displayName }.distinct().size)
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
