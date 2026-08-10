package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleRepositoryTest {

    @Test
    fun validStoredType_isParsed() {
        assertEquals(VehicleType.GASOLINA, vehicleTypeOrNull("GASOLINA"))
    }

    @Test
    fun unknownStoredType_isTreatedAsNotConfigured() {
        assertNull(vehicleTypeOrNull("UNKNOWN_TYPE"))
    }

    @Test
    fun absentStoredType_isTreatedAsNotConfigured() {
        assertNull(vehicleTypeOrNull(null))
    }

    @Test
    fun absentStoredCategory_isTreatedAsCarForExistingInstallations() {
        assertEquals(VehicleCategory.COCHE, vehicleCategoryOrDefault(null))
    }

    @Test
    fun storedMotorcycleCategory_isParsed() {
        assertEquals(VehicleCategory.MOTO, vehicleCategoryOrDefault("MOTO"))
    }

    @Test
    fun loadFailure_isExposed() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences(loadError = error)
        )

        repository.isLoading.first { !it }

        assertSame(error, repository.loadError.value)
    }

    @Test
    fun loadFailure_keepsAnEmptyVehicle() = runBlocking {
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences(loadError = IllegalStateException())
        )

        repository.isLoading.first { !it }

        assertEquals(Vehicle(), repository.vehicle.value)
    }

    @Test
    fun loadingEndsAfterAReadFailure() = runBlocking {
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences(loadError = IllegalStateException())
        )

        repository.isLoading.first { !it }

        assertFalse(repository.isLoading.value)
    }

    @Test
    fun loadingEndsAfterASuccessfulRead() = runBlocking {
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences(vehicle = Vehicle(type = VehicleType.ELECTRICO))
        )

        repository.isLoading.first { !it }

        assertFalse(repository.isLoading.value)
    }

    @Test
    fun successfulSave_updatesVehicleAfterPreferencesAreWritten() = runBlocking {
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences()
        )
        val vehicle = Vehicle(type = VehicleType.ELECTRICO, currentKm = 12.5)

        repository.isLoading.first { !it }
        repository.saveVehicle(vehicle)

        assertEquals(vehicle, repository.vehicle.value)
    }

    @Test
    fun savingCar_persistsCarCategory() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val repository = VehicleRepository(EmptyVehicleCatalog, preferences)
        val vehicle = Vehicle(category = VehicleCategory.COCHE)

        repository.isLoading.first { !it }
        repository.saveVehicle(vehicle)

        assertEquals(VehicleCategory.COCHE, preferences.savedVehicle?.category)
    }

    @Test
    fun savingMotorcycle_persistsMotorcycleCategory() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val repository = VehicleRepository(EmptyVehicleCatalog, preferences)
        val vehicle = Vehicle(category = VehicleCategory.MOTO)

        repository.isLoading.first { !it }
        repository.saveVehicle(vehicle)

        assertEquals(VehicleCategory.MOTO, preferences.savedVehicle?.category)
    }

    @Test
    fun failedSave_keepsThePreviouslyLoadedVehicle() = runBlocking {
        val previousVehicle = Vehicle(type = VehicleType.GASOLINA, currentKm = 10.0)
        val repository = VehicleRepository(
            EmptyVehicleCatalog,
            FakeVehiclePreferences(
                vehicle = previousVehicle,
                saveError = IllegalStateException("DataStore unavailable")
            )
        )

        repository.isLoading.first { !it }

        val result = runCatching {
            repository.saveVehicle(Vehicle(type = VehicleType.ELECTRICO))
        }

        assertTrue(result.isFailure)
        assertEquals(previousVehicle, repository.vehicle.value)
    }

    private object EmptyVehicleCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
    }

    private class FakeVehiclePreferences(
        private val vehicle: Vehicle = Vehicle(),
        private val loadError: Exception? = null,
        private val saveError: Exception? = null
    ) : VehiclePreferencesStore {

        var savedVehicle: Vehicle? = null

        override suspend fun saveVehicle(vehicle: Vehicle) {
            saveError?.let { throw it }
            savedVehicle = vehicle
        }

        override suspend fun loadVehicle(): Vehicle {
            loadError?.let { throw it }
            return vehicle
        }

        override suspend fun clearVehicle() = Unit
    }
}
