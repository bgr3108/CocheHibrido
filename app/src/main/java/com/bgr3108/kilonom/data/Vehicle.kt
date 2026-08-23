package com.bgr3108.kilonom.data

data class Vehicle(

    val id: Long? = null,

    val brand: String = "",

    val model: String = "",

    val year: Int? = null,

    val category: VehicleCategory = VehicleCategory.COCHE,

    val type: VehicleType? = null,

    val batteryCapacity: Double = 0.0,

    val fuelTankCapacity: Double = 0.0,

    val initialKm: Double = 0.0
)
