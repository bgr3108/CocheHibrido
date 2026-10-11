package com.bgr3108.kilonom.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VehicleCatalogPublishArtifactsTest {

    @Test
    fun generatedPublishArtifacts_matchRuntimeSeedAndManifestContract() {
        val runtimeBytes = findRepositoryFile("catalog/runtime/catalog-runtime.json").readBytes()
        val seedBytes = findRepositoryFile("app/src/main/assets/$RUNTIME_CATALOG_ASSET").readBytes()
        val publishBytes = findRepositoryFile("catalog/publish/catalog-runtime.json").readBytes()
        val manifest = parseVehicleCatalogManifest(
            findRepositoryFile("catalog/publish/manifest.json").readText()
        )
        val runtime = parseRuntimeVehicleCatalog(publishBytes.toString(Charsets.UTF_8))

        assertArrayEquals(runtimeBytes, seedBytes)
        assertArrayEquals(runtimeBytes, publishBytes)
        assertEquals(VEHICLE_CATALOG_MANIFEST_SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(runtime.catalogVersion, manifest.catalogVersion)
        assertEquals(runtime.generatedAt, manifest.generatedAt)
        assertEquals(catalogSha256(publishBytes), manifest.sha256)
        assertEquals(INSTALLED_RUNTIME_CATALOG_FILE_NAME, manifest.file)
    }

    private fun findRepositoryFile(path: String): File = sequenceOf(
        File(path),
        File("../$path"),
        File("../../$path")
    ).firstOrNull(File::isFile) ?: error("Repository file not found: $path")
}
