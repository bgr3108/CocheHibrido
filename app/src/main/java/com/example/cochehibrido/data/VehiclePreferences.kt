package com.example.cochehibrido.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(
    name = "vehicle_preferences"
)

class VehiclePreferences(
    private val context: Context
) {

    private object Keys {

        val BRAND =
            stringPreferencesKey("brand")

        val MODEL =
            stringPreferencesKey("model")

        val YEAR =
            intPreferencesKey("year")

        val TYPE =
            stringPreferencesKey("type")

        val BATTERY =
            doublePreferencesKey("battery")

        val TANK =
            doublePreferencesKey("tank")

        val CURRENT_KM =
            doublePreferencesKey("current_km")
    }

    suspend fun saveVehicle(
        vehicle: Vehicle
    ) {

        context.dataStore.edit { prefs ->

            prefs[Keys.BRAND] = vehicle.brand
            prefs[Keys.MODEL] = vehicle.model
            prefs[Keys.YEAR] = vehicle.year ?: 0

            prefs[Keys.TYPE] =
                vehicle.type?.name ?: ""

            prefs[Keys.BATTERY] =
                vehicle.batteryCapacity

            prefs[Keys.TANK] =
                vehicle.fuelTankCapacity

            prefs[Keys.CURRENT_KM] =
                vehicle.currentKm
        }
    }

    suspend fun loadVehicle(): Vehicle {

        val prefs =
            context.dataStore.data.first()

        return Vehicle(

            brand =
                prefs[Keys.BRAND] ?: "",

            model =
                prefs[Keys.MODEL] ?: "",

            year =
                prefs[Keys.YEAR],

            type =
                prefs[Keys.TYPE]
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        VehicleType.valueOf(it)
                    },

            batteryCapacity =
                prefs[Keys.BATTERY] ?: 0.0,

            fuelTankCapacity =
                prefs[Keys.TANK] ?: 0.0,

                currentKm =
                prefs[Keys.CURRENT_KM] ?: 0.0
        )
    }
    suspend fun clearVehicle() {

        context.dataStore.edit {

            it.clear()
        }
    }
}