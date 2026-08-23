package com.bgr3108.kilonom.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bgr3108.kilonom.data.FuelEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelEntryDao {

    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY fecha DESC")
    fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries WHERE id = :entryId AND vehicleId = :vehicleId LIMIT 1")
    suspend fun getEntryForVehicle(entryId: Int, vehicleId: Long): FuelEntry?

    @Query("SELECT km FROM fuel_entries WHERE vehicleId = :vehicleId")
    suspend fun getKilometersForVehicle(vehicleId: Long): List<Double>

    @Query("SELECT COUNT(*) FROM fuel_entries WHERE vehicleId = :vehicleId")
    suspend fun countEntries(vehicleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FuelEntry)

    @Update
    suspend fun updateEntry(entry: FuelEntry): Int

    @Query("DELETE FROM fuel_entries WHERE id = :entryId AND vehicleId = :vehicleId")
    suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int

    @Query("DELETE FROM fuel_entries")
    suspend fun deleteAll()
}
