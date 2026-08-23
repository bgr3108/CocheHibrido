package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.VehicleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class InMemoryVehicleDao(
    initialVehicles: List<VehicleEntity> = emptyList()
) : VehicleDao {
    val vehicles = initialVehicles.toMutableList()
    private var nextId = (vehicles.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeAllVehicles(): Flow<List<VehicleEntity>> =
        flowOf(vehicles.sortedWith(compareBy<VehicleEntity> { it.createdAt }.thenBy { it.id }))

    override fun observeVehicle(id: Long): Flow<VehicleEntity?> = flowOf(vehicles.find { it.id == id })

    override suspend fun getVehicle(id: Long): VehicleEntity? = vehicles.find { it.id == id }

    override suspend fun getFirstVehicle(): VehicleEntity? =
        vehicles.minWithOrNull(compareBy<VehicleEntity> { it.createdAt }.thenBy { it.id })

    override suspend fun insert(vehicle: VehicleEntity): Long {
        val saved = vehicle.copy(id = if (vehicle.id == 0L) nextId++ else vehicle.id)
        vehicles += saved
        return saved.id
    }

    override suspend fun update(vehicle: VehicleEntity) {
        val index = vehicles.indexOfFirst { it.id == vehicle.id }
        check(index >= 0)
        vehicles[index] = vehicle
    }

    override suspend fun delete(vehicle: VehicleEntity) {
        vehicles.removeAll { it.id == vehicle.id }
    }

    override suspend fun deleteAll() {
        vehicles.clear()
    }
}

internal class InMemoryFuelEntryDao(
    val entries: MutableList<FuelEntry> = mutableListOf()
) : FuelEntryDao {
    override fun getAllEntries(): Flow<List<FuelEntry>> = flowOf(entries.toList())

    override fun getEntriesForVehicle(vehicleId: Long): Flow<List<FuelEntry>> =
        flowOf(entries.filter { it.vehicleId == vehicleId })

    override fun getLatestEntry(): Flow<FuelEntry?> = flowOf(entries.maxByOrNull { it.fecha })

    override suspend fun getKilometersForVehicle(vehicleId: Long): List<Double> =
        entries.filter { it.vehicleId == vehicleId }.map { it.km }

    override suspend fun insertEntry(entry: FuelEntry) {
        entries.removeAll { it.id == entry.id && entry.id != 0 }
        entries += entry
    }

    override suspend fun delete(entry: FuelEntry) {
        entries.remove(entry)
    }

    override suspend fun deleteAll() {
        entries.clear()
    }
}
