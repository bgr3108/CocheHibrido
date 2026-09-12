package com.bgr3108.kilonom.data

import android.content.Context
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.stations.StationCacheDatabase
import com.bgr3108.kilonom.stations.StationRepository

class AppContainer(context: Context) {

    private val database = HybridCarDatabase.getDatabase(context)
    private val stationCacheDatabase = StationCacheDatabase.getDatabase(context)

    val carRepository: CarRepository by lazy {
        CarRepository(database.carDao())
    }

    val fuelRepository: FuelRepository by lazy {
        FuelRepository(database.fuelEntryDao())
    }

    val vehicleRepository = VehicleRepository(
        VehicleDataSource(context),
        VehiclePreferences(context),
        database.vehicleDao(),
        database.fuelEntryDao(),
        database.maintenanceDao()
    )

    /** Shared vehicle-scoped maintenance boundary, consumed by the Phase 2 UI. */
    val maintenanceRepository: MaintenanceRepository by lazy {
        MaintenanceRepository(
            database = database,
            maintenanceDao = database.maintenanceDao(),
            vehicleRepository = vehicleRepository
        )
    }

    val stationRepository: StationRepository by lazy {
        StationRepository(stationCacheDatabase, stationCacheDatabase.stationCacheDao())
    }

}
