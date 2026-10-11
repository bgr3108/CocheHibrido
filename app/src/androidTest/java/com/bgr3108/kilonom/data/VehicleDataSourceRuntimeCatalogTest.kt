package com.bgr3108.kilonom.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            val candidate = seed.toString(Charsets.UTF_8)
                .replaceFirst("\"catalogVersion\":1", "\"catalogVersion\":2")
                .replace("\"displayName\":\"BMW\"", "\"displayName\":\"Installed BMW\"")
                .toByteArray()
            val manifest = """{"schemaVersion":1,"catalogVersion":2,"generatedAt":"2026-10-11T00:00:00Z","sha256":"${catalogSha256(candidate)}","file":"catalog-runtime.json"}"""

            assertEquals(
                VehicleCatalogInstallResult.Installed(2),
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
}
