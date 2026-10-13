package com.bgr3108.kilonom.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.shouldShowSetup
import com.bgr3108.kilonom.viewmodel.ResetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class VehicleStartupRecoveryTest {
    @Test
    fun sqliteReadFailureCanBeRetriedWithoutChangingStoredPersonalData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val vehicle = Vehicle(brand = "Prueba", model = "Modelo", year = 2026, type = VehicleType.GASOLINA, initialKm = 1_000.0)
                .toEntity(id = 1, createdAt = 1)
            val entry = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = 1)
            database.vehicleDao().insert(vehicle)
            database.fuelEntryDao().insertEntry(entry)
            val dao = FailingReadDao(database.vehicleDao())
            val preferences = MemoryPreferences()
            val repository = VehicleRepository(EmptyCatalog, preferences, dao, database.fuelEntryDao(), database.maintenanceDao(), scope)

            val failure = withTimeout(5.seconds) { repository.loadState.first { it is VehicleLoadState.Error } }
            assertTrue((failure as VehicleLoadState.Error).cause is SQLiteException)
            assertFalse(shouldShowSetup(failure, ResetState.IDLE))
            assertEquals(0, preferences.writes)
            assertEquals(vehicle, database.vehicleDao().getVehicle(1))
            assertEquals(entry, database.fuelEntryDao().getEntryForVehicle(1, 1))

            dao.failRead = false
            repository.retryVehicleLoad()
            val loaded = withTimeout(5.seconds) { repository.loadState.first { it is VehicleLoadState.Ready } }
            assertEquals(VehicleLoadState.Ready(vehicle), loaded)
            assertEquals(vehicle, repository.activeVehicle.value)
            assertEquals(listOf(vehicle), repository.vehicleSummaries.value.map { it.vehicle })
            assertEquals(entry, database.fuelEntryDao().getEntryForVehicle(1, 1))
        } finally {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
            database.close()
        }
    }

    private class FailingReadDao(private val delegate: VehicleDao) : VehicleDao by delegate {
        @Volatile var failRead = true
        override suspend fun getFirstVehicle(): VehicleEntity? {
            if (failRead) throw SQLiteException("Simulated initial read failure")
            return delegate.getFirstVehicle()
        }
    }

    private class MemoryPreferences : VehiclePreferencesStore {
        var writes = 0
        override suspend fun loadVehicle() = Vehicle()
        override suspend fun loadActiveVehicleId() = 1L
        override suspend fun lastSeenReleaseNotesVersion() = RELEASE_NOTES_VERSION
        override suspend fun hasStoredAppState() = true
        override suspend fun saveVehicle(vehicle: Vehicle) { writes++ }
        override suspend fun saveActiveVehicleId(vehicleId: Long) { writes++ }
        override suspend fun clearVehicle() { writes++ }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
}
