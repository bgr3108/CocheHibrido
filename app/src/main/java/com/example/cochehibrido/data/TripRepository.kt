package com.example.cochehibrido.data

import com.example.cochehibrido.database.TripDao
import kotlinx.coroutines.flow.Flow

class TripRepository(
    private val tripDao: TripDao
) {

    fun getAllTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips()

    suspend fun insertTrip(trip: Trip) {
        tripDao.insert(trip)
    }

    // 🔥 BORRAR (nombre consistente)
    suspend fun deleteTrip(trip: Trip) {
        tripDao.delete(trip)
    }
}