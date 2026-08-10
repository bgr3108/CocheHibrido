package com.bgr3108.kilonom.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VehicleRepository(

    val vehicleDataSource: VehicleCatalog,
    private val vehiclePreferences: VehiclePreferencesStore

) {

    private val _vehicle = MutableStateFlow(Vehicle())
    private val _isLoading = MutableStateFlow(true)
    private val _loadError = MutableStateFlow<Throwable?>(null)

    val isLoading: StateFlow<Boolean> = _isLoading
    val loadError: StateFlow<Throwable?> = _loadError

    val vehicle: StateFlow<Vehicle> = _vehicle

    init {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                _vehicle.value = vehiclePreferences.loadVehicle()
            } catch (error: Throwable) {
                _vehicle.value = Vehicle()
                _loadError.value = error
            } finally {
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
}
