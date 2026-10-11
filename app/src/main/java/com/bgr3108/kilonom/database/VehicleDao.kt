package com.bgr3108.kilonom.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bgr3108.kilonom.data.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC, id ASC")
    fun observeAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeVehicle(id: Long): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicle(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun getFirstVehicle(): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    /** Links catalog provenance without rebuilding or changing the independent personal snapshot. */
    @Query("UPDATE vehicles SET catalogId = :catalogId WHERE id = :vehicleId AND catalogId IS NULL")
    suspend fun setCatalogIdIfMissing(vehicleId: Long, catalogId: String): Int

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles")
    suspend fun deleteAll()
}
