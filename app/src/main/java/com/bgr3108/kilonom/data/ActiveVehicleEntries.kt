package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Atomic read context for screens that depend on both vehicle configuration and its history.
 * A change of active vehicle cancels the former entries query before any new context is emitted.
 */
data class ActiveVehicleEntries(
    val vehicle: Vehicle,
    val entries: List<FuelEntry>
)

@OptIn(ExperimentalCoroutinesApi::class)
fun FuelRepository.observeActiveVehicleEntries(
    activeVehicle: Flow<VehicleEntity?>
): Flow<ActiveVehicleEntries> = activeVehicle.flatMapLatest { vehicle ->
    vehicle?.let { entity ->
        observeEntries(entity.id).map { entries ->
            ActiveVehicleEntries(entity.toVehicle(), entries)
        }
    } ?: flowOf(ActiveVehicleEntries(Vehicle(), emptyList()))
}
