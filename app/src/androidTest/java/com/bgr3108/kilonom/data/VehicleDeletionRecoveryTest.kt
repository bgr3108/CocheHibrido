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
import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
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
import kotlin.time.Duration.Companion.seconds

/** Only isolated Room databases and temporary DataStores are modified by these tests. */
@RunWith(AndroidJUnit4::class)
class VehicleDeletionRecoveryTest {
    @Test
    fun lastVehicleClearsLegacyBeforeDeletingAndCannotReturnAfterRestart() = runBlocking {
        withFixture { f ->
            f.dao.deleteAction = { target ->
                assertEquals(Vehicle(), f.preferences.loadVehicle())
                assertNull(f.preferences.loadActiveVehicleId())
                f.database.vehicleDao().delete(target)
            }
            f.repository.deleteVehicle(1)
            f.assertDeleted(VEHICLE)
            f.assertUnrelatedIntact()
            assertEquals(VehicleLoadState.Empty, f.loaded())
            assertEquals(1, f.dao.deleteCalls)
            f.restart()
            assertEquals(VehicleLoadState.Empty, f.loaded())
            assertTrue(f.database.vehicleDao().observeAllVehicles().first().isEmpty())
            assertEquals(1, f.dao.deleteCalls)
        }
    }

    @Test
    fun deletingActiveVehicleSelectsOldestSurvivorAndPreservesOtherDataAndLegacy() = runBlocking {
        val later = VEHICLE.copy(id = 2, createdAt = 30)
        val oldest = VEHICLE.copy(id = 3, createdAt = 20)
        withFixture(listOf(later, oldest)) { f ->
            f.repository.deleteVehicle(1)
            awaitDeletion { f.repository.activeVehicle.first { it?.id == oldest.id } }
            assertEquals(oldest.id, f.preferences.loadActiveVehicleId())
            assertEquals(LEGACY, f.preferences.loadVehicle())
            assertEquals(0, f.preferences.clearCalls)
            f.assertDeleted(VEHICLE)
            f.assertVehicleIntact(later)
            f.assertVehicleIntact(oldest)
            f.assertUnrelatedIntact()
            f.restart()
            assertEquals(VehicleLoadState.Ready(oldest), f.loaded())
        }
    }

    @Test
    fun deletingInactiveVehicleKeepsActiveVehicleAndPreferences() = runBlocking {
        val other = VEHICLE.copy(id = 2, createdAt = 2)
        withFixture(listOf(other)) { f ->
            f.repository.deleteVehicle(other.id)
            f.assertDeleted(other)
            f.assertVehicleIntact(VEHICLE)
            assertEquals(1L, f.repository.activeVehicleId.value)
            assertEquals(1L, f.preferences.loadActiveVehicleId())
            assertEquals(LEGACY, f.preferences.loadVehicle())
            assertEquals(0, f.preferences.clearCalls)
            f.assertUnrelatedIntact()
        }
    }

    @Test
    fun dataStoreFailurePreventsEveryDeletionAndAllowsRetry() = runBlocking {
        withFixture { f ->
            f.preferences.clearError = IOException("simulated DataStore failure")
            try { f.repository.deleteVehicle(1); fail("Expected IOException") }
            catch (_: IOException) { }
            assertEquals(0, f.dao.deleteCalls)
            f.assertVehicleIntact(VEHICLE)
            f.assertUnrelatedIntact()
            assertEquals(LEGACY, f.preferences.loadVehicle())
            assertEquals(1L, f.preferences.loadActiveVehicleId())
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.repository.loadState.value)
            f.preferences.clearError = null
            f.repository.deleteVehicle(1)
            f.assertDeleted(VEHICLE)
        }
    }

    @Test
    fun roomFailureAfterLegacyCleanupRetainsRowsAcrossRestartAndAllowsRetry() = runBlocking {
        withFixture { f ->
            f.dao.deleteAction = { throw SQLiteException("simulated Room failure") }
            try { f.repository.deleteVehicle(1); fail("Expected SQLiteException") }
            catch (_: SQLiteException) { }
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.assertVehicleIntact(VEHICLE)
            f.assertUnrelatedIntact()
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.loaded())
            assertEquals(1, f.dao.deleteCalls)
            f.dao.deleteAction = f.database.vehicleDao()::delete
            f.repository.deleteVehicle(1)
            f.assertDeleted(VEHICLE)
        }
    }

    @Test
    fun roomRollbackPreservesVehicleAndCascadedHistory() = runBlocking {
        withFixture { f ->
            f.dao.deleteAction = { target ->
                f.database.withTransaction {
                    f.database.vehicleDao().delete(target)
                    throw SQLiteException("simulated transaction failure")
                }
            }
            try { f.repository.deleteVehicle(1); fail("Expected SQLiteException") }
            catch (_: SQLiteException) { }
            f.assertVehicleIntact(VEHICLE)
            f.assertUnrelatedIntact()
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.loaded())
            assertEquals(1, f.database.vehicleDao().observeAllVehicles().first().size)
        }
    }

    @Test
    fun cancellationBetweenStoresSkipsRoomAndRestartDoesNotResumeDeletion() = runBlocking {
        withFixture { f ->
            f.preferences.afterClear = { currentCoroutineContext().cancel() }
            val deletion = async { f.repository.deleteVehicle(1) }
            deletion.join()
            assertTrue(deletion.isCancelled)
            assertEquals(0, f.dao.deleteCalls)
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.assertVehicleIntact(VEHICLE)
            f.restart()
            assertEquals(VehicleLoadState.Ready(VEHICLE), f.loaded())
            assertEquals(0, f.dao.deleteCalls)
            f.assertVehicleIntact(VEHICLE)
            f.repository.deleteVehicle(1)
            f.assertDeleted(VEHICLE)
        }
    }

    @Test
    fun cancellationAfterCommitReloadsEmptyStateAndCannotReimportLegacy() = runBlocking {
        withFixture { f ->
            f.dao.deleteAction = { target ->
                f.database.vehicleDao().delete(target)
                throw CancellationException("simulated interruption after commit")
            }
            val deletion = async { f.repository.deleteVehicle(1) }
            deletion.join()
            assertTrue(deletion.isCancelled)
            f.assertDeleted(VEHICLE)
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            awaitDeletion { f.repository.loadState.first { it == VehicleLoadState.Empty } }
            assertNull(f.repository.activeVehicleId.value)
            f.restart()
            assertEquals(VehicleLoadState.Empty, f.loaded())
            assertTrue(f.database.vehicleDao().observeAllVehicles().first().isEmpty())
            f.assertUnrelatedIntact()
        }
    }

    @Test
    fun sharedMutexPreventsCreationBetweenLastVehicleCheckAndDeletion() = runBlocking {
        withFixture { f ->
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.preferences.afterClear = { entered.complete(Unit); finish.await() }
            val deletion = async { f.repository.deleteVehicle(1) }
            awaitDeletion { entered.await() }
            val creation = async(start = CoroutineStart.UNDISPATCHED) {
                f.repository.createVehicle(LEGACY.copy(model = "Nuevo"))
            }
            assertFalse(creation.isCompleted)
            assertEquals(listOf(VEHICLE), f.database.vehicleDao().observeAllVehicles().first())
            finish.complete(Unit)
            deletion.await()
            val created = awaitDeletion { creation.await() }
            awaitDeletion { f.repository.activeVehicle.first { it?.id == created.id } }
            assertEquals(listOf(created), f.database.vehicleDao().observeAllVehicles().first())
            assertEquals(created.id, f.preferences.loadActiveVehicleId())
            assertEquals(Vehicle(), f.preferences.loadVehicle())
            f.assertUnrelatedIntact()
        }
    }

    @Test
    fun viewModelReportsFailureWithoutSuccessThenRejectsDuplicateAndRetries() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.clearError = IOException("simulated failure")
            withContext(Dispatchers.Main.immediate) { vm.deleteVehicle(1, "delete") }
            awaitDeletion { vm.error.first { it != null } }
            awaitDeletion { vm.isWorking.first { !it } }
            assertEquals(com.bgr3108.kilonom.viewmodel.ScreenOperationStatus.FAILED, vm.operationResults.value["delete"]?.status)
            f.assertVehicleIntact(VEHICLE)
            f.preferences.clearError = null
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.dao.deleteAction = { target -> entered.complete(Unit); finish.await(); f.database.vehicleDao().delete(target) }
            withContext(Dispatchers.Main.immediate) { vm.deleteVehicle(1, "delete") }
            awaitDeletion { entered.await() }
            withContext(Dispatchers.Main.immediate) { vm.deleteVehicle(1, "delete") }
            assertEquals(1, f.dao.deleteCalls)
            assertEquals(com.bgr3108.kilonom.viewmodel.ScreenOperationStatus.RUNNING, vm.operationResults.value["delete"]?.status)
            finish.complete(Unit)
            val completed = awaitDeletion { vm.operationResults.first { it["delete"]?.status == com.bgr3108.kilonom.viewmodel.ScreenOperationStatus.SUCCEEDED } }.getValue("delete")
            assertTrue(vm.consumeOperation("delete", completed.id))
            assertFalse(vm.consumeOperation("delete", completed.id))
            awaitDeletion { vm.isWorking.first { !it } }
            assertNull(vm.error.value)
            f.assertDeleted(VEHICLE)
        }
    }

    @Test
    fun viewModelCancellationRestoresWorkingFlagWithoutSuccessAndAllowsRetry() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            val cleared = CompletableDeferred<Unit>()
            f.preferences.afterClear = { cleared.complete(Unit); currentCoroutineContext().cancel() }
            withContext(Dispatchers.Main.immediate) { vm.deleteVehicle(1, "delete") }
            awaitDeletion { cleared.await() }
            awaitDeletion { vm.isWorking.first { !it } }
            assertEquals(com.bgr3108.kilonom.viewmodel.ScreenOperationStatus.CANCELLED, vm.operationResults.value["delete"]?.status)
            assertNull(vm.error.value)
            assertEquals(0, f.dao.deleteCalls)
            f.assertVehicleIntact(VEHICLE)
            f.preferences.afterClear = {}
            withContext(Dispatchers.Main.immediate) { vm.deleteVehicle(1, "delete") }
            awaitDeletion { vm.operationResults.first { it["delete"]?.status == com.bgr3108.kilonom.viewmodel.ScreenOperationStatus.SUCCEEDED } }
            awaitDeletion { vm.isWorking.first { !it } }
            f.assertDeleted(VEHICLE)
        }
    }

    @Test
    fun missingVehicleDoesNotClearPreferencesOrDeleteAnything() = runBlocking {
        withFixture { f ->
            f.repository.deleteVehicle(999)
            assertEquals(0, f.preferences.clearCalls)
            assertEquals(0, f.dao.deleteCalls)
            assertEquals(LEGACY, f.preferences.loadVehicle())
            f.assertVehicleIntact(VEHICLE)
            f.assertUnrelatedIntact()
        }
    }

    private suspend fun withFixture(
        others: List<VehicleEntity> = emptyList(),
        test: suspend (Fixture) -> Unit
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = withContext(Dispatchers.IO) {
            Files.createTempDirectory(context.cacheDir.toPath(), "vehicle-delete-test-").toFile()
        }
        val fixture = Fixture(directory)
        try { fixture.seed(others); test(fixture) }
        finally {
            fixture.close()
            withContext(Dispatchers.IO) { check(directory.deleteRecursively()) }
        }
    }

    private class Fixture(private val directory: File) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val dao = DeletionDao(database.vehicleDao())
        private var preferencesScope = newScope()
        private var repositoryScope = newScope()
        private val unrelatedScope = newScope()
        private val unrelated = PreferenceDataStoreFactory.create(
            scope = unrelatedScope, produceFile = { File(directory, "unrelated.preferences_pb") }
        )
        private val viewModels = ViewModelStore()
        var preferences = createPreferences()
            private set
        lateinit var repository: VehicleRepository
            private set

        private fun createPreferences() = DeletionPreferences(VehiclePreferences(PreferenceDataStoreFactory.create(
            scope = preferencesScope, produceFile = { File(directory, "vehicle.preferences_pb") }
        )))

        suspend fun seed(others: List<VehicleEntity>) {
            for (vehicle in listOf(VEHICLE) + others) {
                database.vehicleDao().insert(vehicle)
                database.fuelEntryDao().insertEntry(entry(vehicle.id))
                database.maintenanceDao().insertItem(item(vehicle.id))
                database.maintenanceDao().insertRecord(record(vehicle.id))
            }
            database.carDao().upsertCar(CAR)
            preferences.saveVehicle(LEGACY)
            preferences.saveActiveVehicleId(1)
            preferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
            unrelated.edit { it[SENTINEL] = "keep" }
            repository = createRepository()
            loaded()
        }

        private fun createRepository() = VehicleRepository(
            EmptyCatalog, preferences, dao, database.fuelEntryDao(), database.maintenanceDao(), repositoryScope
        )

        suspend fun loaded() = awaitDeletion { repository.loadState.first { it != VehicleLoadState.Loading } }

        suspend fun restart() {
            withContext(Dispatchers.Main.immediate) { viewModels.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            repositoryScope = newScope()
            preferencesScope = newScope()
            preferences = createPreferences()
            repository = createRepository()
        }

        suspend fun viewModel() = withContext(Dispatchers.Main.immediate) {
            MyVehiclesViewModel(repository).also { viewModels.put("vehicles", it) }
        }

        suspend fun assertVehicleIntact(vehicle: VehicleEntity) {
            assertEquals(vehicle, database.vehicleDao().getVehicle(vehicle.id))
            assertEquals(entry(vehicle.id), database.fuelEntryDao().getEntryForVehicle(vehicle.id.toInt(), vehicle.id))
            assertEquals(item(vehicle.id), database.maintenanceDao().getItemForVehicle(vehicle.id, vehicle.id))
            assertEquals(record(vehicle.id), database.maintenanceDao().getRecordForVehicle(vehicle.id, vehicle.id)?.toDomain()?.record)
        }

        suspend fun assertDeleted(vehicle: VehicleEntity) {
            assertNull(database.vehicleDao().getVehicle(vehicle.id))
            assertEquals(0, database.fuelEntryDao().countEntries(vehicle.id))
            assertEquals(0, database.maintenanceDao().countItemsForVehicle(vehicle.id))
            assertEquals(0, database.maintenanceDao().countRecordsForVehicle(vehicle.id))
        }

        suspend fun assertUnrelatedIntact() {
            assertEquals(CAR, database.carDao().getCarOnce())
            assertEquals("keep", unrelated.data.first()[SENTINEL])
        }

        suspend fun close() {
            withContext(Dispatchers.Main.immediate) { viewModels.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(unrelatedScope.coroutineContext[Job]).cancelAndJoin()
            database.close()
        }
    }

    private class DeletionDao(delegate: VehicleDao) : VehicleDao by delegate {
        var deleteAction: suspend (VehicleEntity) -> Unit = delegate::delete
        var deleteCalls = 0
            private set

        override suspend fun delete(vehicle: VehicleEntity) {
            deleteCalls++
            deleteAction(vehicle)
        }
    }

    private class DeletionPreferences(private val delegate: VehiclePreferencesStore) : VehiclePreferencesStore by delegate {
        var clearError: IOException? = null
        var afterClear: suspend () -> Unit = {}
        var clearCalls = 0
            private set

        override suspend fun clearVehicle() {
            clearCalls++
            clearError?.let { throw it }
            delegate.clearVehicle()
            afterClear()
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
        val CAR = Car(marca = "Prueba", modelo = "Conservar", matricula = "TEST", kmActuales = 1_200)
        fun entry(id: Long) = FuelEntry(id = id.toInt(), fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = id)
        fun item(id: Long) = MaintenanceItemEntity(id = id, vehicleId = id, type = MaintenanceType.ITV, trackingKey = "ITV", createdAt = 1, updatedAt = 1)
        fun record(id: Long) = MaintenanceRecordEntity(id = id, itemId = id, odometerKm = 1_200, createdAt = 1, updatedAt = 1)
        fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private suspend fun <T> awaitDeletion(block: suspend () -> T): T = withTimeout(5.seconds) { block() }
