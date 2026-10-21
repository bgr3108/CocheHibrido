package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.VehicleDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

/** No real vehicles, preferences or caches are used by these tests. */
@RunWith(AndroidJUnit4::class)
class VehicleCreationRecoveryTest {
    @Test
    fun normalCreationConfirmsStorageAndActivationWithOneInsert() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.create(vm)
            val saved = f.saved(vm, active = true)
            assertEquals(NEW.copy(id = saved.vehicleId), f.database.vehicleDao().getVehicle(saved.vehicleId)?.toVehicle())
            assertEquals(saved.vehicleId, f.preferences.loadActiveVehicleId())
            assertEquals(saved.vehicleId, f.repository.activeVehicleId.value)
            assertEquals(1, f.dao.insertCalls)
            assertNull(vm.error.value)
            f.assertNoDeletion()
        }
    }

    @Test
    fun roomFailureBeforeInsertReportsRealSaveFailureAndAllowsRetry() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.dao.insertAction = { throw SQLiteException("simulated insert failure") }
            f.create(vm)
            awaitCreation { vm.error.first { it != null } }
            awaitCreation { vm.isWorking.first { !it } }
            assertEquals(VehicleCreationState.Editing, vm.creationState.value)
            assertEquals("No se pudo guardar el vehículo. Inténtalo de nuevo.", vm.error.value)
            assertEquals(listOf(OLD), f.database.vehicleDao().observeAllVehicles().first())
            f.assertPreviousActive()
            f.dao.insertAction = f.database.vehicleDao()::insert
            f.create(vm)
            f.saved(vm, active = true)
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
            f.assertNoDeletion()
        }
    }

    @Test
    fun dataStoreFailureAfterInsertKeepsSavedIdAndPreviousActiveVehicle() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException("private technical detail")
            f.create(vm)
            val saved = f.saved(vm, active = false)
            assertNotNull(f.database.vehicleDao().getVehicle(saved.vehicleId))
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
            assertEquals("El vehículo está guardado, pero no pudo activarse automáticamente. Puedes reintentar la activación.", vm.error.value)
            f.assertPreviousActive()
            f.assertNoDeletion()
        }
    }

    @Test
    fun activationRetriesAndRepeatedSubmitNeverInsertTheSavedVehicleAgain() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException()
            f.create(vm)
            val saved = f.saved(vm, active = false)
            // Even a stale submit callback cannot turn an activation retry into a second INSERT.
            f.create(vm)
            withContext(Dispatchers.Main.immediate) { vm.retryCreatedVehicleActivation() }
            f.saved(vm, active = false)
            assertEquals(1, f.dao.insertCalls)
            f.preferences.selectionError = null
            withContext(Dispatchers.Main.immediate) { vm.retryCreatedVehicleActivation() }
            assertEquals(saved.vehicleId, f.saved(vm, active = true).vehicleId)
            assertEquals(saved.vehicleId, f.preferences.loadActiveVehicleId())
            assertEquals(1, f.dao.insertCalls)
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
            f.assertNoDeletion()
        }
    }

    @Test
    fun doubleClickAndRepeatedCompletedSubmitDoNotCreateAnotherVehicle() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.dao.insertAction = { candidate -> entered.complete(Unit); finish.await(); f.database.vehicleDao().insert(candidate) }
            f.create(vm)
            awaitCreation { entered.await() }
            f.create(vm)
            assertEquals(1, f.dao.insertCalls)
            assertTrue(vm.isWorking.value)
            finish.complete(Unit)
            f.saved(vm, active = true)
            f.create(vm)
            assertEquals(1, f.dao.insertCalls)
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
        }
    }

    @Test
    fun twoIdenticalVehiclesAreAllowedAsTwoExplicitCreations() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.create(vm)
            val first = f.saved(vm, active = true)
            withContext(Dispatchers.Main.immediate) { assertTrue(vm.beginVehicleCreation()) }
            f.create(vm)
            val second = f.saved(vm, active = true)
            assertNotEquals(first.vehicleId, second.vehicleId)
            val snapshots = f.database.vehicleDao().observeAllVehicles().first().filter { it.id != OLD.id }
            assertEquals(2, snapshots.size)
            snapshots.forEach { assertEquals(NEW.copy(id = it.id), it.toVehicle()) }
            assertEquals(2, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    @Test
    fun cancellationBetweenInsertAndSelectionPropagatesAndKeepsTheAcknowledgedId() = runBlocking {
        withFixture { f ->
            val stored = CompletableDeferred<VehicleEntity>()
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.preferences.beforeSelection = { entered.complete(Unit); finish.await() }
            val creation = async { f.repository.createVehicle(NEW) { stored.complete(it) } }
            awaitCreation { entered.await() }
            creation.cancelAndJoin()
            assertTrue(creation.isCancelled)
            val entity = stored.await()
            assertEquals(entity, f.database.vehicleDao().getVehicle(entity.id))
            f.assertPreviousActive()
            f.preferences.beforeSelection = {}
            assertTrue(f.repository.activateCreatedVehicle(entity.id))
            assertEquals(1, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    @Test
    fun cancellationDuringInsertAcknowledgesCommitButDoesNotActivate() = runBlocking {
        withFixture { f ->
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val stored = CompletableDeferred<VehicleEntity>()
            f.dao.insertAction = { candidate -> entered.complete(Unit); finish.await(); f.database.vehicleDao().insert(candidate) }
            val creation = async { f.repository.createVehicle(NEW) { stored.complete(it) } }
            awaitCreation { entered.await() }
            creation.cancel()
            finish.complete(Unit)
            creation.join()
            assertTrue(creation.isCancelled)
            val entity = stored.await()
            assertEquals(entity, f.database.vehicleDao().getVehicle(entity.id))
            f.assertPreviousActive()
            assertEquals(1, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    @Test
    fun viewModelCancellationRestoresLoadingAndRetriesOnlyActivation() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.beforeSelection = { throw CancellationException("simulated interruption") }
            f.create(vm)
            val saved = f.saved(vm, active = false)
            assertNull(vm.error.value)
            f.assertPreviousActive()
            f.preferences.beforeSelection = {}
            withContext(Dispatchers.Main.immediate) { vm.retryCreatedVehicleActivation() }
            assertEquals(saved.vehicleId, f.saved(vm, active = true).vehicleId)
            assertEquals(1, f.dao.insertCalls)
        }
    }

    @Test
    fun cancellationWhileWaitingForRepositoryMutexDoesNotInsertAnything() = runBlocking {
        withFixture { f ->
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.preferences.beforeSelection = { entered.complete(Unit); finish.await() }
            val selection = async { f.repository.selectActiveVehicle(OLD.id) }
            awaitCreation { entered.await() }
            val creation = async(start = CoroutineStart.UNDISPATCHED) { f.repository.createVehicle(NEW) }
            creation.cancelAndJoin()
            assertTrue(creation.isCancelled)
            assertEquals(0, f.dao.insertCalls)
            finish.complete(Unit)
            selection.await()
            f.assertPreviousActive()
            f.assertNoDeletion()
        }
    }

    @Test
    fun retainedViewModelStoreAndReopenedFormKeepThePendingCreation() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException()
            f.create(vm)
            val saved = f.saved(vm, active = false)
            // Activity configuration recreation keeps this store; it is not process death.
            val retained = f.viewModel()
            assertSame(vm, retained)
            withContext(Dispatchers.Main.immediate) { assertTrue(retained.beginVehicleCreation()) }
            assertEquals(saved, retained.creationState.value)
            f.preferences.selectionError = null
            withContext(Dispatchers.Main.immediate) { retained.retryCreatedVehicleActivation() }
            assertEquals(saved.vehicleId, f.saved(retained, active = true).vehicleId)
            assertEquals(1, f.dao.insertCalls)
        }
    }

    @Test
    fun explicitContinueWithoutActivationAllowsAnotherIndependentCreation() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException()
            f.create(vm)
            val saved = f.saved(vm, active = false)
            withContext(Dispatchers.Main.immediate) { vm.acknowledgeCreatedVehicle() }
            assertEquals(saved.copy(acknowledged = true), vm.creationState.value)
            f.assertPreviousActive()
            withContext(Dispatchers.Main.immediate) { assertTrue(vm.beginVehicleCreation()) }
            assertEquals(VehicleCreationState.Editing, vm.creationState.value)
            assertNotNull(f.database.vehicleDao().getVehicle(saved.vehicleId))
            f.preferences.selectionError = null
            f.create(vm)
            assertNotEquals(saved.vehicleId, f.saved(vm, active = true).vehicleId)
            assertEquals(3, f.database.vehicleDao().observeAllVehicles().first().size)
            f.assertNoDeletion()
        }
    }

    @Test
    fun activationFromVehicleListIsRecognizedWhenOpeningANewForm() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException()
            f.create(vm)
            val saved = f.saved(vm, active = false)
            f.preferences.selectionError = null
            withContext(Dispatchers.Main.immediate) { vm.selectVehicle(saved.vehicleId) }
            awaitCreation { vm.isWorking.first { !it } }
            assertEquals(saved.vehicleId, f.repository.activeVehicleId.value)
            withContext(Dispatchers.Main.immediate) { assertTrue(vm.beginVehicleCreation()) }
            assertEquals(VehicleCreationState.Editing, vm.creationState.value)
            assertNull(vm.error.value)
            assertEquals(1, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    @Test
    fun repositoryAndDataStoreRestartPreserveInsertedRowAndPreviousSelection() = runBlocking {
        withFixture { f ->
            val vm = f.viewModel()
            f.preferences.selectionError = IOException()
            f.create(vm)
            val saved = f.saved(vm, active = false)
            f.restart()
            assertNotNull(f.database.vehicleDao().getVehicle(saved.vehicleId))
            f.assertPreviousActive()
            // The draft-to-row identity was only in the old ViewModel; do not claim exactly-once.
            val newVm = f.viewModel()
            assertEquals(VehicleCreationState.Editing, newVm.creationState.value)
            assertTrue(f.repository.activateCreatedVehicle(saved.vehicleId))
            assertEquals(1, f.dao.insertCalls)
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
            f.assertNoDeletion()
        }
    }

    @Test
    fun failedManualSelectionAlsoPreservesPreviouslyActiveVehicle() = runBlocking {
        withFixture { f ->
            val other = OLD.copy(id = 5, model = "Otro", createdAt = 2)
            f.database.vehicleDao().insert(other)
            f.preferences.selectionError = IOException()
            try { f.repository.selectActiveVehicle(other.id); fail("Expected IOException") }
            catch (_: IOException) { }
            f.assertPreviousActive()
            assertEquals(0, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    @Test
    fun missingCreatedIdNeverActivatesAnotherVehicleOrInsertsAnything() = runBlocking {
        withFixture { f ->
            assertFalse(f.repository.activateCreatedVehicle(999))
            f.assertPreviousActive()
            assertEquals(0, f.dao.insertCalls)
            f.assertNoDeletion()
        }
    }

    private suspend fun withFixture(test: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = withContext(Dispatchers.IO) { Files.createTempDirectory(context.cacheDir.toPath(), "vehicle-create-test-").toFile() }
        val f = Fixture(directory)
        try { f.seed(); test(f) }
        finally {
            f.close()
            withContext(Dispatchers.IO) { check(directory.deleteRecursively()) }
        }
    }

    private class Fixture(private val directory: File) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val dao = CreationDao(database.vehicleDao())
        private var preferencesScope = newScope()
        private var repositoryScope = newScope()
        private var store = ViewModelStore()
        var preferences = createPreferences()
            private set
        lateinit var repository: VehicleRepository
            private set

        private fun createPreferences() = CreationPreferences(VehiclePreferences(PreferenceDataStoreFactory.create(
            scope = preferencesScope, produceFile = { File(directory, "vehicle.preferences_pb") }
        )))

        suspend fun seed() {
            database.vehicleDao().insert(OLD)
            preferences.saveVehicle(OLD.toVehicle())
            preferences.saveActiveVehicleId(OLD.id)
            preferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
            repository = createRepository()
            awaitCreation { repository.loadState.first { it is VehicleLoadState.Ready } }
        }

        private fun createRepository() = VehicleRepository(
            EmptyCatalog, preferences, dao, database.fuelEntryDao(), database.maintenanceDao(), repositoryScope
        )

        suspend fun viewModel(): MyVehiclesViewModel = withContext(Dispatchers.Main.immediate) {
            ViewModelProvider.create(store, viewModelFactory {
                initializer { MyVehiclesViewModel(repository) }
            })[MyVehiclesViewModel::class.java]
        }

        suspend fun create(vm: MyVehiclesViewModel) = withContext(Dispatchers.Main.immediate) { vm.createVehicle(NEW) }

        suspend fun saved(vm: MyVehiclesViewModel, active: Boolean): VehicleCreationState.Saved {
            val result = awaitCreation { vm.creationState.first { it is VehicleCreationState.Saved && it.isActive == active } }
            awaitCreation { vm.isWorking.first { !it } }
            return result as VehicleCreationState.Saved
        }

        suspend fun assertPreviousActive() {
            assertEquals(OLD.id, preferences.loadActiveVehicleId())
            assertEquals(OLD.id, repository.activeVehicleId.value)
            assertEquals(OLD, repository.activeVehicle.value)
            assertEquals(OLD, database.vehicleDao().getVehicle(OLD.id))
        }

        fun assertNoDeletion() {
            assertEquals(0, dao.deleteCalls)
            assertEquals(0, preferences.clearCalls)
        }

        suspend fun restart() {
            withContext(Dispatchers.Main.immediate) { store.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            repositoryScope = newScope()
            preferencesScope = newScope()
            store = ViewModelStore()
            preferences = createPreferences()
            repository = createRepository()
            awaitCreation { repository.loadState.first { it is VehicleLoadState.Ready } }
        }

        suspend fun close() {
            withContext(Dispatchers.Main.immediate) { store.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            withContext(Dispatchers.IO) { database.close() }
        }
    }

    private class CreationDao(private val delegate: VehicleDao) : VehicleDao by delegate {
        var insertAction: suspend (VehicleEntity) -> Long = delegate::insert
        var insertCalls = 0
            private set
        var deleteCalls = 0
            private set

        override suspend fun insert(vehicle: VehicleEntity): Long {
            insertCalls++
            return insertAction(vehicle)
        }

        override suspend fun delete(vehicle: VehicleEntity) { deleteCalls++; delegate.delete(vehicle) }
        override suspend fun deleteAll() { deleteCalls++; delegate.deleteAll() }
    }

    private class CreationPreferences(private val delegate: VehiclePreferencesStore) : VehiclePreferencesStore by delegate {
        var selectionError: IOException? = null
        var beforeSelection: suspend () -> Unit = {}
        var clearCalls = 0
            private set

        override suspend fun saveActiveVehicleId(vehicleId: Long) {
            beforeSelection()
            currentCoroutineContext().ensureActive()
            selectionError?.let { throw it }
            delegate.saveActiveVehicleId(vehicleId)
        }

        override suspend fun clearVehicle() { clearCalls++; delegate.clearVehicle() }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }

    private companion object {
        val OLD = Vehicle(brand = "Prueba", model = "Anterior", year = 2026, type = VehicleType.GASOLINA, initialKm = 1_000.0)
            .toEntity(id = 1, createdAt = 1)
        val NEW = Vehicle(brand = "Prueba", model = "Igual", year = 2026, type = VehicleType.ELECTRICO, batteryCapacity = 50.0, initialKm = 200.0)
        fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private suspend fun <T> awaitCreation(block: suspend () -> T): T = withTimeout(5.seconds) { block() }
