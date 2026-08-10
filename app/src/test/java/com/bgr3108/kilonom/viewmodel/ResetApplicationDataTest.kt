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
        val vehicleRepository = VehicleRepository(EmptyVehicleCatalog, vehiclePreferences)

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
        val originalVehicle = Vehicle(type = VehicleType.GASOLINA)
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao()
        val vehicleRepository = VehicleRepository(
            EmptyVehicleCatalog,
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
        assertEquals(originalVehicle, vehicleRepository.vehicle.value)
    }

    @Test
    fun roomFailure_doesNotClearVehiclePreferences() = runBlocking {
        val originalVehicle = Vehicle(type = VehicleType.ELECTRICO)
        val vehiclePreferences = FakeVehiclePreferences(vehicle = originalVehicle)
        val vehicleRepository = VehicleRepository(EmptyVehicleCatalog, vehiclePreferences)

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
        assertEquals(originalVehicle, vehicleRepository.vehicle.value)
    }

    @Test
    fun legacyCarRoomFailure_doesNotClearVehiclePreferences() = runBlocking {
        val originalVehicle = Vehicle(type = VehicleType.GASOLINA)
        val vehiclePreferences = FakeVehiclePreferences(vehicle = originalVehicle)
        val vehicleRepository = VehicleRepository(EmptyVehicleCatalog, vehiclePreferences)

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
        assertEquals(originalVehicle, vehicleRepository.vehicle.value)
    }

    @Test
    fun retryAfterLegacyCarRoomFailure_completesResetSafely() = runBlocking {
        val fuelEntryDao = FakeFuelEntryDao()
        val carDao = FakeCarDao(deleteError = IllegalStateException("Car unavailable"))
        val vehiclePreferences = FakeVehiclePreferences()
        val vehicleRepository = VehicleRepository(EmptyVehicleCatalog, vehiclePreferences)

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

    private class FakeVehiclePreferences(
        private var vehicle: Vehicle = Vehicle(type = VehicleType.GASOLINA),
        private val clearError: Exception? = null
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
                km = 1.0
            )
        )

        override fun getAllEntries(): Flow<List<FuelEntry>> = flowOf(entries)

        override fun getLatestEntry(): Flow<FuelEntry?> = flowOf(entries.lastOrNull())

        override suspend fun insertEntry(entry: FuelEntry) = Unit

        override suspend fun delete(entry: FuelEntry) = Unit

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
