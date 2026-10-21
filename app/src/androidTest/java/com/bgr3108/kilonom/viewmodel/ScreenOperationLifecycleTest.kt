package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.testing.OperationTestActivity
import com.bgr3108.kilonom.ui.navigation.ScreenOperationEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/** Real Compose navigation and Activity recreation; all persistence is isolated from AppContainer. */
@RunWith(AndroidJUnit4::class)
class ScreenOperationLifecycleTest {
    @Test fun fuelSaveNavigatesOnceAndRejectsDoubleSubmission() = normal(Source.FUEL)
    @Test fun maintenanceSaveNavigatesOnceAndRejectsDoubleSubmission() = normal(Source.MAINTENANCE)
    @Test fun fuelBackDoesNotCloseAnotherEntry() = leave(Source.FUEL, back = true)
    @Test fun maintenanceBackDoesNotCloseAnotherEntry() = leave(Source.MAINTENANCE, back = true)
    @Test fun fuelOtherScreenDoesNotReceiveCompletion() = leave(Source.FUEL, back = false)
    @Test fun maintenanceOtherScreenDoesNotReceiveCompletion() = leave(Source.MAINTENANCE, back = false)
    @Test fun fuelSaveSurvivesRealActivityRecreation() = recreate(Source.FUEL, failure = false)
    @Test fun maintenanceSaveSurvivesRealActivityRecreation() = recreate(Source.MAINTENANCE, failure = false)
    @Test fun fuelErrorSurvivesRealRecreationAndRetryWritesOnce() = recreate(Source.FUEL, failure = true)
    @Test fun maintenanceErrorSurvivesRealRecreationAndRetryWritesOnce() = recreate(Source.MAINTENANCE, failure = true)
    @Test fun fuelEditDoesNotRepeatUpdateAfterRecreation() = recreate(Source.FUEL, failure = false, action = Action.EDIT)
    @Test fun maintenanceEditDoesNotRepeatUpdateAfterRecreation() = recreate(Source.MAINTENANCE, failure = false, action = Action.EDIT)
    @Test fun fuelDeletionAfterBackDoesNotNavigate() = leave(Source.FUEL, back = true, action = Action.DELETE)
    @Test fun maintenanceDeletionAfterBackDoesNotNavigate() = leave(Source.MAINTENANCE, back = true, action = Action.DELETE)
    @Test fun fuelCancellationRestoresFlagsAndAllowsRetry() = cancellation(Source.FUEL)
    @Test fun maintenanceCancellationRestoresFlagsAndAllowsRetry() = cancellation(Source.MAINTENANCE)
    @Test fun maintenanceRecordCreationSurvivesRecreationWithoutDuplicateInsert() = recreate(Source.MAINTENANCE, failure = false, action = Action.REGISTER)
    @Test fun maintenanceRecordEditErrorSurvivesRecreationAndRetries() = recreate(Source.MAINTENANCE, failure = true, action = Action.EDIT_RECORD)
    @Test fun maintenanceRecordDeletionAfterBackDoesNotNavigate() = leave(Source.MAINTENANCE, back = true, action = Action.DELETE_RECORD)

    private fun normal(source: Source) = runBlocking {
        fixture(source) { f, scenario ->
            scenario.onActivity { f.start(); f.start() }
            f.gate.started.await()
            assertEquals(1, f.gate.calls.get())
            f.gate.release.complete(Unit)
            f.completed()
            f.route.first { it == "home" }
            assertEquals(1, f.navigations.get())
            // A stale click from the completed form cannot insert again, even after consumption.
            scenario.onActivity { f.submit(f.origin) }
            assertEquals(1, f.gate.calls.get())
            f.assertPersisted()
        }
    }

    private fun leave(source: Source, back: Boolean, action: Action = Action.CREATE) = runBlocking {
        fixture(source, action) { f, scenario ->
            scenario.onActivity { f.start() }
            f.gate.started.await()
            scenario.onActivity {
                if (back) f.nav.get().popBackStack() else f.nav.get().navigate("other")
            }
            f.route.first { it == if (back) "home" else "other" }
            f.gate.release.complete(Unit)
            f.completed()
            idle()
            assertEquals(if (back) "home" else "other", f.route.value)
            assertEquals(0, f.navigations.get())
            assertEquals(1, f.gate.calls.get())
            f.assertPersisted()
            if (!back) {
                // Returning to the original entry consumes its pending completion, exactly once.
                scenario.onActivity { f.nav.get().popBackStack() }
                f.route.first { it == "home" }
                assertEquals(1, f.navigations.get())
            }
        }
    }

    private fun recreate(source: Source, failure: Boolean, action: Action = Action.CREATE) = runBlocking {
        fixture(source, action) { f, scenario ->
            scenario.onActivity { f.start() }
            f.gate.started.await()
            val oldNav = f.nav.get()
            val oldVm = f.modelIdentity()
            scenario.recreate()
            idle()
            assertNotSame(oldNav, f.nav.get())
            assertSame(oldVm, f.modelIdentity())
            if (failure) f.gate.error = SQLiteException("private storage detail")
            f.gate.release.complete(Unit)
            if (failure) {
                f.results().first { it[f.origin]?.status == ScreenOperationStatus.FAILED }
                f.assertNotWorking()
                idle()
                assertEquals("form", f.route.value)
                assertEquals(0, f.navigations.get())
                f.displayedError.first { it != null }
                // Recreate once more with the error already present: a one-shot event would lose it.
                f.displayedError.value = null
                scenario.recreate()
                idle()
                f.displayedError.first { it != null }
                f.gate.error = null
                scenario.onActivity { f.start() }
            }
            f.completed()
            f.route.first { it == "home" }
            assertEquals(1, f.navigations.get())
            scenario.recreate()
            idle()
            assertEquals("home", f.route.value)
            assertEquals(1, f.navigations.get())
            assertEquals(if (failure) 2 else 1, f.gate.calls.get())
            f.assertPersisted()
        }
    }

    private fun cancellation(source: Source) = runBlocking {
        fixture(source) { f, scenario ->
            f.gate.error = CancellationException("test cancellation")
            scenario.onActivity { f.start() }
            f.gate.started.await()
            f.gate.release.complete(Unit)
            f.results().first { it[f.origin]?.status == ScreenOperationStatus.CANCELLED }
            f.assertNotWorking()
            assertEquals(0, f.navigations.get())
            assertNull(f.results().value[f.origin]?.error)
            f.gate.error = null
            scenario.onActivity { f.start() }
            f.completed()
            f.route.first { it == "home" }
            f.assertPersisted()
        }
    }

    private suspend fun fixture(
        source: Source,
        action: Action = Action.CREATE,
        test: suspend (Fixture, ActivityScenario<OperationTestActivity>) -> Unit
    ) {
        val f = Fixture(source, action)
        try {
            withTimeout(10.seconds) { f.seed() }
            OperationTestActivity.content = { activity -> f.Content(activity) }
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                try { withTimeout(20.seconds) {
                    f.route.first { it == "home" }
                    scenario.onActivity { f.nav.get().navigate("form") }
                    f.route.first { it == "form" }
                    test(f, scenario)
                } } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                    throw AssertionError("Lifecycle timeout: route=${f.route.value}, calls=${f.gate.calls.get()}, result=${f.results().value}", error)
                }
            }
        } finally {
            OperationTestActivity.content = null
            f.close()
        }
    }

    private enum class Source { FUEL, MAINTENANCE }
    private enum class Action { CREATE, EDIT, DELETE, REGISTER, EDIT_RECORD, DELETE_RECORD }

    private class Gate {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        @Volatile var error: Exception? = null
        suspend fun enter() {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
            error?.let { throw it }
        }
    }

    private class Fixture(val source: Source, val action: Action) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val directory = Files.createTempDirectory(context.cacheDir.toPath(), "operation-lifecycle-").toFile()
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        private val preferences = VehiclePreferences(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { File(directory, "vehicle.preferences_pb") }
        ))
        val gate = Gate()
        private val fuelDao = object : FuelEntryDao by database.fuelEntryDao() {
            override suspend fun insertEntry(entry: FuelEntry) { gate.enter(); database.fuelEntryDao().insertEntry(entry) }
            override suspend fun updateEntry(entry: FuelEntry): Int { gate.enter(); return database.fuelEntryDao().updateEntry(entry) }
            override suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int {
                gate.enter(); return database.fuelEntryDao().deleteEntryForVehicle(entryId, vehicleId)
            }
        }
        private val maintenanceDao = object : MaintenanceDao by database.maintenanceDao() {
            override suspend fun insertItemWithRecord(item: MaintenanceItemEntity, record: MaintenanceRecordEntity?): Long {
                gate.enter(); return database.maintenanceDao().insertItemWithRecord(item, record)
            }
            override suspend fun updateItem(item: MaintenanceItemEntity): Int { gate.enter(); return database.maintenanceDao().updateItem(item) }
            override suspend fun deleteItemForVehicle(itemId: Long, vehicleId: Long): Int {
                gate.enter(); return database.maintenanceDao().deleteItemForVehicle(itemId, vehicleId)
            }
            override suspend fun updateItemWithNewRecord(item: MaintenanceItemEntity, record: MaintenanceRecordEntity) {
                gate.enter(); database.maintenanceDao().updateItemWithNewRecord(item, record)
            }
            override suspend fun updateRecord(record: MaintenanceRecordEntity): Int {
                gate.enter(); return database.maintenanceDao().updateRecord(record)
            }
            override suspend fun deleteRecordForVehicle(recordId: Long, vehicleId: Long): Int {
                gate.enter(); return database.maintenanceDao().deleteRecordForVehicle(recordId, vehicleId)
            }
        }
        private lateinit var repository: VehicleRepository
        val nav = AtomicReference<NavHostController>()
        val route = MutableStateFlow<String?>(null)
        val displayedError = MutableStateFlow<String?>(null)
        val navigations = AtomicInteger()
        private lateinit var fuel: FuelEntryViewModel
        private lateinit var maintenance: MaintenanceViewModel
        lateinit var origin: String

        suspend fun seed() {
            database.vehicleDao().insert(Vehicle(type = VehicleType.GASOLINA).toEntity(id = 1, createdAt = 1))
            if (action != Action.CREATE) {
                database.fuelEntryDao().insertEntry(ENTRY)
                database.maintenanceDao().insertItem(ITEM)
                if (action == Action.EDIT_RECORD || action == Action.DELETE_RECORD) database.maintenanceDao().insertRecord(RECORD)
            }
            preferences.saveActiveVehicleId(1)
            repository = VehicleRepository(EmptyCatalog, preferences, database.vehicleDao(), database.fuelEntryDao(), database.maintenanceDao(), scope)
            repository.loadState.first { it is VehicleLoadState.Ready }
        }

        @Composable
        fun Content(activity: OperationTestActivity) {
            val provider = ViewModelProvider(activity, viewModelFactory {
                initializer { FuelEntryViewModel(FuelRepository(fuelDao), repository) }
                initializer { MaintenanceViewModel(MaintenanceRepository(database, maintenanceDao, repository), repository) }
            })
            fuel = provider[FuelEntryViewModel::class.java]
            maintenance = provider[MaintenanceViewModel::class.java]
            val controller = rememberNavController()
            val working = when (source) {
                Source.FUEL -> {
                    val saving by fuel.isSaving.collectAsStateWithLifecycle()
                    val deleting by fuel.isDeleting.collectAsStateWithLifecycle()
                    saving || deleting
                }
                Source.MAINTENANCE -> {
                    val state by maintenance.state.collectAsStateWithLifecycle()
                    state.isWorking
                }
            }
            SideEffect { nav.set(controller) }
            DisposableEffect(controller) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    route.value = destination.route
                }
                controller.addOnDestinationChangedListener(listener)
                onDispose { controller.removeOnDestinationChangedListener(listener) }
            }
            MaterialTheme {
                NavHost(controller, startDestination = "home") {
                    composable("home") { Text("Home aislado") }
                    composable("other") { Text("Otra pantalla") }
                    composable("form") { entry ->
                        val operations by results().collectAsStateWithLifecycle()
                        SideEffect { displayedError.value = operations[entry.id]?.error }
                        ScreenOperationEffect(entry.id, results(), ::consume,
                            isCurrentEntry = { controller.currentBackStackEntry?.id == entry.id }
                        ) {
                            navigations.incrementAndGet()
                            controller.popBackStack()
                        }
                        Column {
                            Text("Formulario aislado")
                            operations[entry.id]?.error?.let { Text(it) }
                            Button(enabled = !working, onClick = { start() }) { Text("Guardar") }
                        }
                    }
                }
            }
        }

        fun start() {
            origin = requireNotNull(nav.get().currentBackStackEntry).id
            submit(origin)
        }

        fun submit(entryId: String) {
            when (source) {
                Source.FUEL -> if (action == Action.DELETE) fuel.deleteEntry(ENTRY, entryId)
                else fuel.saveEntry(FuelEntryDraft(
                    id = if (action == Action.EDIT) 1 else 0, fecha = 2, cantidad = 20.0,
                    precio = 30.0, tipo = FuelType.GASOLINA, km = 1_300.0, fullTank = true,
                    fuelLevelAfter = null, electricChargeStartPercentage = null,
                    electricChargeEndPercentage = null, originalVehicleId = 1
                ), entryId)
                Source.MAINTENANCE -> when (action) {
                    Action.CREATE -> maintenance.createItem(MaintenanceItemDraft(MaintenanceType.GENERAL_SERVICE), null, origin = entryId)
                    Action.EDIT -> maintenance.updateItem(ITEM, MaintenanceItemDraft(ITEM.type, nextDueKm = 2_000), entryId)
                    Action.DELETE -> maintenance.deleteItem(ITEM.id, entryId)
                    Action.REGISTER -> maintenance.registerRecord(ITEM, RECORD_DRAFT,
                        com.bgr3108.kilonom.domain.MaintenanceNextDueUpdate.Clear, entryId)
                    Action.EDIT_RECORD -> maintenance.updateRecord(RECORD, RECORD_DRAFT, entryId)
                    Action.DELETE_RECORD -> maintenance.deleteRecord(RECORD.id, entryId)
                }
            }
        }

        fun results() = if (source == Source.FUEL) fuel.operationResults else maintenance.operationResults
        fun modelIdentity(): Any = if (source == Source.FUEL) fuel else maintenance
        private fun consume(entryId: String, id: Long) = if (source == Source.FUEL) fuel.consumeOperation(entryId, id) else maintenance.consumeOperation(entryId, id)
        suspend fun completed() {
            results().first { it[origin]?.status == ScreenOperationStatus.SUCCEEDED }
            assertNotWorking()
        }
        suspend fun assertNotWorking() {
            if (source == Source.FUEL) {
                fuel.isSaving.first { !it }
                fuel.isDeleting.first { !it }
            } else {
                maintenance.state.first { it.vehicle != null && !it.isWorking }
            }
        }
        suspend fun assertPersisted() {
            val expected = if (action == Action.DELETE) 0 else 1
            if (source == Source.FUEL) {
                assertEquals(expected, database.fuelEntryDao().countEntries(1))
                if (action == Action.EDIT) assertEquals(20.0, database.fuelEntryDao().getEntryForVehicle(1, 1)!!.cantidad, 0.0)
            } else {
                assertEquals(expected, database.maintenanceDao().countItemsForVehicle(1))
                if (action == Action.EDIT) assertEquals(2_000L, database.maintenanceDao().getItemForVehicle(1, 1)?.nextDueKm)
                if (action == Action.REGISTER || action == Action.EDIT_RECORD || action == Action.DELETE_RECORD) {
                    assertEquals(if (action == Action.DELETE_RECORD) 0 else 1, database.maintenanceDao().countRecordsForVehicle(1))
                    if (action == Action.EDIT_RECORD) assertEquals(25.0, database.maintenanceDao().getRecordForVehicle(1, 1)?.cost)
                }
            }
        }
        suspend fun close() {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
            database.close()
            check(directory.deleteRecursively())
        }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
    private companion object {
        val ENTRY = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = 1)
        val ITEM = MaintenanceItemEntity(id = 1, vehicleId = 1, type = MaintenanceType.OIL_AND_FILTER, trackingKey = "oil", createdAt = 1, updatedAt = 1)
        val RECORD = MaintenanceRecordEntity(id = 1, itemId = 1, cost = 10.0, createdAt = 1, updatedAt = 1)
        val RECORD_DRAFT = MaintenanceRecordDraft(performedDate = null, odometerKm = null, cost = 25.0, notes = "Prueba aislada")
        fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
