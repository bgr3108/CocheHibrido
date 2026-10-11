package com.bgr3108.kilonom.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coordinates an explicit catalog check. Callers decide when, if ever, to invoke it. */
/** Small UI-facing boundary: reading the local version never performs a network request. */
internal interface VehicleCatalogUpdateChecker {
    fun activeCatalogVersion(): Int
    suspend fun checkForUpdate(): VehicleCatalogUpdateResult
}

internal class VehicleCatalogUpdater(
    private val remoteSource: VehicleCatalogRemoteSource,
    private val installer: VehicleCatalogInstaller
) : VehicleCatalogUpdateChecker {
    private val updateMutex = Mutex()

    override fun activeCatalogVersion(): Int = installer.activeCatalogVersion()

    override suspend fun checkForUpdate(): VehicleCatalogUpdateResult = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            checkForUpdateOnIo()
        }
    }

    private fun checkForUpdateOnIo(): VehicleCatalogUpdateResult {
        val manifestJson = try {
            remoteSource.downloadManifest()
        } catch (_: Exception) {
            return VehicleCatalogUpdateResult.ManifestDownloadFailed
        }
        val manifest = try {
            parseVehicleCatalogManifest(manifestJson)
        } catch (_: Exception) {
            return VehicleCatalogUpdateResult.InvalidManifest
        }
        val activeVersion = try {
            installer.activeCatalogVersion()
        } catch (_: Exception) {
            return VehicleCatalogUpdateResult.InstallFailed(VehicleCatalogInstallResult.IoError)
        }
        if (manifest.catalogVersion <= activeVersion) {
            return VehicleCatalogUpdateResult.UpToDate(activeVersion)
        }
        val candidateBytes = try {
            remoteSource.downloadCatalog(manifest.file)
        } catch (_: Exception) {
            return VehicleCatalogUpdateResult.CatalogDownloadFailed
        }
        return when (val result = installer.install(manifestJson, candidateBytes)) {
            is VehicleCatalogInstallResult.Installed -> VehicleCatalogUpdateResult.Updated(result.catalogVersion)
            is VehicleCatalogInstallResult.NoUpdate -> VehicleCatalogUpdateResult.UpToDate(result.activeCatalogVersion)
            else -> VehicleCatalogUpdateResult.InstallFailed(result)
        }
    }
}

internal sealed interface VehicleCatalogUpdateResult {
    data class Updated(val catalogVersion: Int) : VehicleCatalogUpdateResult
    data class UpToDate(val activeCatalogVersion: Int) : VehicleCatalogUpdateResult
    data object ManifestDownloadFailed : VehicleCatalogUpdateResult
    data object InvalidManifest : VehicleCatalogUpdateResult
    data object CatalogDownloadFailed : VehicleCatalogUpdateResult
    data class InstallFailed(val result: VehicleCatalogInstallResult) : VehicleCatalogUpdateResult
}
