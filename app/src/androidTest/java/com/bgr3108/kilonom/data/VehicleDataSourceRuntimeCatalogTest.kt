package com.bgr3108.kilonom.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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

    @Test
    fun validLocalInstallationIsUsedByANewProductionDataSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogFile = File(context.filesDir, INSTALLED_RUNTIME_CATALOG_PATH)
        val relatedFiles = listOf(catalogFile, File("${catalogFile.path}.bak"), File("${catalogFile.path}.new"))
        val previousFiles = relatedFiles.associateWith { file -> file.takeIf(File::isFile)?.readBytes() }
        try {
            relatedFiles.forEach(File::delete)
            val seed = context.assets.open(RUNTIME_CATALOG_ASSET).use { it.readBytes() }
            val candidateVersion = parseRuntimeVehicleCatalog(seed.toString(Charsets.UTF_8)).catalogVersion + 1
            val candidate = catalogWithVersion(seed, candidateVersion)
            val manifest = """{"schemaVersion":1,"catalogVersion":$candidateVersion,"generatedAt":"2026-10-11T00:00:00Z","sha256":"${catalogSha256(candidate)}","file":"catalog-runtime.json"}"""

            assertEquals(
                VehicleCatalogInstallResult.Installed(candidateVersion),
                VehicleCatalogInstaller(context).install(manifest, candidate)
            )
            assertTrue(
                VehicleDataSource(context).loadVehicles(VehicleCategory.COCHE)
                    .any { it.brand == "Installed BMW" }
            )
        } finally {
            relatedFiles.forEach { file ->
                file.delete()
                previousFiles.getValue(file)?.let { bytes ->
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
            }
        }
    }

    private fun catalogWithVersion(seed: ByteArray, version: Int): ByteArray {
        val root = Json.parseToJsonElement(seed.toString(Charsets.UTF_8)).jsonObject
        val vehicles = root.getValue("vehicles").jsonArray.map { element ->
            val vehicle = element.jsonObject
            val brand = vehicle.getValue("brand").jsonObject
            if (brand.getValue("displayName").jsonPrimitive.content == "BMW") {
                JsonObject(vehicle + ("brand" to JsonObject(brand + ("displayName" to JsonPrimitive("Installed BMW")))))
            } else {
                vehicle
            }
        }
        return Json.encodeToString(
            JsonObject(root + ("catalogVersion" to JsonPrimitive(version)) + ("vehicles" to JsonArray(vehicles)))
        ).toByteArray()
    }
}
