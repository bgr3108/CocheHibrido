package com.example.cochehibrido.data

import com.example.cochehibrido.database.FuelEntryDao
import kotlinx.coroutines.flow.Flow

class FuelRepository(
    private val fuelEntryDao: FuelEntryDao
) {
    fun getAllEntries(): Flow<List<FuelEntry>> = fuelEntryDao.getAllEntries()

    fun getLatestEntry(): Flow<FuelEntry?> = fuelEntryDao.getLatestEntry()

    suspend fun addEntry(entry: FuelEntry) {
        fuelEntryDao.insertEntry(entry)
    }

    // 🔥 AÑADE ESTO
    suspend fun delete(entry: FuelEntry) {
        fuelEntryDao.delete(entry)
    }
}