package com.bgr3108.kilonom.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Validates and installs a catalog already available locally. Transport and update scheduling are
 * deliberately outside this component.
 */
internal class VehicleCatalogInstaller internal constructor(
    private val storage: VehicleCatalogInstallStorage
) {
    constructor(context: Context) : this(AndroidVehicleCatalogInstallStorage(context))

    fun install(manifestJson: String, candidateBytes: ByteArray): VehicleCatalogInstallResult {
        val manifest = try {
            parseVehicleCatalogManifest(manifestJson)
        } catch (_: Exception) {
            return VehicleCatalogInstallResult.InvalidManifest
        }
        if (catalogSha256(candidateBytes) != manifest.sha256) {
            return VehicleCatalogInstallResult.ChecksumMismatch
        }
        val candidate = try {
            parseRuntimeVehicleCatalog(candidateBytes.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            return VehicleCatalogInstallResult.InvalidCatalog
        }
        if (candidate.catalogVersion != manifest.catalogVersion) {
            return VehicleCatalogInstallResult.VersionMismatch
        }
        val activeVersion = try {
            activeCatalogVersion()
        } catch (_: Exception) {
            return VehicleCatalogInstallResult.IoError
        }
        if (candidate.catalogVersion <= activeVersion) {
            return VehicleCatalogInstallResult.NoUpdate(activeVersion)
        }
        return try {
            storage.writeTemporary(candidateBytes)
            storage.promoteTemporary()
            VehicleCatalogInstallResult.Installed(candidate.catalogVersion)
        } catch (_: Exception) {
            VehicleCatalogInstallResult.IoError
        } finally {
            storage.discardTemporary()
        }
    }

    private fun activeCatalogVersion(): Int {
        val installed = try {
            storage.readInstalledCatalogBytes()?.let { bytes ->
                parseRuntimeVehicleCatalog(bytes.toString(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            null
        }
        return installed?.catalogVersion
            ?: parseRuntimeVehicleCatalog(storage.readBundledSeedBytes().toString(Charsets.UTF_8)).catalogVersion
    }
}

internal sealed interface VehicleCatalogInstallResult {
    data class Installed(val catalogVersion: Int) : VehicleCatalogInstallResult
    data class NoUpdate(val activeCatalogVersion: Int) : VehicleCatalogInstallResult
    data object InvalidManifest : VehicleCatalogInstallResult
    data object ChecksumMismatch : VehicleCatalogInstallResult
    data object InvalidCatalog : VehicleCatalogInstallResult
    data object VersionMismatch : VehicleCatalogInstallResult
    data object IoError : VehicleCatalogInstallResult
}

/** File operations for catalog installation only; catalog reading remains in VehicleCatalogLocalSource. */
internal interface VehicleCatalogInstallStorage {
    fun readInstalledCatalogBytes(): ByteArray?
    fun readBundledSeedBytes(): ByteArray
    fun writeTemporary(bytes: ByteArray)
    fun promoteTemporary()
    fun discardTemporary()
}

internal class AndroidVehicleCatalogInstallStorage(
    private val context: Context
) : VehicleCatalogInstallStorage {
    private val catalogDirectory = File(context.filesDir, INSTALLED_RUNTIME_CATALOG_DIRECTORY)
    private val atomicFile = AtomicFile(File(catalogDirectory, INSTALLED_RUNTIME_CATALOG_FILE_NAME))
    private var pendingWrite: FileOutputStream? = null

    override fun readInstalledCatalogBytes(): ByteArray? = try {
        atomicFile.openRead().use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }

    override fun readBundledSeedBytes(): ByteArray =
        context.assets.open(RUNTIME_CATALOG_ASSET).use { it.readBytes() }

    override fun writeTemporary(bytes: ByteArray) {
        check(pendingWrite == null) { "A catalog installation is already in progress" }
        if (!catalogDirectory.exists() && !catalogDirectory.mkdirs()) {
            throw IOException("Unable to create catalog directory")
        }
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            pendingWrite = output
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }

    override fun promoteTemporary() {
        val output = pendingWrite ?: throw IOException("No temporary catalog to promote")
        try {
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        } finally {
            pendingWrite = null
        }
    }

    override fun discardTemporary() {
        pendingWrite?.let(atomicFile::failWrite)
        pendingWrite = null
    }
}

internal fun catalogSha256(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hexadecimal = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hexadecimal[value ushr 4])
            append(hexadecimal[value and 0x0f])
        }
    }
}

internal const val INSTALLED_RUNTIME_CATALOG_DIRECTORY = "catalog"
internal const val INSTALLED_RUNTIME_CATALOG_FILE_NAME = "catalog-runtime.json"
