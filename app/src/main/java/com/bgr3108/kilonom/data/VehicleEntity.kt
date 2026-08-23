package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent snapshot of a configured vehicle.
 *
 * These fields deliberately do not refer back to the bundled catalog: catalog updates must not
 * alter vehicles that users have already configured.
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: VehicleCategory,
    val brand: String,
    val model: String,
    val year: Int?,
    val type: VehicleType?,
    val fuelTankCapacity: Double,
    val batteryCapacity: Double,
    val initialKm: Double,
    val createdAt: Long
)

internal fun VehicleEntity.toVehicle() = Vehicle(
    id = id,
    brand = brand,
    model = model,
    year = year,
    category = category,
    type = type,
    batteryCapacity = batteryCapacity,
    fuelTankCapacity = fuelTankCapacity,
    initialKm = initialKm
)

internal fun Vehicle.toEntity(
    id: Long = this.id ?: 0,
    createdAt: Long
) = VehicleEntity(
    id = id,
    category = category,
    brand = brand,
    model = model,
    year = year,
    type = type,
    fuelTankCapacity = fuelTankCapacity,
    batteryCapacity = batteryCapacity,
    initialKm = initialKm,
    createdAt = createdAt
)
