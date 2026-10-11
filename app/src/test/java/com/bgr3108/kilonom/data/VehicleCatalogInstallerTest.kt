package com.bgr3108.kilonom.data

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

        val manifest = parseVehicleCatalogManifest(manifestFor(bytes, 1).uppercaseSha())

        assertEquals(1, manifest.schemaVersion)
        assertEquals(1, manifest.catalogVersion)
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
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(2, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, 2), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(2), result)
        assertArrayEquals(candidate, storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun checksumMismatch_isRejectedBeforeWriting() {
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(2, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, 2, sha256 = "0".repeat(64)), candidate)

        assertEquals(VehicleCatalogInstallResult.ChecksumMismatch, result)
        assertNull(storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun invalidOrIncompatibleRuntime_isRejectedBeforeWriting() {
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val malformed = "{ not catalog".toByteArray()
        val incompatible = catalogWithVersion(2, "Installed BMW").toString(Charsets.UTF_8)
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
            .toByteArray()

        assertEquals(
            VehicleCatalogInstallResult.InvalidCatalog,
            VehicleCatalogInstaller(storage).install(manifestFor(malformed, 2), malformed)
        )
        assertEquals(
            VehicleCatalogInstallResult.InvalidCatalog,
            VehicleCatalogInstaller(storage).install(manifestFor(incompatible, 2), incompatible)
        )
        assertNull(storage.installed)
    }

    @Test
    fun manifestRuntimeVersionMismatch_isRejected() {
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        val candidate = catalogWithVersion(2, "Installed BMW")

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, 3), candidate)

        assertEquals(VehicleCatalogInstallResult.VersionMismatch, result)
        assertNull(storage.installed)
    }

    @Test
    fun sameAndOlderVersions_doNotReplaceTheActiveCatalog() {
        val installed = catalogWithVersion(2, "Installed BMW")
        val storage = FakeInstallStorage(installed = installed, seed = bundledSeedBytes())
        val seed = bundledSeedBytes()

        assertEquals(
            VehicleCatalogInstallResult.NoUpdate(2),
            VehicleCatalogInstaller(storage).install(manifestFor(installed, 2), installed)
        )
        assertEquals(
            VehicleCatalogInstallResult.NoUpdate(2),
            VehicleCatalogInstaller(storage).install(manifestFor(seed, 1), seed)
        )
        assertArrayEquals(installed, storage.installed)
    }

    @Test
    fun validNewerCandidate_replacesThePreviousValidCatalog() {
        val previous = catalogWithVersion(2, "Previous BMW")
        val candidate = catalogWithVersion(3, "Installed BMW")
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, 3), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(3), result)
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun corruptPreviousInstalledCatalog_usesSeedAsVersionBaseline() {
        val candidate = catalogWithVersion(2, "Installed BMW")
        val storage = FakeInstallStorage(installed = "not a catalog".toByteArray(), seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(candidate, 2), candidate)

        assertEquals(VehicleCatalogInstallResult.Installed(2), result)
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun failedCandidateInstall_keepsThePreviousCatalogByteForByte() {
        val previous = catalogWithVersion(2, "Previous BMW")
        val corruptCandidate = "{ broken".toByteArray()
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())

        val result = VehicleCatalogInstaller(storage).install(manifestFor(corruptCandidate, 3), corruptCandidate)

        assertEquals(VehicleCatalogInstallResult.InvalidCatalog, result)
        assertArrayEquals(previous, storage.installed)
        assertNull(storage.temporary)
    }

    @Test
    fun writeAndPromotionFailures_keepThePreviousCatalogAndCleanTemporaryData() {
        val previous = catalogWithVersion(2, "Previous BMW")
        val candidate = catalogWithVersion(3, "Installed BMW")

        val writeFailure = FakeInstallStorage(installed = previous, seed = bundledSeedBytes(), failWrite = true)
        assertEquals(
            VehicleCatalogInstallResult.IoError,
            VehicleCatalogInstaller(writeFailure).install(manifestFor(candidate, 3), candidate)
        )
        assertArrayEquals(previous, writeFailure.installed)
        assertNull(writeFailure.temporary)

        val promotionFailure = FakeInstallStorage(installed = previous, seed = bundledSeedBytes(), failPromotion = true)
        assertEquals(
            VehicleCatalogInstallResult.IoError,
            VehicleCatalogInstaller(promotionFailure).install(manifestFor(candidate, 3), candidate)
        )
        assertArrayEquals(previous, promotionFailure.installed)
        assertNull(promotionFailure.temporary)
    }

    @Test
    fun installedCatalog_isUsedByANewDataSourceInstance() {
        val candidate = catalogWithVersion(2, "Installed BMW")
        val storage = FakeInstallStorage(seed = bundledSeedBytes())
        assertEquals(
            VehicleCatalogInstallResult.Installed(2),
            VehicleCatalogInstaller(storage).install(manifestFor(candidate, 2), candidate)
        )
        val source = VehicleDataSource(object : VehicleCatalogLocalSource {
            override fun readInstalledCatalog(): String? = storage.installed?.toString(Charsets.UTF_8)
            override fun readBundledSeed(): String = storage.seed.toString(Charsets.UTF_8)
        })

        assertTrue(source.loadVehicles(VehicleCategory.COCHE).any { it.brand == "Installed BMW" })
    }

    private fun catalogWithVersion(version: Int, bmwDisplayName: String): ByteArray = bundledSeed()
        .replaceFirst("\"catalogVersion\":1", "\"catalogVersion\":$version")
        .replace("\"displayName\":\"BMW\"", "\"displayName\":\"$bmwDisplayName\"")
        .toByteArray()

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
