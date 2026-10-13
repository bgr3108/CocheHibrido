package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class ResetApplicationDataTest {
    @Test
    fun successfulReset_clearsAllRoomDataAndVehicle() = runBlocking {
        withFixture { f ->
            resetApplicationData(f.repository)
            assertTrue(f.vehicles.vehicles.isEmpty())
            assertTrue(f.entries.entries.isEmpty())
            assertFalse(f.hasLegacyCar)
            assertEquals(Vehicle(), f.preferences.vehicle)
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
        }
    }

    @Test
    fun dataStoreFailure_doesNotReportSuccessOrDeleteRoom() = runBlocking {
        withFixture { f ->
            f.preferences.clearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.assertPersonalDataPresent()
            assertEquals(0, f.roomClearCalls)
            assertEquals(ORIGINAL, f.preferences.vehicle)
        }
    }

    @Test
    fun retryAfterDataStoreFailure_completesResetSafely() = runBlocking {
        withFixture { f ->
            f.preferences.clearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.preferences.clearError = null
            resetApplicationData(f.repository)
            assertTrue(f.vehicles.vehicles.isEmpty())
            assertEquals(1, f.roomClearCalls)
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
        }
    }

    @Test
    fun successfulReset_cascadesMaintenanceWithTheVehicle() = runBlocking {
        withFixture { f ->
            resetApplicationData(f.repository)
            assertTrue(f.maintenance.items.isEmpty())
            assertTrue(f.maintenance.records.isEmpty())
        }
    }

    @Test
    fun roomFailure_keepsRoomDataButLegacyIsAlreadyCleared() = runBlocking {
        withFixture { f ->
            f.roomClearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.assertPersonalDataPresent()
            assertEquals(Vehicle(), f.preferences.vehicle)
            assertTrue(f.completedLoad() is VehicleLoadState.Ready)
        }
    }

    @Test
    fun roomFailure_doesNotPublishAnEmptyVehicle() = runBlocking {
        withFixture { f ->
            f.roomClearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.completedLoad()
            assertEquals(ORIGINAL.brand, f.repository.vehicle.value.brand)
            assertEquals(ORIGINAL.type, f.repository.vehicle.value.type)
        }
    }

    @Test
    fun retryAfterRoomFailure_completesResetSafely() = runBlocking {
        withFixture { f ->
            f.roomClearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.roomClearError = null
            resetApplicationData(f.repository)
            assertEquals(2, f.roomClearCalls)
            assertTrue(f.vehicles.vehicles.isEmpty())
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
        }
    }

    @Test
    fun legacyClearCommitsBeforeAnyPersonalRoomDeletion() = runBlocking {
        withFixture { f ->
            resetApplicationData(f.repository)
            assertEquals(listOf("legacy", "room"), f.events)
        }
    }

    @Test
    fun cancellationAfterLegacyClearPropagatesAndLeavesRoomUntouched() = runBlocking {
        withFixture { f ->
            f.preferences.cancelAfterClear = true
            try {
                resetApplicationData(f.repository)
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) { }
            f.assertPersonalDataPresent()
            assertEquals(Vehicle(), f.preferences.vehicle)
            assertEquals(0, f.roomClearCalls)
            assertTrue(f.completedLoad() is VehicleLoadState.Ready)
            f.preferences.cancelAfterClear = false
            resetApplicationData(f.repository)
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
        }
    }

    @Test
    fun restartingAfterInterruptedResetKeepsRoomAndDoesNotReimportLegacy() = runBlocking {
        withFixture { f ->
            f.roomClearError = IOException()
            assertFailure { resetApplicationData(f.repository) }
            f.restart()
            assertTrue(f.completedLoad() is VehicleLoadState.Ready)
            assertEquals(1, f.vehicles.vehicles.size)
            assertEquals(Vehicle(), f.preferences.vehicle)
            f.roomClearError = null
            resetApplicationData(f.repository)
            f.restart()
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
            assertTrue(f.vehicles.vehicles.isEmpty())
        }
    }

    private suspend fun assertFailure(action: suspend () -> Unit) {
        try { action(); throw AssertionError("Expected IOException") }
        catch (_: IOException) { }
    }

    private suspend fun withFixture(test: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try { fixture.completedLoad(); test(fixture) }
        finally { fixture.close() }
    }

    private class Fixture {
        val events = mutableListOf<String>()
        val preferences = MemoryPreferences(events)
        val entries = InMemoryFuelEntryDao(mutableListOf(ENTRY))
        val maintenance = InMemoryMaintenanceDao(
            mutableListOf(MaintenanceItemEntity(id = 1, vehicleId = 1, type = MaintenanceType.ITV, trackingKey = "ITV", createdAt = 1, updatedAt = 1)),
            mutableListOf(MaintenanceRecordEntity(id = 1, itemId = 1, createdAt = 1, updatedAt = 1))
        )
        val vehicles = InMemoryVehicleDao(listOf(ORIGINAL.toEntity(id = 1, createdAt = 1)), maintenance::cascadeVehicleDelete)
        var hasLegacyCar = true
        var roomClearCalls = 0
        var roomClearError: Exception? = null
        private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var repository = createRepository()
            private set

        private fun createRepository() = VehicleRepository(EmptyCatalog, preferences, vehicles, entries, maintenance, scope,
            clearPersonalRoomData = {
                roomClearCalls++
                events += "room"
                roomClearError?.let { throw it }
                entries.deleteAll()
                hasLegacyCar = false
                vehicles.deleteAll()
            })

        suspend fun completedLoad() = withTimeout(5.seconds) { repository.loadState.first { it != VehicleLoadState.Loading } }
        fun assertPersonalDataPresent() {
            assertEquals(1, vehicles.vehicles.size)
            assertEquals(listOf(ENTRY), entries.entries)
            assertTrue(hasLegacyCar)
            assertEquals(1, maintenance.items.size)
            assertEquals(1, maintenance.records.size)
        }
        suspend fun restart() {
            close()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            repository = createRepository()
        }
        suspend fun close() { requireNotNull(scope.coroutineContext[Job]).cancelAndJoin() }
    }

    private class MemoryPreferences(private val events: MutableList<String>) : VehiclePreferencesStore {
        var vehicle = ORIGINAL
        var clearError: Exception? = null
        var cancelAfterClear = false
        override suspend fun loadVehicle(): Vehicle = vehicle
        override suspend fun saveVehicle(vehicle: Vehicle) { this.vehicle = vehicle }
        override suspend fun clearVehicle() {
            clearError?.let { throw it }
            events += "legacy"
            vehicle = Vehicle()
            if (cancelAfterClear) throw CancellationException()
        }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
    private companion object {
        val ORIGINAL = Vehicle(brand = "Marca", model = "Modelo", year = 2026, type = VehicleType.GASOLINA)
        val ENTRY = FuelEntry(id = 1, fecha = 1, cantidad = 1.0, precio = 1.0, tipo = FuelType.GASOLINA, km = 1.0, vehicleId = 1)
    }
}
