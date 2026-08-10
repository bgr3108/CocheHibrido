package com.bgr3108.kilonom.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.bgr3108.kilonom.data.Car
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {
    @Query("SELECT * FROM car WHERE id = 1 LIMIT 1")
    fun getCar(): Flow<Car?>

    @Query("SELECT * FROM car WHERE id = 1 LIMIT 1")
    suspend fun getCarOnce(): Car?

    @Upsert
    suspend fun upsertCar(car: Car)

    @Query("DELETE FROM car")
    suspend fun deleteAll()
}
