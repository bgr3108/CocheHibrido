package com.example.cochehibrido.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
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

    private object EmptyVehicleCatalog : VehicleCatalog {
        override fun loadVehicles(): List<VehicleInfo> = emptyList()
    }

    private class FakeVehiclePreferences(
        private val vehicle: Vehicle = Vehicle(),
        private val loadError: Exception? = null
    ) : VehiclePreferencesStore {

        override suspend fun saveVehicle(vehicle: Vehicle) = Unit

        override suspend fun loadVehicle(): Vehicle {
            loadError?.let { throw it }
            return vehicle
        }

        override suspend fun clearVehicle() = Unit
    }
}
