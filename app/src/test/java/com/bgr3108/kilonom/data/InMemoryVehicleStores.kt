package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.VehicleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class InMemoryVehicleDao(
    initialVehicles: List<VehicleEntity> = emptyList()
) : VehicleDao {
    val vehicles = initialVehicles.toMutableList()
    private val vehiclesFlow = MutableStateFlow(vehicles.toList())
    private var nextId = (vehicles.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeAllVehicles(): Flow<List<VehicleEntity>> =
        vehiclesFlow.map { it.sortedWith(compareBy<VehicleEntity> { vehicle -> vehicle.createdAt }.thenBy { vehicle -> vehicle.id }) }

    override fun observeVehicle(id: Long): Flow<VehicleEntity?> =
        vehiclesFlow.map { vehicles -> vehicles.find { it.id == id } }

    override suspend fun getVehicle(id: Long): VehicleEntity? = vehicles.find { it.id == id }

    override suspend fun getFirstVehicle(): VehicleEntity? =
        vehicles.minWithOrNull(compareBy<VehicleEntity> { it.createdAt }.thenBy { it.id })

    override suspend fun insert(vehicle: VehicleEntity): Long {
        val saved = vehicle.copy(id = if (vehicle.id == 0L) nextId++ else vehicle.id)
        vehicles += saved
        vehiclesFlow.value = vehicles.toList()
        return saved.id
    }

    override suspend fun update(vehicle: VehicleEntity) {
        val index = vehicles.indexOfFirst { it.id == vehicle.id }
        check(index >= 0)
        vehicles[index] = vehicle
        vehiclesFlow.value = vehicles.toList()
    }

    override suspend fun delete(vehicle: VehicleEntity) {
        vehicles.removeAll { it.id == vehicle.id }
        vehiclesFlow.value = vehicles.toList()
    }

    override suspend fun deleteAll() {
        vehicles.clear()
        vehiclesFlow.value = emptyList()
    }
}

internal class InMemoryFuelEntryDao(
    val entries: MutableList<FuelEntry> = mutableListOf()
) : FuelEntryDao {
    private val entriesFlow = MutableStateFlow(entries.toList())

    override fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>> =
        entriesFlow.map { entries ->
            entries.filter { it.vehicleId == vehicleId }.sortedByDescending { it.fecha }
        }

    override suspend fun getEntryForVehicle(entryId: Int, vehicleId: Long): FuelEntry? =
        entries.find { it.id == entryId && it.vehicleId == vehicleId }

    override suspend fun getKilometersForVehicle(vehicleId: Long): List<Double> =
        entries.filter { it.vehicleId == vehicleId }.map { it.km }

    override suspend fun countEntries(vehicleId: Long): Int =
        entries.count { it.vehicleId == vehicleId }

    override suspend fun insertEntry(entry: FuelEntry) {
        entries.removeAll { it.id == entry.id && entry.id != 0 }
        entries += entry
        entriesFlow.value = entries.toList()
    }

    override suspend fun updateEntry(entry: FuelEntry): Int {
        val index = entries.indexOfFirst { it.id == entry.id && it.vehicleId == entry.vehicleId }
        if (index < 0) return 0
        entries[index] = entry
        entriesFlow.value = entries.toList()
        return 1
    }

    override suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int {
        val removed = entries.removeAll { it.id == entryId && it.vehicleId == vehicleId }
        if (removed) entriesFlow.value = entries.toList()
        return if (removed) 1 else 0
    }

    override suspend fun deleteAll() {
        entries.clear()
        entriesFlow.value = emptyList()
    }
}
