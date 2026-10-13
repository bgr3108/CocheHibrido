package com.bgr3108.kilonom.data

import android.database.sqlite.SQLiteException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.ResetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/** Tests use Room in memory and temporary DataStore files, never real user data. */
@RunWith(AndroidJUnit4::class)
class PersonalDataResetRecoveryTest {
    @Test
    fun completedResetRemovesAllPersonalDataAndPreservesUnrelatedPreferences() = runBlocking {
        withFixture { f ->
            f.repository.clearVehicle()
            f.assertRoomEmpty()
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            assertNull(f.preferences.loadActiveVehicleId())
            assertEquals("keep", f.unrelated.data.first()[SENTINEL])
            f.restart()
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
            f.assertRoomEmpty()
            assertEquals("keep", f.unrelated.data.first()[SENTINEL])
        }
    }

    @Test
    fun dataStoreFailureBeforeRoomPreservesEveryPersonalRowAndCanRetry() = runBlocking {
        withFixture { f ->
            f.preferences.clearError = IOException("simulated storage failure")
            try { f.repository.clearVehicle(); fail("Expected IOException") }
            catch (_: IOException) { }
            f.assertRoomIntact()
            assertEquals(LEGACY, f.preferences.loadVehicle())
            assertEquals(0, f.roomCalls)
            f.preferences.clearError = null
            f.repository.clearVehicle()
            f.assertRoomEmpty()
        }
    }

    @Test
    fun roomFailureAfterPartialDeletesRollsBackAndRestartDoesNotResumeDeletion() = runBlocking {
        withFixture { f ->
            f.roomAction = {
                f.database.withTransaction {
                    f.database.fuelEntryDao().deleteAll()
                    f.database.carDao().deleteAll()
                    throw SQLiteException("simulated failure before vehicle deletion")
                }
            }
            try { f.repository.clearVehicle(); fail("Expected SQLiteException") }
            catch (_: SQLiteException) { }
            f.assertRoomIntact()
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.completedLoad())
            f.assertRoomIntact()
            // The next startup only reloads retained rows. Deletion requires a new user request.
            assertEquals(1, f.roomCalls)
            f.roomAction = f.database::clearPersonalData
            f.repository.clearVehicle()
            f.assertRoomEmpty()
        }
    }

    @Test
    fun cancellationInsideRoomRollsBackAndPropagatesWithLegacyAlreadyCleared() = runBlocking {
        withFixture { f ->
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.roomAction = {
                f.database.withTransaction {
                    f.database.fuelEntryDao().deleteAll()
                    entered.complete(Unit)
                    finish.await()
                    f.database.vehicleDao().deleteAll()
                }
            }
            val reset = async { f.repository.clearVehicle() }
            awaitReset { entered.await() }
            reset.cancelAndJoin()
            assertTrue(reset.isCancelled)
            f.assertRoomIntact()
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.completedLoad())
            f.assertRoomIntact()
        }
    }

    @Test
    fun interruptionBetweenStoresKeepsRoomAndCannotRecreateADuplicateLegacyVehicle() = runBlocking {
        withFixture { f ->
            f.preferences.cancelAfterClear = true
            val reset = async { f.repository.clearVehicle() }
            reset.join()
            assertTrue(reset.isCancelled)
            assertEquals(0, f.roomCalls)
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.assertRoomIntact()
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.completedLoad())
            assertEquals(listOf(VEHICLE), f.database.vehicleDao().observeAllVehicles().first())
        }
    }

    @Test
    fun interruptionAfterRoomCommitCannotResurrectLegacyVehicleOnRestart() = runBlocking {
        withFixture { f ->
            f.roomAction = { f.database.clearPersonalData(); throw CancellationException() }
            val reset = async { f.repository.clearVehicle() }
            reset.join()
            assertTrue(reset.isCancelled)
            f.assertRoomEmpty()
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.restart()
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
            f.assertRoomEmpty()
        }
    }

    @Test
    fun homeResetReportsFailureAllowsRetryAndRejectsConcurrentRequests() = runBlocking {
        withFixture { f ->
            val vm = f.homeViewModel()
            f.preferences.clearError = IOException()
            onResetMain { vm.resetApplication() }
            awaitReset { vm.resetState.first { it == ResetState.ERROR } }
            f.assertRoomIntact()
            f.preferences.clearError = null
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.roomAction = { entered.complete(Unit); finish.await(); f.database.clearPersonalData() }
            onResetMain { vm.resetApplication() }
            awaitReset { entered.await() }
            assertEquals(ResetState.LOADING, vm.resetState.value)
            onResetMain { vm.resetApplication() }
            assertEquals(1, f.roomCalls)
            finish.complete(Unit)
            awaitReset { vm.resetState.first { it == ResetState.IDLE } }
            assertEquals(VehicleLoadState.Empty, f.completedLoad())
            f.assertRoomEmpty()
        }
    }

    @Test
    fun homeCancellationRestoresVisualStateWithoutReportingSuccessAndAllowsRetry() = runBlocking {
        withFixture { f ->
            val vm = f.homeViewModel()
            f.preferences.cancelAfterClear = true
            onResetMain { vm.resetApplication() }
            awaitReset { vm.resetState.first { it == ResetState.ERROR } }
            f.assertRoomIntact()
            assertEquals(0, f.roomCalls)
            f.preferences.cancelAfterClear = false
            onResetMain { vm.resetApplication() }
            awaitReset { vm.resetState.first { it == ResetState.IDLE } }
            f.assertRoomEmpty()
        }
    }

    private suspend fun withFixture(test: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = withContext(Dispatchers.IO) { Files.createTempDirectory(context.cacheDir.toPath(), "reset-test-").toFile() }
        val fixture = Fixture(directory)
        try { fixture.seed(); test(fixture) }
        finally {
            fixture.close()
            withContext(Dispatchers.IO) { check(directory.deleteRecursively()) }
        }
    }

    private class Fixture(private val directory: File) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        private var preferencesScope = newScope()
        private var repositoryScope = newScope()
        private val unrelatedScope = newScope()
        val unrelated = PreferenceDataStoreFactory.create(scope = unrelatedScope, produceFile = { File(directory, "unrelated.preferences_pb") })
        private val viewModels = ViewModelStore()
        private var realPreferences = createPreferences()
        var preferences = ResetPreferences(realPreferences)
            private set
        var roomAction: suspend () -> Unit = database::clearPersonalData
        var roomCalls = 0
        lateinit var repository: VehicleRepository
            private set

        private fun createPreferences() = VehiclePreferences(PreferenceDataStoreFactory.create(
            scope = preferencesScope, produceFile = { File(directory, "vehicle.preferences_pb") }
        ))

        suspend fun seed() {
            database.vehicleDao().insert(VEHICLE)
            database.fuelEntryDao().insertEntry(ENTRY)
            database.carDao().upsertCar(CAR)
            database.maintenanceDao().insertItem(ITEM)
            database.maintenanceDao().insertRecord(RECORD)
            realPreferences.saveVehicle(LEGACY)
            realPreferences.saveActiveVehicleId(1)
            realPreferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
            unrelated.edit { it[SENTINEL] = "keep" }
            repository = createRepository()
            completedLoad()
        }

        private fun createRepository() = VehicleRepository(
            EmptyCatalog, preferences, database.vehicleDao(), database.fuelEntryDao(), database.maintenanceDao(), repositoryScope,
            clearPersonalRoomData = { roomCalls++; roomAction() }
        )

        suspend fun completedLoad() = awaitReset { repository.loadState.first { it != VehicleLoadState.Loading } }
        suspend fun restart() {
            onResetMain { viewModels.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            repositoryScope = newScope()
            preferencesScope = newScope()
            realPreferences = createPreferences()
            preferences = ResetPreferences(realPreferences)
            repository = createRepository()
        }

        fun homeViewModel() = onResetMain {
            HomeViewModel(FuelRepository(database.fuelEntryDao()), repository).also { viewModels.put("home", it) }
        }

        suspend fun assertRoomIntact() {
            assertEquals(listOf(VEHICLE), database.vehicleDao().observeAllVehicles().first())
            assertEquals(ENTRY, database.fuelEntryDao().getEntryForVehicle(1, 1))
            assertEquals(CAR, database.carDao().getCarOnce())
            assertEquals(ITEM, database.maintenanceDao().getItemForVehicle(1, 1))
            assertEquals(RECORD, database.maintenanceDao().getRecordForVehicle(1, 1)?.toDomain()?.record)
        }

        suspend fun assertRoomEmpty() {
            assertTrue(database.vehicleDao().observeAllVehicles().first().isEmpty())
            assertNull(database.fuelEntryDao().getEntryForVehicle(1, 1))
            assertNull(database.carDao().getCarOnce())
            assertEquals(0, database.maintenanceDao().countItemsForVehicle(1))
            assertEquals(0, database.maintenanceDao().countRecordsForVehicle(1))
        }

        suspend fun close() {
            onResetMain { viewModels.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(unrelatedScope.coroutineContext[Job]).cancelAndJoin()
            database.close()
        }
    }

    private class ResetPreferences(private val delegate: VehiclePreferencesStore) : VehiclePreferencesStore by delegate {
        var clearError: IOException? = null
        var cancelAfterClear = false
        override suspend fun clearVehicle() {
            clearError?.let { throw it }
            delegate.clearVehicle()
            if (cancelAfterClear) throw CancellationException()
        }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
    private companion object {
        val SENTINEL = stringPreferencesKey("unrelated")
        val LEGACY = Vehicle(brand = "Prueba", model = "Modelo", year = 2026, type = VehicleType.GASOLINA, initialKm = 1_000.0)
        val VEHICLE = LEGACY.toEntity(id = 1, createdAt = 1)
        val ENTRY = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = 1)
        val CAR = Car(marca = "Prueba", modelo = "Modelo", matricula = "TEST", kmActuales = 1_200)
        val ITEM = MaintenanceItemEntity(id = 1, vehicleId = 1, type = MaintenanceType.ITV, trackingKey = "ITV", createdAt = 1, updatedAt = 1)
        val RECORD = MaintenanceRecordEntity(id = 1, itemId = 1, odometerKm = 1_200, createdAt = 1, updatedAt = 1)
        fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private suspend fun <T> awaitReset(block: suspend () -> T): T = withTimeout(5.seconds) { block() }

private fun <T> onResetMain(action: () -> T): T {
    val result = AtomicReference<T>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
    return result.get()
}
