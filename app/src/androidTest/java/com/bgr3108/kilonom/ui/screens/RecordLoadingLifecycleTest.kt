package com.bgr3108.kilonom.ui.screens

import android.database.sqlite.SQLiteException
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.database.MaintenanceRecordWithItemRow
import com.bgr3108.kilonom.testing.OperationTestActivity
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Production screens and real Activity recreation, with isolated Room and temporary DataStore. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class RecordLoadingLifecycleTest {
    @Test fun fuelEditWaitsForRoomAndSurvivesRecreationWhileLoading() = delayed(Screen.FUEL)
    @Test fun maintenanceEditWaitsForRoomAndSurvivesRecreationWhileLoading() = delayed(Screen.ITEM)
    @Test fun maintenanceRecordEditWaitsForRoom() = delayed(Screen.RECORD)
    @Test fun registeringMaintenanceWaitsForItsRealParentItem() = delayed(Screen.REGISTER)
    @Test fun maintenanceDetailWaitsForRoomAndSurvivesRecreationWhileLoading() = delayed(Screen.DETAIL)
    @Test fun fuelEditOnlyReportsMissingAfterActualEmptyAnswer() = absent(Screen.FUEL)
    @Test fun maintenanceEditOnlyReportsMissingAfterActualEmptyAnswer() = absent(Screen.ITEM)
    @Test fun maintenanceRecordEditOnlyReportsMissingAfterActualEmptyAnswer() = absent(Screen.RECORD)
    @Test fun maintenanceDetailOnlyReportsMissingAfterActualEmptyAnswer() = absent(Screen.DETAIL)
    @Test fun fuelReadErrorIsRecoverableAfterActivityRecreation() = failure(Screen.FUEL)
    @Test fun maintenanceReadErrorIsRecoverableAfterActivityRecreation() = failure(Screen.ITEM)
    @Test fun fuelListDoesNotPresentReadErrorAsAnEmptyHistory() = failure(Screen.FUEL_LIST)
    @Test fun maintenanceListDoesNotPresentReadErrorAsAnEmptyHistory() = failure(Screen.MAINTENANCE_LIST)
    @Test fun fuelDraftSurvivesRecreationRetryAndLaterRoomEmission() = draft(Screen.FUEL)
    @Test fun maintenanceDraftSurvivesRecreationRetryAndLaterRoomEmission() = draft(Screen.RECORD)
    @Test fun realDeletionOfFuelRecordShowsNotFound() = deletion(Screen.FUEL)
    @Test fun realDeletionOfMaintenanceItemShowsNotFound() = deletion(Screen.DETAIL)
    @Test fun changingActiveVehicleCancelsFuelReadAndRejectsOldVehicle() = changeVehicle(Screen.FUEL)
    @Test fun changingActiveVehicleCancelsMaintenanceReadAndRejectsOldVehicle() = changeVehicle(Screen.ITEM)

    private fun delayed(screen: Screen) = runBlocking {
        fixture(screen) { f, scenario ->
            awaitText(LOADING)
            assertFalse(hasText(MISSING))
            scenario.recreate()
            awaitText(LOADING)
            assertFalse(hasText(MISSING))
            f.release()
            awaitText(screen.foundText)
            f.assertSingleRead()
        }
    }

    private fun absent(screen: Screen) = runBlocking {
        fixture(screen, seedRecords = false) { f, _ ->
            awaitText(LOADING)
            assertFalse(hasText(MISSING))
            f.release()
            awaitText(MISSING)
            assertFalse(hasText("Guardar"))
        }
    }

    private fun failure(screen: Screen) = runBlocking {
        fixture(screen) { f, scenario ->
            f.fail = true
            f.release()
            awaitText(ERROR)
            assertFalse(hasText(MISSING))
            scenario.recreate()
            awaitText(ERROR)
            f.fail = false
            click("Reintentar")
            awaitText(screen.foundText)
            assertEquals(1, f.database.fuelEntryDao().countEntries(1))
            assertEquals(1, f.database.maintenanceDao().countRecordsForVehicle(1))
        }
    }

    private fun draft(screen: Screen) = runBlocking {
        fixture(screen) { f, scenario ->
            f.release()
            awaitText(screen.foundText)
            val field = if (screen == Screen.FUEL) "Importe total (€)" else "Coste (€) opcional"
            setField(field, "987")
            scenario.recreate()
            awaitText("987")
            // Retry removes the form from composition. The holder must preserve its saved draft.
            f.fail = true
            scenario.onActivity { f.retry() }
            awaitText(ERROR)
            f.fail = false
            click("Reintentar")
            awaitText("987")
            withContext(Dispatchers.IO) {
                if (screen == Screen.FUEL) f.database.fuelEntryDao().updateEntry(ENTRY.copy(precio = 22.0))
                else f.database.maintenanceDao().updateRecord(RECORD.copy(cost = 22.0))
            }
            f.awaitUpdatedRecord()
            awaitText("987")
            // No production save button was clicked; only the isolated fixture was updated above.
            assertEquals(1, f.database.fuelEntryDao().countEntries(1))
        }
    }

    private fun deletion(screen: Screen) = runBlocking {
        fixture(screen) { f, _ ->
            f.release()
            awaitText(screen.foundText)
            withContext(Dispatchers.IO) {
                if (screen == Screen.FUEL) f.database.fuelEntryDao().deleteEntryForVehicle(1, 1)
                else f.database.maintenanceDao().deleteItemForVehicle(1, 1)
            }
            awaitText(MISSING)
        }
    }

    private fun changeVehicle(screen: Screen) = runBlocking {
        fixture(screen) { f, _ ->
            awaitText(LOADING)
            withContext(Dispatchers.IO) { f.repository.selectActiveVehicle(2) }
            withTimeout(10.seconds) { while (f.cancelled.get() == 0) delay(16.milliseconds) }
            f.release()
            awaitText(MISSING) // The requested id belongs exclusively to the former vehicle.
            assertFalse(hasText(screen.foundText))
            assertEquals(1, f.database.fuelEntryDao().countEntries(1))
        }
    }

    private suspend fun fixture(
        screen: Screen,
        seedRecords: Boolean = true,
        test: suspend (Fixture, ActivityScenario<OperationTestActivity>) -> Unit
    ) = withTimeout(30.seconds) {
        val f = Fixture(screen)
        try {
            f.seed(seedRecords)
            OperationTestActivity.content = { f.Content(it) }
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                test(f, scenario)
            }
        } finally {
            OperationTestActivity.content = null
            f.close()
        }
    }

    private enum class Screen(val foundText: String) {
        FUEL("Importe total (€)"), ITEM("Editar seguimiento"), RECORD("Editar registro"),
        REGISTER("Registrar mantenimiento"), DETAIL("Historial"),
        FUEL_LIST("1 registros"), MAINTENANCE_LIST("Historial reciente")
    }

    private class Fixture(val screen: Screen) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val directory = Files.createTempDirectory(context.cacheDir.toPath(), "record-loading-").toFile()
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        private val preferences = VehiclePreferences(PreferenceDataStoreFactory.create(
            scope = scope, produceFile = { File(directory, "vehicle.preferences_pb") }
        ))
        private val gate = CompletableDeferred<Unit>()
        @Volatile var fail = false
        val cancelled = AtomicInteger()
        val fuelReads = AtomicInteger()
        val itemReads = AtomicInteger()
        val recordReads = AtomicInteger()
        private fun <T> delayed(source: Flow<T>, calls: AtomicInteger): Flow<T> = flow {
            calls.incrementAndGet()
            try {
                gate.await()
                if (fail) throw SQLiteException("isolated simulated read failure")
                emitAll(source)
            } finally { cancelled.incrementAndGet() }
        }
        private val fuelDao = object : FuelEntryDao by database.fuelEntryDao() {
            override fun observeEntries(vehicleId: Long) = delayed(database.fuelEntryDao().observeEntries(vehicleId), fuelReads)
        }
        private val maintenanceDao = object : MaintenanceDao by database.maintenanceDao() {
            override fun observeItems(vehicleId: Long) = delayed(database.maintenanceDao().observeItems(vehicleId), itemReads)
            override fun observeRecordsForVehicle(vehicleId: Long): Flow<List<MaintenanceRecordWithItemRow>> =
                delayed(database.maintenanceDao().observeRecordsForVehicle(vehicleId), recordReads)
        }
        lateinit var repository: VehicleRepository
        private lateinit var fuel: FuelEntryViewModel
        private lateinit var maintenance: MaintenanceViewModel

        suspend fun seed(records: Boolean) {
            database.vehicleDao().insert(Vehicle(type = VehicleType.GASOLINA).toEntity(id = 1, createdAt = 1))
            database.vehicleDao().insert(Vehicle(type = VehicleType.GASOLINA).toEntity(id = 2, createdAt = 2))
            if (records) {
                database.fuelEntryDao().insertEntry(ENTRY)
                database.maintenanceDao().insertItem(ITEM)
                database.maintenanceDao().insertRecord(RECORD)
            }
            preferences.saveActiveVehicleId(1)
            repository = VehicleRepository(EmptyCatalog, preferences, database.vehicleDao(), database.fuelEntryDao(), database.maintenanceDao(), scope)
            repository.loadState.first { it is VehicleLoadState.Ready }
        }

        @Composable fun Content(activity: OperationTestActivity) {
            val provider = ViewModelProvider(activity, viewModelFactory {
                initializer { FuelEntryViewModel(FuelRepository(fuelDao), repository) }
                initializer { HomeViewModel(FuelRepository(database.fuelEntryDao()), repository) }
                initializer { MaintenanceViewModel(MaintenanceRepository(database, maintenanceDao, repository), repository) }
            })
            fuel = provider[FuelEntryViewModel::class.java]
            maintenance = provider[MaintenanceViewModel::class.java]
            MaterialTheme {
                when (screen) {
                    Screen.FUEL -> EditConsumptionScreen(1, fuel, provider[HomeViewModel::class.java], PaddingValues(), "isolated", {})
                    Screen.ITEM -> MaintenanceFormScreen(PaddingValues(), MaintenanceFormMode.EDIT_ITEM, itemId = 1,
                        viewModel = maintenance, operationOrigin = "isolated", onClose = {})
                    Screen.RECORD -> MaintenanceFormScreen(PaddingValues(), MaintenanceFormMode.EDIT_RECORD, recordId = 1,
                        viewModel = maintenance, operationOrigin = "isolated", onClose = {})
                    Screen.REGISTER -> MaintenanceFormScreen(PaddingValues(), MaintenanceFormMode.REGISTER_RECORD, itemId = 1,
                        viewModel = maintenance, operationOrigin = "isolated", onClose = {})
                    Screen.DETAIL -> MaintenanceDetailScreen(PaddingValues(), 1, maintenance, "isolated", {}, {}, {}, {})
                    Screen.FUEL_LIST -> ConsumptionListScreen(PaddingValues(), fuel, provider[HomeViewModel::class.java],
                        rememberNavController(), "isolated", {})
                    Screen.MAINTENANCE_LIST -> MaintenanceScreen(PaddingValues(), maintenance, {}, {})
                }
            }
        }

        fun release() { gate.complete(Unit) }
        fun retry() { if (screen == Screen.FUEL || screen == Screen.FUEL_LIST) fuel.retryEntryLoad() else maintenance.retryRead() }
        suspend fun awaitUpdatedRecord() = withTimeout(10.seconds) {
            if (screen == Screen.FUEL) fuel.entryLoadState.first {
                (it as? RecordLoadState.Found)?.value?.firstOrNull()?.precio == 22.0
            } else maintenance.state.first { it.records.firstOrNull()?.record?.cost == 22.0 }
        }
        fun assertSingleRead() {
            if (screen == Screen.FUEL) assertEquals(1, fuelReads.get())
            else { assertEquals(1, itemReads.get()); assertEquals(1, recordReads.get()) }
        }
        suspend fun close() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
            check(directory.deleteRecursively())
        }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }

    private companion object {
        const val LOADING = "Cargando registro…"
        const val MISSING = "Registro no encontrado"
        const val ERROR = "No se ha podido leer el registro. Tus datos no se han modificado."
        val ENTRY = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1200.0, vehicleId = 1)
        val ITEM = MaintenanceItemEntity(id = 1, vehicleId = 1, type = MaintenanceType.OIL_AND_FILTER, trackingKey = "oil", createdAt = 1, updatedAt = 1)
        val RECORD = MaintenanceRecordEntity(id = 1, itemId = 1, cost = 10.0, createdAt = 1, updatedAt = 1)
        fun nodes() = WindowInspector.getGlobalWindowViews().flatMap(::roots)
            .flatMap { descendants(it.semanticsOwner.unmergedRootSemanticsNode).toList() }
        fun roots(view: View): List<RootForTest> = (if (view is RootForTest) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { roots(view.getChildAt(it)) } else emptyList())
        fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
            yield(node); node.children.forEach { yieldAll(descendants(it)) }
        }
        fun matches(node: SemanticsNode, text: String) =
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
                node.config.getOrNull(SemanticsProperties.EditableText)?.text == text
        suspend fun hasText(text: String) = withContext(Dispatchers.Main.immediate) { nodes().any { matches(it, text) } }
        suspend fun awaitText(text: String) {
            try {
                withTimeout(10.seconds) { while (!hasText(text)) delay(16.milliseconds) }
            } catch (error: TimeoutCancellationException) {
                val shown = withContext(Dispatchers.Main.immediate) {
                    nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                }
                throw AssertionError("Expected '$text'; visible semantics: $shown", error)
            }
        }
        suspend fun click(text: String) = withContext(Dispatchers.Main.immediate) {
            val node = nodes().first { node -> node.config.getOrNull(SemanticsActions.OnClick) != null && descendants(node).any { matches(it, text) } }
            assertTrue(node.config[SemanticsActions.OnClick].action?.invoke() == true)
        }
        suspend fun setField(label: String, value: String) = withContext(Dispatchers.Main.immediate) {
            val field = nodes().first { node -> node.config.getOrNull(SemanticsActions.SetText) != null && descendants(node).any { matches(it, label) } }
            assertTrue(field.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(value)) == true)
        }
    }
}
