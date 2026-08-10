package com.example.cochehibrido.data

import com.example.cochehibrido.database.FuelEntryDao
import kotlinx.coroutines.flow.Flow

class FuelRepository(
    private val fuelEntryDao: FuelEntryDao
) {
    fun getAllEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllEntries()

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
