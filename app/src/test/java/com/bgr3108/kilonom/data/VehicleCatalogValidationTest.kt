package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleCatalogValidationTest {

    @Test
    fun catalogsAreValidJsonAndContainOnlySupportedVehicleTypes() {
        catalogNames.forEach { catalogName ->
            readCatalog(catalogName).forEach { entry ->
                VehicleType.valueOf(entry.type)
            }
        }
    }

    @Test
    fun catalogsDoNotContainExactBrandModelYearDuplicates() {
        catalogNames.forEach { catalogName ->
            val keys = readCatalog(catalogName).map { entry ->
                listOf(entry.brand, entry.model, entry.year)
            }

            assertEquals(catalogName, keys.size, keys.toSet().size)
        }
    }

    @Test
    fun catalogCapacitiesFollowTheRulesForEachVehicleType() {
        catalogNames.forEach { catalogName ->
            readCatalog(catalogName).forEach { entry ->
                val type = VehicleType.valueOf(entry.type)
                val hasFuelTank = entry.fuelTankCapacity != null
                val hasBattery = entry.batteryCapacity != null

                entry.fuelTankCapacity?.let { assertTrue(it >= 0.0) }
                entry.batteryCapacity?.let { assertTrue(it >= 0.0) }

                when (type) {
                    VehicleType.ELECTRICO -> {
                        assertFalse(hasFuelTank)
                        assertTrue(hasBattery)
                        assertTrue(requireNotNull(entry.batteryCapacity) > 0.0)
                    }

                    VehicleType.HIBRIDO_ENCHUFABLE -> {
                        assertTrue(hasFuelTank)
                        assertTrue(hasBattery)
                        assertTrue(requireNotNull(entry.fuelTankCapacity) > 0.0)
                        assertTrue(requireNotNull(entry.batteryCapacity) > 0.0)
                    }

                    VehicleType.GASOLINA,
                    VehicleType.DIESEL,
                    VehicleType.HIBRIDO -> {
                        assertTrue(hasFuelTank)
                        assertFalse(hasBattery)
                        assertTrue(requireNotNull(entry.fuelTankCapacity) > 0.0)
                    }
                }
            }
        }
    }

    @Test
    fun confirmedCarCatalogCorrectionsArePreserved() {
        val vehicles = readCatalog("vehicles.json")

        val kuga = vehicles.first { entry ->
            entry.brand == "Ford" && entry.model == "Kuga PHEV" && entry.year == 2024
        }
        val glc = vehicles.first { entry ->
            entry.brand == "Mercedes-Benz" && entry.model == "GLC 300 de" && entry.year == 2025
        }

        assertEquals(10.3, requireNotNull(kuga.batteryCapacity), 0.0)
        assertEquals(45.0, requireNotNull(kuga.fuelTankCapacity), 0.0)
        assertEquals(25.28, requireNotNull(glc.batteryCapacity), 0.0)
    }

    @Test
    fun confirmedMotorcycleBatteryCorrectionsArePreserved() {
        val motorcycles = readCatalog("motorcycles.json")

        assertBatteryCapacity(motorcycles, "BMW", "CE 04", listOf(2022, 2023, 2024, 2025), 8.5)
        assertBatteryCapacity(motorcycles, "Zero", "SR/F", listOf(2020, 2021), 12.6)
        assertBatteryCapacity(motorcycles, "Zero", "SR/F", listOf(2024, 2025), 15.1)
    }

    private fun assertBatteryCapacity(
        entries: List<CatalogEntry>,
        brand: String,
        model: String,
        years: List<Int>,
        expectedCapacity: Double
    ) {
        years.forEach { year ->
            val entry = entries.first { it.brand == brand && it.model == model && it.year == year }
            assertEquals(expectedCapacity, requireNotNull(entry.batteryCapacity), 0.0)
        }
    }

    private fun readCatalog(catalogName: String): List<CatalogEntry> {
        val file = sequenceOf(
            File("src/main/assets", catalogName),
            File("app/src/main/assets", catalogName),
            File("../app/src/main/assets", catalogName)
        ).firstOrNull(File::isFile)
            ?: error("Catalog file not found: $catalogName")

        val json = file.readText().trim()
        assertTrue("$catalogName debe ser un array JSON", json.startsWith("[") && json.endsWith("]"))

        val arrayContent = json.removePrefix("[").removeSuffix("]")
        val matches = catalogEntryPattern.findAll(arrayContent).toList()
        val remainingContent = catalogEntryPattern
            .replace(arrayContent, "")
            .replace(",", "")
            .trim()

        assertTrue("$catalogName contiene JSON no reconocido o malformado", remainingContent.isEmpty())
        return matches.map { match ->
            CatalogEntry(
                brand = match.groupValues[1],
                model = match.groupValues[2],
                year = match.groupValues[3].toInt(),
                type = match.groupValues[4],
                batteryCapacity = match.groupValues[5].toNullableDouble(),
                fuelTankCapacity = match.groupValues[6].toNullableDouble()
            )
        }
    }

    private fun String.toNullableDouble(): Double? = takeUnless { it == "null" }?.toDouble()

    private data class CatalogEntry(
        val brand: String,
        val model: String,
        val year: Int,
        val type: String,
        val batteryCapacity: Double?,
        val fuelTankCapacity: Double?
    )

    private companion object {
        val catalogNames = listOf("vehicles.json", "motorcycles.json")
        val catalogEntryPattern = Regex(
            """\{\s*"brand"\s*:\s*"([^"]+)"\s*,\s*"model"\s*:\s*"([^"]+)"\s*,\s*"year"\s*:\s*(\d+)\s*,\s*"type"\s*:\s*"([A-Z_]+)"\s*,\s*"batteryCapacity"\s*:\s*(null|-?\d+(?:\.\d+)?)\s*,\s*"fuelTankCapacity"\s*:\s*(null|-?\d+(?:\.\d+)?)\s*\}"""
        )
    }
}
