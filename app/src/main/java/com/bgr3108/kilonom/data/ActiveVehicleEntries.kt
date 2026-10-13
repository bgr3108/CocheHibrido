package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Atomic read context for screens that depend on both vehicle configuration and its history.
 * A change of active vehicle cancels the former entries query before any new context is emitted.
 */
data class ActiveVehicleEntries(
    val vehicle: Vehicle,
    val entries: List<FuelEntry>
)

fun FuelRepository.observeActiveVehicleEntries(
    vehicleRepository: VehicleRepository
): Flow<ActiveVehicleEntries> = vehicleRepository.observeLoadedVehicle { vehicle ->
    vehicle?.let { entity ->
        observeEntries(entity.id).map { entries ->
            ActiveVehicleEntries(entity.toVehicle(), entries)
        }
    } ?: flowOf(ActiveVehicleEntries(Vehicle(), emptyList()))
}
