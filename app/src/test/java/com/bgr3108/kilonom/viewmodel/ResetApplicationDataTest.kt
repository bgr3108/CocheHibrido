package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelRepository
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.Car
import com.bgr3108.kilonom.data.CarRepository
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleCatalog
import com.bgr3108.kilonom.data.VehicleInfo
import com.bgr3108.kilonom.data.VehiclePreferencesStore
import com.bgr3108.kilonom.data.VehicleRepository
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.InMemoryFuelEntryDao
import com.bgr3108.kilonom.data.InMemoryMaintenanceDao
import com.bgr3108.kilonom.data.InMemoryVehicleDao
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.CarDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetApplicationDataTest {

    @Test
    fun successfulReset_clearsAllRoomDataAndVehicle() = runBlocking {
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao()
        val vehiclePreferences = FakeVehiclePreferences()
        val vehicleRepository = vehicleRepository(vehiclePreferences)

        vehicleRepository.isLoading.first { !it }

        resetApplicationData(
            FuelRepository(fuelEntryDao),
            CarRepository(carDao),
            vehicleRepository
        )

        assertTrue(fuelEntryDao.entries.isEmpty())
        assertTrue(carDao.cars.isEmpty())
        assertEquals(Vehicle(), vehicleRepository.vehicle.value)
    }

    @Test
    fun dataStoreFailure_doesNotReportASuccessfulReset() = runBlocking {
        val originalVehicle = configuredVehicle(VehicleType.GASOLINA)
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao()
        val vehicleRepository = vehicleRepository(
            FakeVehiclePreferences(
                vehicle = originalVehicle,
                clearError = IllegalStateException("DataStore unavailable")
            )
        )

        vehicleRepository.isLoading.first { !it }

        val result = runCatching {
            resetApplicationData(
                FuelRepository(fuelEntryDao),
                CarRepository(carDao),
                vehicleRepository
            )
        }

        assertTrue(result.isFailure)
        assertEquals(originalVehicle.brand, vehicleRepository.vehicle.value.brand)
        assertEquals(originalVehicle.type, vehicleRepository.vehicle.value.type)
    }

    @Test
    fun retryAfterDataStoreFailure_completesResetSafely() = runBlocking {
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao()
        val preferences = FakeVehiclePreferences(
            clearError = IllegalStateException("DataStore unavailable")
        )
        val vehicleRepository = vehicleRepository(preferences)

        vehicleRepository.isLoading.first { !it }

        val firstAttempt = runCatching {
            resetApplicationData(FuelRepository(fuelEntryDao), CarRepository(carDao), vehicleRepository)
        }
        preferences.clearError = null

        resetApplicationData(FuelRepository(fuelEntryDao), CarRepository(carDao), vehicleRepository)

        assertTrue(firstAttempt.isFailure)
        assertTrue(fuelEntryDao.entries.isEmpty())
        assertTrue(carDao.cars.isEmpty())
        assertEquals(Vehicle(), vehicleRepository.vehicle.value)
    }

    @Test
    fun successfulReset_cascadesMaintenanceWithTheVehicle() = runBlocking {
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(
                MaintenanceItemEntity(
                    id = 1,
                    vehicleId = 1,
                    type = MaintenanceType.ITV,
                    trackingKey = "ITV",
                    createdAt = 1,
                    updatedAt = 1
                )
            ),
            records = mutableListOf(
                MaintenanceRecordEntity(id = 1, itemId = 1, odometerKm = 1_200, createdAt = 1, updatedAt = 1)
            )
        )
        val vehicleDao = InMemoryVehicleDao(
            initialVehicles = listOf(
                com.bgr3108.kilonom.data.VehicleEntity(
                    id = 1,
                    category = VehicleCategory.COCHE,
                    brand = "Marca",
                    model = "Modelo",
                    year = 2026,
                    type = VehicleType.GASOLINA,
                    fuelTankCapacity = 40.0,
                    batteryCapacity = 0.0,
                    initialKm = 1_000.0,
                    createdAt = 1
                )
            ),
            onVehicleDeleted = maintenanceDao::cascadeVehicleDelete
        )
        val preferences = FakeVehiclePreferences()
        val vehicleRepository = VehicleRepository(
            EmptyVehicleCatalog,
            preferences,
            vehicleDao,
            InMemoryFuelEntryDao(),
            maintenanceDao
        )
        vehicleRepository.isLoading.first { !it }

        resetApplicationData(FuelRepository(InMemoryFuelEntryDao()), CarRepository(FakeCarDao()), vehicleRepository)

        assertTrue(maintenanceDao.items.isEmpty())
        assertTrue(maintenanceDao.records.isEmpty())
        assertEquals(Vehicle(), vehicleRepository.vehicle.value)
    }

    @Test
    fun roomFailure_doesNotClearVehiclePreferences() = runBlocking {
        val originalVehicle = configuredVehicle(VehicleType.ELECTRICO)
        val vehiclePreferences = FakeVehiclePreferences(vehicle = originalVehicle)
        val vehicleRepository = vehicleRepository(vehiclePreferences)

        vehicleRepository.isLoading.first { !it }

        val result = runCatching {
            resetApplicationData(
                FuelRepository(FakeFuelEntryDao(deleteError = IllegalStateException("Room unavailable"))),
                CarRepository(FakeCarDao()),
                vehicleRepository
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, vehiclePreferences.clearCalls)
        assertEquals(originalVehicle.brand, vehicleRepository.vehicle.value.brand)
        assertEquals(originalVehicle.type, vehicleRepository.vehicle.value.type)
    }

    @Test
    fun legacyCarRoomFailure_doesNotClearVehiclePreferences() = runBlocking {
        val originalVehicle = configuredVehicle(VehicleType.GASOLINA)
        val vehiclePreferences = FakeVehiclePreferences(vehicle = originalVehicle)
        val vehicleRepository = vehicleRepository(vehiclePreferences)

        vehicleRepository.isLoading.first { !it }

        val result = runCatching {
            resetApplicationData(
                FuelRepository(FakeFuelEntryDao()),
                CarRepository(FakeCarDao(deleteError = IllegalStateException("Car unavailable"))),
                vehicleRepository
            )
        }

        assertTrue(result.isFailure)
        assertEquals(0, vehiclePreferences.clearCalls)
        assertEquals(originalVehicle.brand, vehicleRepository.vehicle.value.brand)
        assertEquals(originalVehicle.type, vehicleRepository.vehicle.value.type)
    }

    @Test
    fun retryAfterLegacyCarRoomFailure_completesResetSafely() = runBlocking {
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao(deleteError = IllegalStateException("Car unavailable"))
        val vehiclePreferences = FakeVehiclePreferences()
        val vehicleRepository = vehicleRepository(vehiclePreferences)

        vehicleRepository.isLoading.first { !it }

        val firstAttempt = runCatching {
            resetApplicationData(
                FuelRepository(fuelEntryDao),
                CarRepository(carDao),
                vehicleRepository
            )
        }

        carDao.deleteError = null

        resetApplicationData(
            FuelRepository(fuelEntryDao),
            CarRepository(carDao),
            vehicleRepository
        )

        assertTrue(firstAttempt.isFailure)
        assertTrue(fuelEntryDao.entries.isEmpty())
        assertTrue(carDao.cars.isEmpty())
        assertEquals(Vehicle(), vehicleRepository.vehicle.value)
    }

    private object EmptyVehicleCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
    }

    private fun vehicleRepository(preferences: FakeVehiclePreferences) = VehicleRepository(
        EmptyVehicleCatalog,
        preferences,
        InMemoryVehicleDao(),
        InMemoryFuelEntryDao(),
        InMemoryMaintenanceDao()
    )

    private fun configuredVehicle(type: VehicleType) = Vehicle(
        brand = "Marca",
        model = "Modelo",
        year = 2026,
        type = type
    )

    private class FakeVehiclePreferences(
        private var vehicle: Vehicle = Vehicle(
            brand = "Marca",
            model = "Modelo",
            year = 2026,
            type = VehicleType.GASOLINA
        ),
        var clearError: Exception? = null
    ) : VehiclePreferencesStore {
        var clearCalls = 0

        override suspend fun saveVehicle(vehicle: Vehicle) {
            this.vehicle = vehicle
        }

        override suspend fun loadVehicle(): Vehicle = vehicle

        override suspend fun clearVehicle() {
            clearCalls += 1
            clearError?.let { throw it }
            vehicle = Vehicle()
        }
    }

    private class FakeFuelEntryDao(
        private val deleteError: Exception? = null
    ) : FuelEntryDao {
        val entries = mutableListOf(
            FuelEntry(
                fecha = 0L,
                cantidad = 1.0,
                precio = 1.0,
                tipo = FuelType.GASOLINA,
                km = 1.0,
                vehicleId = 1L
            )
        )

        override fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>> =
            flowOf(entries.filter { it.vehicleId == vehicleId })

        override suspend fun getEntryForVehicle(entryId: Int, vehicleId: Long): FuelEntry? =
            entries.find { it.id == entryId && it.vehicleId == vehicleId }

        override suspend fun getKilometersForVehicle(vehicleId: Long): List<Double> =
            entries.filter { it.vehicleId == vehicleId }.map { it.km }

        override suspend fun countEntries(vehicleId: Long): Int =
            entries.count { it.vehicleId == vehicleId }

        override suspend fun insertEntry(entry: FuelEntry) = Unit

        override suspend fun updateEntry(entry: FuelEntry): Int = 1

        override suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int = 1

        override suspend fun deleteAll() {
            deleteError?.let { throw it }
            entries.clear()
        }
    }

    private class FakeCarDao(
        var deleteError: Exception? = null
    ) : CarDao {
        val cars = mutableListOf(
            Car(
                marca = "Marca",
                modelo = "Modelo",
                matricula = "1234ABC",
                kmActuales = 100
            )
        )

        override fun getCar(): Flow<Car?> = flowOf(cars.firstOrNull())

        override suspend fun getCarOnce(): Car? = cars.firstOrNull()

        override suspend fun upsertCar(car: Car) {
            cars.clear()
            cars += car
        }

        override suspend fun deleteAll() {
            deleteError?.let { throw it }
            cars.clear()
        }
    }
}
