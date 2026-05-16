package com.example.cochehibrido.data

import android.content.Context
import com.example.cochehibrido.database.HybridCarDatabase

class AppContainer(context: Context) {

    private val database = HybridCarDatabase.getDatabase(context)

    val carRepository: CarRepository by lazy {
        CarRepository(database.carDao())
    }

    val fuelRepository: FuelRepository by lazy {
        FuelRepository(database.fuelEntryDao())
    }

    val tripRepository: TripRepository by lazy {
        TripRepository(database.tripDao())
    }
    val baselineRepository = BaselineRepository(context)

    val vehicleRepository = VehicleRepository()
}