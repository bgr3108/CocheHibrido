package com.bgr3108.kilonom.data

import android.content.Context
import com.bgr3108.kilonom.database.HybridCarDatabase

class AppContainer(context: Context) {

    private val database = HybridCarDatabase.getDatabase(context)

    val carRepository: CarRepository by lazy {
        CarRepository(database.carDao())
    }

    val fuelRepository: FuelRepository by lazy {
        FuelRepository(database.fuelEntryDao())
    }

    val vehicleRepository = VehicleRepository(
        VehicleDataSource(context),
        VehiclePreferences(context)
    )
}
