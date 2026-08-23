package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import kotlinx.coroutines.flow.Flow

class FuelRepository(
    private val fuelEntryDao: FuelEntryDao
) {
    /**
     * Compatibility stream used by the one-vehicle UI during phase 1. Phase 2 must switch its
     * callers to [getEntriesForVehicle] once vehicle switching is exposed.
     */
    fun getAllEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllEntries()

    fun getEntriesForVehicle(vehicleId: Long): Flow<List<FuelEntry>> =
        fuelEntryDao.getEntriesForVehicle(vehicleId)

    suspend fun addEntry(entry: FuelEntry) {
        fuelEntryDao.insertEntry(entry)
    }

    suspend fun delete(entry: FuelEntry) {
        fuelEntryDao.delete(entry)
    }

    suspend fun deleteAll() {
        fuelEntryDao.deleteAll()
    }
}
