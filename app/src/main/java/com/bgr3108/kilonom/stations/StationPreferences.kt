package com.bgr3108.kilonom.stations

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.stationPreferencesDataStore by preferencesDataStore(name = "station_preferences")

/** Stores only permission-flow UI state; no position is stored. */
interface StationPreferencesStore {
    suspend fun hasSeenLocationIntro(): Boolean
    suspend fun markLocationIntroSeen()
    suspend fun hasRequestedLocationPermission(): Boolean
    suspend fun markLocationPermissionRequested()
}

class StationPreferences(private val context: Context) : StationPreferencesStore {
    override suspend fun hasSeenLocationIntro(): Boolean =
        context.stationPreferencesDataStore.data.first()[LOCATION_INTRO_SHOWN] == true

    override suspend fun markLocationIntroSeen() {
        context.stationPreferencesDataStore.edit { it[LOCATION_INTRO_SHOWN] = true }
    }

    override suspend fun hasRequestedLocationPermission(): Boolean =
        context.stationPreferencesDataStore.data.first()[LOCATION_PERMISSION_REQUESTED] == true

    override suspend fun markLocationPermissionRequested() {
        context.stationPreferencesDataStore.edit { it[LOCATION_PERMISSION_REQUESTED] = true }
    }

    private companion object {
        val LOCATION_INTRO_SHOWN = booleanPreferencesKey("station_location_intro_shown")
        val LOCATION_PERMISSION_REQUESTED = booleanPreferencesKey("station_location_permission_requested")
    }
}

object NoOpStationPreferences : StationPreferencesStore {
    override suspend fun hasSeenLocationIntro(): Boolean = true
    override suspend fun markLocationIntroSeen() = Unit
    override suspend fun hasRequestedLocationPermission(): Boolean = false
    override suspend fun markLocationPermissionRequested() = Unit
}
