package com.bgr3108.kilonom.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

/**
 * Physical local sources for the runtime catalog.
 *
 * The installed file is deliberately read-only here. A later update layer will be responsible
 * for validating, atomically installing, and rolling back downloaded catalogs.
 */
internal interface VehicleCatalogLocalSource {
    fun readInstalledCatalog(): String?
    fun readBundledSeed(): String
}

internal class AndroidVehicleCatalogLocalSource(
    private val context: Context
) : VehicleCatalogLocalSource {

    override fun readInstalledCatalog(): String? {
        val installedCatalog = AtomicFile(File(context.filesDir, INSTALLED_RUNTIME_CATALOG_PATH))
        return try {
            installedCatalog.openRead().bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun readBundledSeed(): String =
        context.assets.open(RUNTIME_CATALOG_ASSET).bufferedReader().use { it.readText() }
}

/** Reserved private-files location for a future validated catalog installation. */
internal const val INSTALLED_RUNTIME_CATALOG_PATH =
    "$INSTALLED_RUNTIME_CATALOG_DIRECTORY/$INSTALLED_RUNTIME_CATALOG_FILE_NAME"
