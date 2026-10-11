package com.bgr3108.kilonom.data

import android.content.Context
import android.util.Log
interface VehicleCatalog {
    fun loadVehicles(category: VehicleCategory): List<VehicleInfo>
    fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog
    fun reconcileLegacyVehicle(vehicle: VehicleEntity): LegacyCatalogReconciliation =
        LegacyCatalogReconciliation.NoMatch
}

class VehicleDataSource internal constructor(
    private val catalogSource: VehicleCatalogLocalSource
) : VehicleCatalog {

    constructor(context: Context) : this(AndroidVehicleCatalogLocalSource(context))

    private val runtimeCatalog: RuntimeVehicleCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadRuntimeCatalog()
    }
    private val vehiclesByCategory: Map<VehicleCategory, List<VehicleInfo>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VehicleCategory.entries.associateWith(runtimeCatalog::vehiclesFor)
    }
    private val selectionsByCategory: Map<VehicleCategory, VehicleSelectionCatalog> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VehicleCategory.entries.associateWith(runtimeCatalog::selectionFor)
    }

    override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> =
        vehiclesByCategory.getValue(category)

    override fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog =
        selectionsByCategory.getValue(category)

    override fun reconcileLegacyVehicle(vehicle: VehicleEntity): LegacyCatalogReconciliation =
        runtimeCatalog.reconcileLegacyVehicle(vehicle)

    private fun loadRuntimeCatalog(): RuntimeVehicleCatalog {
        val installedCatalog = try {
            catalogSource.readInstalledCatalog()?.let(::parseRuntimeVehicleCatalog)
        } catch (exception: Exception) {
            logInstalledCatalogFailure(exception)
            null
        }
        return installedCatalog ?: parseRuntimeVehicleCatalog(catalogSource.readBundledSeed())
    }

    /** Logging must never prevent the offline seed fallback, including in plain JVM tests. */
    private fun logInstalledCatalogFailure(exception: Exception) {
        try {
            Log.w(TAG, "Ignoring unavailable or invalid installed vehicle catalog; using bundled seed", exception)
        } catch (_: RuntimeException) {
            // android.util.Log is intentionally not implemented by the local JVM test stub.
        }
    }

    private companion object {
        const val TAG = "VehicleDataSource"
    }
}

internal fun isVehicleSelectionCompatible(
    vehicle: VehicleInfo?,
    category: VehicleCategory
): Boolean = vehicle?.category == category
