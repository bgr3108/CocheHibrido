package com.bgr3108.kilonom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleRuntimeCatalogTest {

    @Test
    fun bundledRuntimePreservesEveryLegacySelectionAndAddsReadyVehicles() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val activeLegacyKeys = runtime.vehicles.flatMap { it.legacyKeys }.map { it.key }.toSet()

        readLegacy("vehicles.json", VehicleCategory.COCHE).plus(readLegacy("motorcycles.json", VehicleCategory.MOTO)).forEach { legacy ->
            assertTrue("legacy entry missing: $legacy", activeLegacyKeys.contains(legacy.key))
        }
        assertEquals(201, runtime.vehicles.size)
        assertTrue(runtime.vehicles.any { it.catalogId == "car-seat-leon-kl-facelift-e-hybrid-1-5" })
        assertTrue(runtime.vehicles.any { it.catalogId == "car-bmw-x5-g05-lci-xdrive50e" })
    }

    @Test
    fun usableCapacityIsTheOnlyAutomaticCapacityForPercentageCalculations() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val seat = runtime.vehicles.single { it.catalogId == "car-seat-leon-kl-facelift-e-hybrid-1-5" }
        val golf = runtime.vehicles.single { it.catalogId == "car-volkswagen-golf-viii-gte" }
        val kuga = runtime.vehicles.single { it.catalogId == "car-ford-kuga-cx482-2-5-phev" }
        val peugeot = runtime.vehicles.single { it.catalogId == "car-peugeot-3008-p64-plug-in-hybrid-195" }
        val bmw = runtime.vehicles.single { it.catalogId == "car-bmw-x5-g05-lci-xdrive50e" }
        val rav4 = runtime.vehicles.single { it.catalogId == "car-toyota-rav4-xa50-plug-in-hybrid" }

        assertEquals(19.7, seat.toVehicleInfo(2025).batteryCapacity, 0.0)
        assertEquals(13.0, golf.battery.declaredKwh!!, 0.0)
        assertEquals(0.0, golf.toVehicleInfo(2024).batteryCapacity, 0.0)
        assertEquals(14.4, kuga.battery.grossKwh!!, 0.0)
        assertEquals(0.0, kuga.toVehicleInfo(2025).batteryCapacity, 0.0)
        assertEquals(17.8, peugeot.toVehicleInfo(2025).batteryCapacity, 0.0)
        assertEquals(25.7, bmw.toVehicleInfo(2023).batteryCapacity, 0.0)
        assertEquals(0.0, rav4.toVehicleInfo(2022).batteryCapacity, 0.0)
    }

    @Test
    fun runtimeMapsEverySupportedPowertrainWithoutFallback() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        assertTrue(runtime.vehicles.any { it.powertrain.kind == RuntimePowertrainKind.ICE && it.toVehicleInfo(it.yearFrom).type == VehicleType.GASOLINA })
        assertTrue(runtime.vehicles.any { it.powertrain.kind == RuntimePowertrainKind.ICE && it.toVehicleInfo(it.yearFrom).type == VehicleType.DIESEL })
        assertTrue(runtime.vehicles.any { it.powertrain.kind == RuntimePowertrainKind.HEV && it.toVehicleInfo(it.yearFrom).type == VehicleType.HIBRIDO })
        assertTrue(runtime.vehicles.any { it.powertrain.kind == RuntimePowertrainKind.PHEV && it.toVehicleInfo(it.yearFrom).type == VehicleType.HIBRIDO_ENCHUFABLE })
        assertTrue(runtime.vehicles.any { it.powertrain.kind == RuntimePowertrainKind.BEV && it.toVehicleInfo(it.yearFrom).type == VehicleType.ELECTRICO })
        assertTrue(runtime.vehicles.any { it.category == VehicleCategory.MOTO && it.powertrain.kind == RuntimePowertrainKind.BEV })
    }

    @Test
    fun runtimeBuildsStableCategoryBrandModelAndIdIndexes() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())

        assertTrue(runtime.brandsFor(VehicleCategory.COCHE).contains("SEAT"))
        assertTrue(runtime.modelsFor(VehicleCategory.COCHE, "seat").contains("León"))
        assertEquals(
            "BMW",
            runtime.findByCatalogId("car-bmw-x5-g05-lci-xdrive50e")?.brand?.displayName
        )
    }

    @Test
    fun parserRejectsUnknownEnumsAndInvalidBatteryRules() {
        assertThrows(IllegalStateException::class.java) {
            parseRuntimeVehicleCatalog(runtimeWith("\"kind\":\"BEV\"", "\"kind\":\"UNKNOWN\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseRuntimeVehicleCatalog(runtimeWith("\"fuelTankLitres\":null", "\"fuelTankLitres\":45"))
        }
    }

    @Test
    fun openEndedCarYearsAndConsolidatedMotorcycleYearsRemainSelectable() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val seat = runtime.vehicles.single { it.catalogId == "car-seat-leon-kl-facelift-e-hybrid-1-5" }
        assertTrue(2026 in seat.years(runtime.selectionYearUpperBound))
        val mt07 = runtime.vehicles.single { vehicle ->
            vehicle.category == VehicleCategory.MOTO && vehicle.legacyKeys.any { it.brand == "Yamaha" && it.model == "MT-07" && it.year == 2022 }
        }
        assertTrue(2022 in mt07.years(runtime.selectionYearUpperBound))
        assertEquals(mt07.years(runtime.selectionYearUpperBound).count(), mt07.legacyKeys.size)
    }

    @Test
    fun consolidatedMotorcyclesRemainEquivalentToEveryLegacySnapshot() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val legacyMotorcycles = readLegacyMotorcycles()
        val activeMotorcycles = runtime.vehicles.filter { it.category == VehicleCategory.MOTO }

        assertEquals(404, legacyMotorcycles.size)
        assertEquals(68, activeMotorcycles.size)
        assertTrue(activeMotorcycles.all { vehicle ->
            vehicle.years(runtime.selectionYearUpperBound).count() == vehicle.legacyKeys.size
        })

        legacyMotorcycles.forEach { legacy ->
            val vehicle = activeMotorcycles.single { candidate ->
                candidate.legacyKeys.any { key ->
                    key.brand == legacy.brand && key.model == legacy.model && key.year == legacy.year
                }
            }
            val snapshot = vehicle.toVehicleInfo(legacy.year)

            assertTrue("year missing: $legacy", legacy.year in vehicle.years(runtime.selectionYearUpperBound))
            assertEquals(legacy.type, snapshot.type)
            assertEquals(legacy.fuelTankCapacity ?: 0.0, snapshot.fuelTankCapacity, 0.0)
            assertEquals(legacy.batteryCapacity ?: 0.0, vehicle.battery.declaredKwh ?: 0.0, 0.0)
            assertEquals(0.0, vehicle.battery.usableKwh ?: 0.0, 0.0)
            assertEquals(0.0, snapshot.batteryCapacity, 0.0)
        }
    }

    @Test
    fun normalizedCorsaBerlingoAndQashqaiPreserveEveryLegacySnapshot() {
        val runtime = parseRuntimeVehicleCatalog(readRuntime())
        val normalizedFamilies = setOf(
            "Opel" to "Corsa",
            "Citroën" to "Berlingo",
            "Nissan" to "Qashqai"
        )
        val legacyCars = readLegacyCars().filter { legacy ->
            normalizedFamilies.any { (brand, model) ->
                legacy.brand == brand && legacy.model.startsWith(model)
            }
        }

        assertEquals(60, legacyCars.size)
        legacyCars.forEach { legacy ->
            val vehicle = runtime.vehicles.single { candidate ->
                candidate.legacyKeys.any { key ->
                    key.brand == legacy.brand && key.model == legacy.model && key.year == legacy.year
                }
            }
            val snapshot = vehicle.toVehicleInfo(legacy.year)

            assertTrue("year missing: $legacy", legacy.year in vehicle.years(runtime.selectionYearUpperBound))
            assertEquals(legacy.type, snapshot.type)
            assertEquals(legacy.fuelTankCapacity ?: 0.0, snapshot.fuelTankCapacity, 0.0)
            assertEquals(legacy.batteryCapacity ?: 0.0, vehicle.battery.declaredKwh ?: 0.0, 0.0)
            assertEquals(0.0, snapshot.batteryCapacity, 0.0)
        }
    }

    private fun runtimeWith(from: String, to: String): String = readRuntime().replace(from, to)

    private fun readRuntime(): String = findAsset(RUNTIME_CATALOG_ASSET).readText()

    private fun readLegacy(fileName: String, category: VehicleCategory): List<LegacyKey> {
        val json = findAsset(fileName).readText()
        return Regex("""\{\s*"brand"\s*:\s*"([^"]+)",\s*"model"\s*:\s*"([^"]+)",\s*"year"\s*:\s*(\d+)""")
            .findAll(json)
            .map { match -> LegacyKey(category.name, match.groupValues[1], match.groupValues[2], match.groupValues[3].toInt()) }
            .toList()
    }

    private fun readLegacyMotorcycles(): List<LegacyMotorcycleSnapshot> =
        Json.parseToJsonElement(findAsset("motorcycles.json").readText()).jsonArray.map { element ->
            val entry = element.jsonObject
            LegacyMotorcycleSnapshot(
                brand = entry.getValue("brand").jsonPrimitive.content,
                model = entry.getValue("model").jsonPrimitive.content,
                year = entry.getValue("year").jsonPrimitive.content.toInt(),
                type = VehicleType.valueOf(entry.getValue("type").jsonPrimitive.content),
                fuelTankCapacity = entry["fuelTankCapacity"]?.jsonPrimitive?.doubleOrNull,
                batteryCapacity = entry["batteryCapacity"]?.jsonPrimitive?.doubleOrNull
            )
        }

    private fun readLegacyCars(): List<LegacyMotorcycleSnapshot> =
        Json.parseToJsonElement(findAsset("vehicles.json").readText()).jsonArray.map { element ->
            val entry = element.jsonObject
            LegacyMotorcycleSnapshot(
                brand = entry.getValue("brand").jsonPrimitive.content,
                model = entry.getValue("model").jsonPrimitive.content,
                year = entry.getValue("year").jsonPrimitive.content.toInt(),
                type = VehicleType.valueOf(entry.getValue("type").jsonPrimitive.content),
                fuelTankCapacity = entry["fuelTankCapacity"]?.jsonPrimitive?.doubleOrNull,
                batteryCapacity = entry["batteryCapacity"]?.jsonPrimitive?.doubleOrNull
            )
        }

    private data class LegacyKey(val category: String, val brand: String, val model: String, val year: Int) {
        val key = "$category|$brand|$model|$year"
    }

    private data class LegacyMotorcycleSnapshot(
        val brand: String,
        val model: String,
        val year: Int,
        val type: VehicleType,
        val fuelTankCapacity: Double?,
        val batteryCapacity: Double?
    )

    private val RuntimeLegacyKey.key: String
        get() = "$category|$brand|$model|$year"

    private fun findAsset(name: String): File = sequenceOf(
        File("app/src/main/assets", name),
        File("src/main/assets", name),
        File("../app/src/main/assets", name)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $name")
}
