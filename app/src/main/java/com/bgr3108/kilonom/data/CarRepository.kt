package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.CarDao
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
