package com.example.cochehibrido.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import com.example.cochehibrido.data.FuelEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelEntryDao {

    @Query("SELECT * FROM fuel_entries ORDER BY id DESC")
    fun getAllEntries(): Flow<List<FuelEntry>>

    @Query("SELECT * FROM fuel_entries ORDER BY id DESC LIMIT 1")
    fun getLatestEntry(): Flow<FuelEntry?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: FuelEntry)

    // 🔥 AÑADE ESTO
    @Delete
    suspend fun delete(entry: FuelEntry)
}