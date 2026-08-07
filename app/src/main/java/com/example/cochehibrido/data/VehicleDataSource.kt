package com.example.cochehibrido.data

import android.content.Context
import org.json.JSONArray

interface VehicleCatalog {
    fun loadVehicles(): List<VehicleInfo>
}

class VehicleDataSource(
    private val context: Context
) : VehicleCatalog {

    override fun loadVehicles(): List<VehicleInfo> {

        val jsonString = context.assets
            .open("vehicles.json")
            .bufferedReader()
            .use { it.readText() }

        val jsonArray = JSONArray(jsonString)

        val vehicles = mutableListOf<VehicleInfo>()

        for (i in 0 until jsonArray.length()) {

            val obj = jsonArray.getJSONObject(i)

            vehicles.add(
                VehicleInfo(
                    brand = obj.getString("brand"),
                    model = obj.getString("model"),
                    year = obj.getInt("year"),
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
}
