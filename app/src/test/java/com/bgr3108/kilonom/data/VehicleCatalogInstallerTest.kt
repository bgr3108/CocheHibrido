package com.bgr3108.kilonom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class VehicleCatalogInstallerTest {

    @Test
    fun manifestParser_acceptsTheMinimalRuntimeContract() {
        val bytes = bundledSeed().toByteArray()
        val baseVersion = seedCatalogVersion()

        val manifest = parseVehicleCatalogManifest(manifestFor(bytes, baseVersion).uppercaseSha())

        assertEquals(1, manifest.schemaVersion)
        assertEquals(baseVersion, manifest.catalogVersion)
        assertEquals(catalogSha256(bytes), manifest.sha256)
        assertEquals(INSTALLED_RUNTIME_CATALOG_FILE_NAME, manifest.file)
    }

    @Test
    fun manifestParser_rejectsMalformedMissingAndUnsafeContracts() {
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest("{") }
        assertThrows(Exception::class.java) {
            parseVehicleCatalogManifest("""{"schemaVersion":1,"catalogVersion":1,"generatedAt":"2026-10-11T00:00:00Z","sha256":"${"a".repeat(64)}"}""")
        }
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest(manifestFor(bundledSeed().toByteArray(), 1, schemaVersion = 2)) }
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest(manifestFor(bundledSeed().toByteArray(), 0)) }
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest(manifestFor(bundledSeed().toByteArray(), 1, sha256 = "invalid")) }
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest(manifestFor(bundledSeed().toByteArray(), 1, file = "")) }
        assertThrows(Exception::class.java) { parseVehicleCatalogManifest(manifestFor(bundledSeed().toByteArray(), 1, file = "../catalog-runtime.json")) }
    }

    @Test
    fun validNewerCandidate_installsAfterEveryValidation() {
        val candidateVersion = seedCatalogVersion() + 1
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(candidateVersion), result)
        assertArrayEquals(candidate, storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun checksumMismatch_isRejectedBeforeWriting() {
        val candidateVersion = seedCatalogVersion() + 1
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion, sha256 = "0".repeat(64)), candidate)

        assertEquals(VehicleCatalogInstallResult.ChecksumMismatch, result)
        assertNull(storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun invalidOrIncompatibleRuntime_isRejectedBeforeWriting() {
        val candidateVersion = seedCatalogVersion() + 1
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val malformed = "{ not catalog".toByteArray()
        val incompatible = catalogWithVersion(candidateVersion, "Installed BMW").toString(Charsets.UTF_8)
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
            .toByteArray()

        assertEquals(
            VehicleCatalogInstallResult.InvalidCatalog,
            VehicleCatalogInstaller(storage).install(manifestFor(malformed, candidateVersion), malformed)
        )
        assertEquals(
            VehicleCatalogInstallResult.InvalidCatalog,
            VehicleCatalogInstaller(storage).install(manifestFor(incompatible, candidateVersion), incompatible)
        )
        assertNull(storage.installed)
    }

    @Test
    fun manifestRuntimeVersionMismatch_isRejected() {
        val candidateVersion = seedCatalogVersion() + 1
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion + 1), candidate)

        assertEquals(VehicleCatalogInstallResult.VersionMismatch, result)
        assertNull(storage.installed)
    }

    @Test
    fun sameAndOlderVersions_doNotReplaceTheActiveCatalog() {
        val baseVersion = seedCatalogVersion()
        val installedVersion = baseVersion + 1
        val installed = catalogWithVersion(installedVersion, "Installed BMW")
        val storage = FakeInstallStorage(installed = installed, seed = bundledSeedBytes())
        val seed = bundledSeedBytes()

        assertEquals(
            VehicleCatalogInstallResult.NoUpdate(installedVersion),
            VehicleCatalogInstaller(storage).install(manifestFor(installed, installedVersion), installed)
        )
        assertEquals(
            VehicleCatalogInstallResult.NoUpdate(installedVersion),
            VehicleCatalogInstaller(storage).install(manifestFor(seed, baseVersion), seed)
        )
        assertArrayEquals(installed, storage.installed)
    }

    @Test
    fun validNewerCandidate_replacesThePreviousValidCatalog() {
        val previousVersion = seedCatalogVersion() + 1
        val candidateVersion = previousVersion + 1
        val previous = catalogWithVersion(previousVersion, "Previous BMW")
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(candidateVersion), result)
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun corruptPreviousInstalledCatalog_usesSeedAsVersionBaseline() {
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")
        val storage = FakeInstallStorage(installed = "not a catalog".toByteArray(), seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(candidateVersion), result)
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun failedCandidateInstall_keepsThePreviousCatalogByteForByte() {
        val previousVersion = seedCatalogVersion() + 1
        val previous = catalogWithVersion(previousVersion, "Previous BMW")
        val corruptCandidate = "{ broken".toByteArray()
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(corruptCandidate, previousVersion + 1), corruptCandidate)

        assertEquals(VehicleCatalogInstallResult.InvalidCatalog, result)
        assertArrayEquals(previous, storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun writeAndPromotionFailures_keepThePreviousCatalogAndCleanTemporaryData() {
        val previousVersion = seedCatalogVersion() + 1
        val candidateVersion = previousVersion + 1
        val previous = catalogWithVersion(previousVersion, "Previous BMW")
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")

        val writeFailure = FakeInstallStorage(installed = previous, seed = bundledSeedBytes(), failWrite = true)
        assertEquals(
            VehicleCatalogInstallResult.IoError,
            VehicleCatalogInstaller(writeFailure).install(manifestFor(candidate, candidateVersion), candidate)
        )
        assertArrayEquals(previous, writeFailure.installed)
        assertNull(writeFailure.temporary)

        val promotionFailure = FakeInstallStorage(installed = previous, seed = bundledSeedBytes(), failPromotion = true)
        assertEquals(
            VehicleCatalogInstallResult.IoError,
            VehicleCatalogInstaller(promotionFailure).install(manifestFor(candidate, candidateVersion), candidate)
        )
        assertArrayEquals(previous, promotionFailure.installed)
        assertNull(promotionFailure.temporary)
    }

    @Test
    fun installedCatalog_isUsedByANewDataSourceInstance() {
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion, "Installed BMW")
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        assertEquals(
            VehicleCatalogInstallResult.Installed(candidateVersion),
            VehicleCatalogInstaller(storage).install(manifestFor(candidate, candidateVersion), candidate)
        )
        val source = VehicleDataSource(object : VehicleCatalogLocalSource {
            override fun readInstalledCatalog(): String? = storage.installed?.toString(Charsets.UTF_8)
            override fun readBundledSeed(): String = storage.seed.toString(Charsets.UTF_8)
        })

        assertTrue(source.loadVehicles(VehicleCategory.COCHE).any { it.brand == "Installed BMW" })
    }

    private fun catalogWithVersion(version: Int, bmwDisplayName: String): ByteArray {
        val root = Json.parseToJsonElement(bundledSeed()).jsonObject
        val vehicles = root.getValue("vehicles").jsonArray.map { element ->
            val vehicle = element.jsonObject
            val brand = vehicle.getValue("brand").jsonObject
            if (brand.getValue("displayName").jsonPrimitive.content == "BMW") {
                JsonObject(vehicle + ("brand" to JsonObject(brand + ("displayName" to JsonPrimitive(bmwDisplayName)))))
            } else {
                vehicle
            }
        }
        return Json.encodeToString(
            JsonObject(root + ("catalogVersion" to JsonPrimitive(version)) + ("vehicles" to JsonArray(vehicles)))
        ).toByteArray()
    }

    private fun seedCatalogVersion(): Int = parseRuntimeVehicleCatalog(bundledSeed()).catalogVersion

    private fun manifestFor(
        bytes: ByteArray,
        catalogVersion: Int,
        schemaVersion: Int = 1,
        sha256: String = catalogSha256(bytes),
        file: String = INSTALLED_RUNTIME_CATALOG_FILE_NAME
    ): String = """{"schemaVersion":$schemaVersion,"catalogVersion":$catalogVersion,"generatedAt":"2026-10-11T00:00:00Z","sha256":"$sha256","file":"$file"}"""

    private fun String.uppercaseSha(): String = replace(
        Regex("\"sha256\":\"([a-f0-9]+)\"")
    ) { match -> "\"sha256\":\"${match.groupValues[1].uppercase()}\"" }

    private fun bundledSeed(): String = findRuntimeAsset().readText()

    private fun bundledSeedBytes(): ByteArray = bundledSeed().toByteArray()

    private fun findRuntimeAsset(): File = sequenceOf(
        File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
        File("src/main/assets", RUNTIME_CATALOG_ASSET),
        File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $RUNTIME_CATALOG_ASSET")

    private class FakeInstallStorage(
        var installed: ByteArray? = null,
        val seed: ByteArray,
        private val failWrite: Boolean = false,
        private val failPromotion: Boolean = false
    ) : VehicleCatalogInstallStorage {
        var temporary: ByteArray? = null

        override fun readInstalledCatalogBytes(): ByteArray? = installed
        override fun readBundledSeedBytes(): ByteArray = seed

        override fun writeTemporary(bytes: ByteArray) {
            if (failWrite) throw IOException("test write failure")
            temporary = bytes.copyOf()
        }

        override fun promoteTemporary() {
            if (failPromotion) throw IOException("test promotion failure")
            installed = requireNotNull(temporary).copyOf()
            temporary = null
        }

        override fun discardTemporary() {
            temporary = null
        }
    }
}
