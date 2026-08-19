package com.bgr3108.kilonom.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val RELEASE_NOTES_VERSION = "1.0.3"

class VehicleRepository(

    val vehicleDataSource: VehicleCatalog,
    private val vehiclePreferences: VehiclePreferencesStore

) {

    private val _vehicle = MutableStateFlow(Vehicle())
    private val _isLoading = MutableStateFlow(true)
    private val _loadError = MutableStateFlow<Throwable?>(null)
    private val _showReleaseNotes = MutableStateFlow(false)
    private val releaseNotesMutex = Mutex()

    val isLoading: StateFlow<Boolean> = _isLoading
    val loadError: StateFlow<Throwable?> = _loadError

    val vehicle: StateFlow<Vehicle> = _vehicle
    val showReleaseNotes: StateFlow<Boolean> = _showReleaseNotes

    init {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                _vehicle.value = vehiclePreferences.loadVehicle()
            } catch (error: Throwable) {
                _vehicle.value = Vehicle()
                _loadError.value = error
            } finally {
                _showReleaseNotes.value = runCatching {
                    !vehiclePreferences.hasSeenReleaseNotes(RELEASE_NOTES_VERSION)
                }.getOrDefault(false)
                _isLoading.value = false
            }
        }
    }

    suspend fun saveVehicle(vehicle: Vehicle) {

        vehiclePreferences.saveVehicle(vehicle)
        _vehicle.value = vehicle
    }
    suspend fun clearVehicle() {

        vehiclePreferences.clearVehicle()
        _vehicle.value = Vehicle()
    }

    suspend fun dismissReleaseNotes() {
        releaseNotesMutex.withLock {
            if (!_showReleaseNotes.value) return

            vehiclePreferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
            _showReleaseNotes.value = false
        }
    }
}
