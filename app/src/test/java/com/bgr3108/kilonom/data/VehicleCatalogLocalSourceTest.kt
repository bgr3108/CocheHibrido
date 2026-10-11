package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class VehicleCatalogLocalSourceTest {

    @Test
    fun absentInstalledCatalog_usesBundledSeed() {
        val source = VehicleDataSource(FakeCatalogSource(installed = null, seed = bundledSeed()))

        assertTrue(source.loadVehicles(VehicleCategory.COCHE).any { it.brand == "SEAT" })
    }

    @Test
    fun validInstalledCatalog_takesPriorityOverBundledSeed() {
        val installed = bundledSeed().replace("\"displayName\":\"BMW\"", "\"displayName\":\"Installed BMW\"")
        val source = VehicleDataSource(FakeCatalogSource(installed = installed, seed = bundledSeed()))

        assertTrue(source.loadVehicles(VehicleCategory.COCHE).any { it.brand == "Installed BMW" })
        assertTrue(source.loadVehicles(VehicleCategory.COCHE).none { it.brand == "BMW" })
    }

    @Test
    fun invalidInstalledCatalog_fallsBackToBundledSeed() {
        assertUsesSeed(FakeCatalogSource(installed = "{ not json", seed = bundledSeed()))
    }

    @Test
    fun incompatibleInstalledCatalog_fallsBackToBundledSeed() {
        val incompatible = bundledSeed().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")

        assertUsesSeed(FakeCatalogSource(installed = incompatible, seed = bundledSeed()))
    }

    @Test
    fun runtimeInvalidInstalledCatalog_fallsBackToBundledSeed() {
        val invalidRuntime = bundledSeed().replaceFirst("\"fuelTankLitres\":null", "\"fuelTankLitres\":45")

        assertUsesSeed(FakeCatalogSource(installed = invalidRuntime, seed = bundledSeed()))
    }

    @Test
    fun emptyInstalledCatalog_fallsBackToBundledSeed() {
        assertUsesSeed(FakeCatalogSource(installed = "", seed = bundledSeed()))
    }

    @Test
    fun installedReadFailure_fallsBackToBundledSeed() {
        assertUsesSeed(FailingInstalledCatalogSource(bundledSeed()))
    }

    @Test
    fun bundledSeed_remainsAvailableWhenNoInstalledCatalogExists() {
        val source = VehicleDataSource(FakeCatalogSource(installed = null, seed = bundledSeed()))

        assertEquals(228, parseRuntimeVehicleCatalog(bundledSeed()).vehicles.size)
        assertTrue(source.loadVehicles(VehicleCategory.MOTO).isNotEmpty())
    }

    private fun assertUsesSeed(catalogSource: VehicleCatalogLocalSource) {
        val source = VehicleDataSource(catalogSource)
        assertTrue(source.loadVehicles(VehicleCategory.COCHE).any { it.brand == "SEAT" })
    }

    private fun bundledSeed(): String = findRuntimeAsset().readText()

    private fun findRuntimeAsset(): File = sequenceOf(
        File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
        File("src/main/assets", RUNTIME_CATALOG_ASSET),
        File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $RUNTIME_CATALOG_ASSET")

    private class FakeCatalogSource(
        private val installed: String?,
        private val seed: String
    ) : VehicleCatalogLocalSource {
        override fun readInstalledCatalog(): String? = installed
        override fun readBundledSeed(): String = seed
    }

    private class FailingInstalledCatalogSource(
        private val seed: String
    ) : VehicleCatalogLocalSource {
        override fun readInstalledCatalog(): Nothing = throw IOException("test read failure")
        override fun readBundledSeed(): String = seed
    }
}
