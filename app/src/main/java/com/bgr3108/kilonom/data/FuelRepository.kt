package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import kotlinx.coroutines.flow.Flow

class FuelRepository(
    private val fuelEntryDao: FuelEntryDao
) {
    fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>> =
        fuelEntryDao.observeEntries(vehicleId)

    suspend fun addEntryForVehicle(entry: FuelEntry, vehicleId: Long) {
        require(vehicleId > 0) { "Se requiere un vehículo activo válido" }
        require(entry.id == 0) { "Una entrada existente debe actualizarse" }
        fuelEntryDao.insertEntry(entry.copy(vehicleId = vehicleId))
    }

    suspend fun updateEntryForVehicle(entry: FuelEntry, vehicleId: Long) {
        require(vehicleId > 0 && entry.vehicleId == vehicleId) {
            "La entrada no pertenece al vehículo activo"
        }
        require(fuelEntryDao.getEntryForVehicle(entry.id, vehicleId) != null) {
            "No existe una entrada editable para el vehículo activo"
        }
        fuelEntryDao.updateEntry(entry)
    }

    suspend fun deleteEntryForVehicle(entry: FuelEntry, vehicleId: Long) {
        require(vehicleId > 0 && entry.vehicleId == vehicleId) {
            "La entrada no pertenece al vehículo activo"
        }
        fuelEntryDao.deleteEntryForVehicle(entry.id, vehicleId)
    }

    suspend fun deleteAll() {
        fuelEntryDao.deleteAll()
    }
}
