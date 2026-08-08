package com.example.cochehibrido.data

data class Vehicle(

    val brand: String = "",

    val model: String = "",

    val year: Int? = null,

    val category: VehicleCategory = VehicleCategory.COCHE,

    val type: VehicleType? = null,

    val batteryCapacity: Double = 0.0,

    val fuelTankCapacity: Double = 0.0,

    val currentKm: Double = 0.0
)
