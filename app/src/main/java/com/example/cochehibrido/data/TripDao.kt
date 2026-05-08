package com.example.cochehibrido.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.cochehibrido.data.Trip
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    // 🔥 CORREGIDO → nombre tabla
    @Query("SELECT * FROM trips ORDER BY fecha DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}