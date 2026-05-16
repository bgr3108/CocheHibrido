package com.example.cochehibrido.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VehicleRepository {

    private val _vehicle = MutableStateFlow(Vehicle())

    val vehicle: StateFlow<Vehicle> = _vehicle

    fun saveVehicle(vehicle: Vehicle) {
        _vehicle.value = vehicle
    }
}