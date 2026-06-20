package com.example.cochehibrido.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VehicleRepository(

    val vehicleDataSource: VehicleDataSource,
    private val vehiclePreferences: VehiclePreferences

) {

    private val _vehicle = MutableStateFlow(Vehicle())
    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading

    val vehicle: StateFlow<Vehicle> = _vehicle

    init {

        CoroutineScope(Dispatchers.IO).launch {

            _vehicle.value =
                vehiclePreferences.loadVehicle()

            _isLoading.value = false
        }
    }

    fun saveVehicle(vehicle: Vehicle) {

        _vehicle.value = vehicle

        CoroutineScope(Dispatchers.IO).launch {

            vehiclePreferences.saveVehicle(vehicle)
        }
    }
}