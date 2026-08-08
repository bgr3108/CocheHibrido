package com.example.cochehibrido.data

import android.content.Context
import org.json.JSONArray

interface VehicleCatalog {
    fun loadVehicles(category: VehicleCategory): List<VehicleInfo>
}

class VehicleDataSource(
    private val context: Context
) : VehicleCatalog {

    override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> {

        val jsonString = context.assets
            .open(catalogFileNameFor(category))
            .bufferedReader()
            .use { it.readText() }

        return parseVehicleCatalog(jsonString, category)
    }
}

internal fun catalogFileNameFor(category: VehicleCategory): String =
    when (category) {
        VehicleCategory.COCHE -> "vehicles.json"
        VehicleCategory.MOTO -> "motorcycles.json"
    }

internal fun isVehicleSelectionCompatible(
    vehicle: VehicleInfo?,
    category: VehicleCategory
): Boolean = vehicle?.category == category

internal fun parseVehicleCatalog(
    jsonString: String,
    category: VehicleCategory
): List<VehicleInfo> {
    val jsonArray = JSONArray(jsonString)

    val vehicles = mutableListOf<VehicleInfo>()

    for (i in 0 until jsonArray.length()) {

        val obj = jsonArray.getJSONObject(i)

        vehicles.add(
            VehicleInfo(
                brand = obj.getString("brand"),
                model = obj.getString("model"),
                year = obj.getInt("year"),
                category = category,
                type = try {
                    VehicleType.valueOf(
                        obj.getString("type")
                    )
                } catch (e: Exception) {
                    VehicleType.GASOLINA
                },
                batteryCapacity = obj.optDouble("batteryCapacity", 0.0),
                fuelTankCapacity = obj.optDouble("fuelTankCapacity", 0.0)
            )
        )
    }

    return vehicles
}
