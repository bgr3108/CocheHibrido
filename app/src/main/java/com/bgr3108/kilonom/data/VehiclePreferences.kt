package com.bgr3108.kilonom.data

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

interface VehiclePreferencesStore {
    suspend fun saveVehicle(vehicle: Vehicle)
    suspend fun loadVehicle(): Vehicle
    suspend fun clearVehicle()
    suspend fun hasSeenReleaseNotes(versionName: String): Boolean = false
    suspend fun markReleaseNotesAsSeen(versionName: String) = Unit
}

class VehiclePreferences(
    private val context: Context
) : VehiclePreferencesStore {

    private object Keys {

        val BRAND =
            stringPreferencesKey("brand")

        val MODEL =
            stringPreferencesKey("model")

        val YEAR =
            intPreferencesKey("year")

        val TYPE =
            stringPreferencesKey("type")

        val CATEGORY =
            stringPreferencesKey("vehicle_category")

        val BATTERY =
            doublePreferencesKey("battery")

        val TANK =
            doublePreferencesKey("tank")

        val CURRENT_KM =
            doublePreferencesKey("current_km")

        val LAST_SEEN_RELEASE_NOTES_VERSION =
            stringPreferencesKey("last_seen_release_notes_version")
    }

    override suspend fun saveVehicle(
        vehicle: Vehicle
    ) {

        context.dataStore.edit { prefs ->

            prefs[Keys.BRAND] = vehicle.brand
            prefs[Keys.MODEL] = vehicle.model
            prefs[Keys.YEAR] = vehicle.year ?: 0

            prefs[Keys.TYPE] =
                vehicle.type?.name ?: ""

            prefs[Keys.CATEGORY] = vehicle.category.name

            prefs[Keys.BATTERY] =
                vehicle.batteryCapacity

            prefs[Keys.TANK] =
                vehicle.fuelTankCapacity

            prefs[Keys.CURRENT_KM] =
                vehicle.currentKm
        }
    }

    override suspend fun loadVehicle(): Vehicle {

        val prefs =
            context.dataStore.data.first()

        return Vehicle(

            brand =
                prefs[Keys.BRAND] ?: "",

            model =
                prefs[Keys.MODEL] ?: "",

            year =
                prefs[Keys.YEAR],

            type = vehicleTypeOrNull(prefs[Keys.TYPE]),

            category = vehicleCategoryOrDefault(prefs[Keys.CATEGORY]),

            batteryCapacity =
                prefs[Keys.BATTERY] ?: 0.0,

            fuelTankCapacity =
                prefs[Keys.TANK] ?: 0.0,

                currentKm =
                prefs[Keys.CURRENT_KM] ?: 0.0
        )
    }
    override suspend fun clearVehicle() {

        context.dataStore.edit {

            it.clear()
        }
    }

    override suspend fun hasSeenReleaseNotes(versionName: String): Boolean =
        context.dataStore.data.first()[Keys.LAST_SEEN_RELEASE_NOTES_VERSION] == versionName

    override suspend fun markReleaseNotesAsSeen(versionName: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_SEEN_RELEASE_NOTES_VERSION] = versionName
        }
    }
}

internal fun vehicleTypeOrNull(value: String?): VehicleType? =
    value
        ?.takeIf { it.isNotBlank() }
        ?.let { storedType ->
            if (storedType == "HEV") {
                VehicleType.HIBRIDO
            } else {
                VehicleType.entries.firstOrNull { it.name == storedType }
            }
        }

internal fun vehicleCategoryOrDefault(value: String?): VehicleCategory =
    value
        ?.takeIf { it.isNotBlank() }
        ?.let { storedCategory ->
            VehicleCategory.entries.firstOrNull { it.name == storedCategory }
        }
        ?: VehicleCategory.COCHE
