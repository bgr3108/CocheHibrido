package com.bgr3108.kilonom.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.IOException

class VehicleCatalogUpdaterTest {

    @Test
    fun installedCatalogIsNotRevertedOrReportedAsFailedWhenTimestampCannotBeSaved() = runBlocking {
        val seed = bundledSeedBytes()
        val version = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(version)
        val storage = FakeInstallStorage(seed = seed)
        val remote = FakeRemoteSource(manifest = manifestFor(candidate, version), catalog = candidate)
        val timestamps = object : VehicleCatalogUpdateTimestampStore {
            override suspend fun lastCatalogCheck(): Long? = null
            override suspend fun saveLastCatalogCheck(epochMillis: Long) { throw IOException("write failed") }
        }
        val coordinator = VehicleCatalogUpdateCoordinator(VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)), timestamps)
        assertEquals(VehicleCatalogUpdateResult.Updated(version), coordinator.checkForUpdate())
        assertArrayEquals(candidate, storage.installed)
        assertEquals(version, coordinator.activeCatalogVersion())
        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(1, remote.manifestDownloads)
        assertEquals(1, remote.catalogDownloads)
    }

    @Test
    fun sameRemoteVersion_isUpToDateAndNeverDownloadsTheCatalog() = runBlocking {
        val seed = bundledSeedBytes()
        val baseVersion = seedCatalogVersion()
        val remote = FakeRemoteSource(manifest = manifestFor(seed, baseVersion), catalog = catalogWithVersion(baseVersion + 1))
        val result = VehicleCatalogUpdater(remote, installer(seed)).checkForUpdate()

        assertEquals(VehicleCatalogUpdateResult.UpToDate(baseVersion), result)
        assertEquals(1, remote.manifestDownloads)
        assertEquals(0, remote.catalogDownloads)
    }

    @Test
    fun olderRemoteVersion_isUpToDateAndNeverDownloadsTheCatalog() = runBlocking {
        val seed = bundledSeedBytes()
        val baseVersion = seedCatalogVersion()
        val installedVersion = baseVersion + 1
        val installedV2 = catalogWithVersion(installedVersion)
        val remote = FakeRemoteSource(manifest = manifestFor(seed, baseVersion), catalog = seed)
        val storage = FakeInstallStorage(installed = installedV2, seed = seed)

        assertEquals(
            VehicleCatalogUpdateResult.UpToDate(installedVersion),
            VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)).checkForUpdate()
        )
        assertEquals(0, remote.catalogDownloads)
        assertArrayEquals(installedV2, storage.installed)
    }

    @Test
    fun newerValidCatalog_isInstalledThroughTheExistingInstaller() = runBlocking {
        val seed = bundledSeedBytes()
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion)
        val storage = FakeInstallStorage(seed = seed)
        val remote = FakeRemoteSource(manifest = manifestFor(candidate, candidateVersion), catalog = candidate)

        assertEquals(
            VehicleCatalogUpdateResult.Updated(candidateVersion),
            VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)).checkForUpdate()
        )
        assertEquals(1, remote.catalogDownloads)
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun rejectedCandidate_keepsThePreviousInstalledCatalogIntact() = runBlocking {
        val previousVersion = seedCatalogVersion() + 1
        val previous = catalogWithVersion(previousVersion)
        val corruptCandidate = "{ broken".toByteArray()
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())
        val remote = FakeRemoteSource(manifest = manifestFor(corruptCandidate, previousVersion + 1), catalog = corruptCandidate)

        assertEquals(
            VehicleCatalogUpdateResult.InstallFailed(VehicleCatalogInstallResult.InvalidCatalog),
            VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)).checkForUpdate()
        )
        assertArrayEquals(previous, storage.installed)
    }

    @Test
    fun checksumMismatch_isReportedByTheInstallerWithoutChangingTheCatalog() = runBlocking {
        val previousVersion = seedCatalogVersion() + 1
        val candidateVersion = previousVersion + 1
        val previous = catalogWithVersion(previousVersion)
        val candidate = catalogWithVersion(candidateVersion)
        val storage = FakeInstallStorage(installed = previous, seed = bundledSeedBytes())
        val remote = FakeRemoteSource(
            manifest = manifestFor(candidate, candidateVersion, sha256 = "0".repeat(64)),
            catalog = candidate
        )

        assertEquals(
            VehicleCatalogUpdateResult.InstallFailed(VehicleCatalogInstallResult.ChecksumMismatch),
            VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)).checkForUpdate()
        )
        assertArrayEquals(previous, storage.installed)
    }

    @Test
    fun invalidManifestAndNetworkFailures_areControlled() = runBlocking {
        val seed = bundledSeedBytes()
        assertEquals(
            VehicleCatalogUpdateResult.InvalidManifest,
            VehicleCatalogUpdater(FakeRemoteSource(manifest = "{", catalog = seed), installer(seed)).checkForUpdate()
        )
        assertEquals(
            VehicleCatalogUpdateResult.ManifestDownloadFailed,
            VehicleCatalogUpdater(FakeRemoteSource(manifestFailure = IOException("offline")), installer(seed)).checkForUpdate()
        )
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion)
        assertEquals(
            VehicleCatalogUpdateResult.CatalogDownloadFailed,
            VehicleCatalogUpdater(
                FakeRemoteSource(manifest = manifestFor(candidate, candidateVersion), catalogFailure = IOException("timeout")),
                installer(seed)
            ).checkForUpdate()
        )
    }

    @Test
    fun corruptInstalledCatalog_usesSeedAsTheUpdateBaseline() = runBlocking {
        val seed = bundledSeedBytes()
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion)
        val storage = FakeInstallStorage(installed = "not a catalog".toByteArray(), seed = seed)
        val remote = FakeRemoteSource(manifest = manifestFor(candidate, candidateVersion), catalog = candidate)

        assertEquals(
            VehicleCatalogUpdateResult.Updated(candidateVersion),
            VehicleCatalogUpdater(remote, VehicleCatalogInstaller(storage)).checkForUpdate()
        )
        assertArrayEquals(candidate, storage.installed)
    }

    @Test
    fun concurrentChecks_shareOneInstallationAndTheSecondSeesTheInstalledVersion() = runBlocking {
        val seed = bundledSeedBytes()
        val candidateVersion = seedCatalogVersion() + 1
        val candidate = catalogWithVersion(candidateVersion)
        val remote = FakeRemoteSource(manifest = manifestFor(candidate, candidateVersion), catalog = candidate, catalogDelayMillis = 100)
        val updater = VehicleCatalogUpdater(remote, installer(seed))

        val results = listOf(
            async(Dispatchers.Default) { updater.checkForUpdate() },
            async(Dispatchers.Default) { updater.checkForUpdate() }
        ).awaitAll()

        assertEquals(1, remote.catalogDownloads)
        assertEquals(1, results.count { it is VehicleCatalogUpdateResult.Updated })
        assertEquals(1, results.count { it is VehicleCatalogUpdateResult.UpToDate })
    }

    private fun installer(seed: ByteArray): VehicleCatalogInstaller =
        VehicleCatalogInstaller(FakeInstallStorage(seed = seed))

    private fun catalogWithVersion(version: Int): ByteArray {
        val root = Json.parseToJsonElement(bundledSeed()).jsonObject
        return Json.encodeToString(
            JsonObject(root + ("catalogVersion" to JsonPrimitive(version)))
        ).toByteArray()
    }

    private fun seedCatalogVersion(): Int = parseRuntimeVehicleCatalog(bundledSeed()).catalogVersion

    private fun manifestFor(
        bytes: ByteArray,
        version: Int,
        sha256: String = catalogSha256(bytes)
    ): String = """{"schemaVersion":1,"catalogVersion":$version,"generatedAt":"2026-10-05T00:00:00Z","sha256":"$sha256","file":"catalog-runtime.json"}"""

    private fun bundledSeed(): String = findRuntimeAsset().readText()

    private fun bundledSeedBytes(): ByteArray = bundledSeed().toByteArray()

    private fun findRuntimeAsset(): File = sequenceOf(
        File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
        File("src/main/assets", RUNTIME_CATALOG_ASSET),
        File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $RUNTIME_CATALOG_ASSET")

    private class FakeRemoteSource(
        private val manifest: String? = null,
        private val catalog: ByteArray? = null,
        private val manifestFailure: Exception? = null,
        private val catalogFailure: Exception? = null,
        private val catalogDelayMillis: Long = 0
    ) : VehicleCatalogRemoteSource {
        var manifestDownloads = 0
            private set
        var catalogDownloads = 0
            private set

        override fun downloadManifest(): String = synchronized(this) {
            manifestDownloads++
            manifestFailure?.let { throw it }
            requireNotNull(manifest)
        }

        override fun downloadCatalog(fileName: String): ByteArray {
            synchronized(this) { catalogDownloads++ }
            if (catalogDelayMillis > 0) Thread.sleep(catalogDelayMillis)
            catalogFailure?.let { throw it }
            return requireNotNull(catalog)
        }
    }

    private class FakeInstallStorage(
        var installed: ByteArray? = null,
        private val seed: ByteArray
    ) : VehicleCatalogInstallStorage {
        private var temporary: ByteArray? = null

        override fun readInstalledCatalogBytes(): ByteArray? = installed
        override fun readBundledSeedBytes(): ByteArray = seed
        override fun writeTemporary(bytes: ByteArray) {
            temporary = bytes.copyOf()
        }
        override fun promoteTemporary() {
            installed = requireNotNull(temporary).copyOf()
            temporary = null
        }
        override fun discardTemporary() {
            temporary = null
        }
    }
}
