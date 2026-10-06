package com.bgr3108.kilonom.data

import android.content.Context
interface VehicleCatalog {
    fun loadVehicles(category: VehicleCategory): List<VehicleInfo>

    fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog =
        VehicleSelectionCatalog.fromLegacy(category, loadVehicles(category))
}

class VehicleDataSource(
    private val context: Context
) : VehicleCatalog {

    private val runtimeCatalog: RuntimeVehicleCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        context.assets.open(RUNTIME_CATALOG_ASSET).bufferedReader().use { reader ->
            parseRuntimeVehicleCatalog(reader.readText())
        }
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
}

internal fun isVehicleSelectionCompatible(
    vehicle: VehicleInfo?,
    category: VehicleCategory
): Boolean = vehicle?.category == category
