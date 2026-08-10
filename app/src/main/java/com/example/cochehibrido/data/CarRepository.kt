package com.example.cochehibrido.data

import com.example.cochehibrido.database.CarDao
import kotlinx.coroutines.flow.Flow

class CarRepository(
    private val carDao: CarDao
) {
    fun getCar(): Flow<Car?> = carDao.getCar()

    suspend fun saveCar(car: Car) {
        carDao.upsertCar(car)
    }

    suspend fun deleteAll() {
        carDao.deleteAll()
    }
}
