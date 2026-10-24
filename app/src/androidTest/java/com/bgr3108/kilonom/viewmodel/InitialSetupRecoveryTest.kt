package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteException
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.ReleaseNotesDialog
import com.bgr3108.kilonom.shouldShowSetup
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.testing.OperationTestActivity
import com.bgr3108.kilonom.ui.screens.SetupScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Private temporary Room/DataStore files, never AppContainer or personal storage. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class InitialSetupRecoveryTest {
    @Test fun emptyInstallationStoresAndActivatesOneVehicle() = fixture { f ->
        val vm = f.home()
        onMain { vm.saveInitialVehicle(NEW) }
        val saved = f.saved(vm, active = true)
        assertEquals(NEW.copy(id = saved.vehicleId), f.database.vehicleDao().getFirstVehicle()?.toVehicle())
        assertEquals(saved.vehicleId, f.preferences.loadActiveVehicleId())
        assertEquals(NEW, f.preferences.loadVehicle())
        assertEquals(1, f.inserts.get())
        assertEquals(0, f.deletes.get())
        assertNull(vm.vehicleSaveError.value)
    }

    @Test fun activationFailureAndRepeatedSaveRetryOnlySelection() = fixture { f ->
        val vm = f.home()
        f.preferences.selectionError = IOException("private selection failure")
        onMain { vm.saveInitialVehicle(NEW) }
        val saved = f.saved(vm, active = false)
        assertNotNull(vm.vehicleSaveError.value)
        assertNull(f.preferences.loadActiveVehicleId())
        val confirmed = f.database.vehicleDao().getFirstVehicle()
        f.preferences.selectionError = null
        onMain { vm.saveInitialVehicle(NEW.copy(brand = "Must not overwrite")) }
        assertEquals(saved.vehicleId, f.saved(vm, active = true).vehicleId)
        onMain { vm.saveInitialVehicle(NEW); vm.retryInitialVehicleActivation() }
        assertEquals(confirmed, f.database.vehicleDao().getFirstVehicle())
        assertEquals(1, f.inserts.get())
        assertEquals(0, f.updates.get())
        assertEquals(1, f.preferences.legacyWrites)
        assertEquals(0, f.deletes.get())
    }

    @Test fun failedLegacyWriteDoesNotCreateVehicleAndCanRetry() = fixture { f ->
        val vm = f.home()
        f.preferences.legacyError = IOException("legacy failure")
        onMain { vm.saveInitialVehicle(NEW) }
        await { vm.vehicleSaveError.first { it != null } }
        assertFalse(vm.isSavingVehicle.value)
        assertEquals(VehicleCreationState.Editing, vm.initialVehicleCreation.value)
        assertNull(f.database.vehicleDao().getFirstVehicle())
        f.preferences.legacyError = null
        onMain { vm.saveInitialVehicle(NEW) }
        f.saved(vm, active = true)
        assertEquals(1, f.inserts.get())
    }

    @Test fun failedInsertDoesNotClaimSavedAndCanRetry() = fixture { f ->
        val vm = f.home()
        f.insertError = SQLiteException("insert failure")
        onMain { vm.saveInitialVehicle(NEW) }
        await { vm.vehicleSaveError.first { it != null } }
        assertFalse(vm.isSavingVehicle.value)
        assertEquals(VehicleCreationState.Editing, vm.initialVehicleCreation.value)
        assertNull(f.database.vehicleDao().getFirstVehicle())
        assertNull(f.preferences.loadActiveVehicleId())
        f.insertError = null
        onMain { vm.saveInitialVehicle(NEW) }
        f.saved(vm, active = true)
        assertEquals(1, f.database.vehicleDao().observeAllVehicles().first().size)
    }

    @Test fun repeatedClicksDuringInsertAndAfterSuccessDoNotRepeatInsert() = fixture { f ->
        val vm = f.home()
        val gate = Gate()
        f.insertGate = gate
        onMain { vm.saveInitialVehicle(NEW) }
        await { gate.entered.await() }
        onMain { repeat(4) { vm.saveInitialVehicle(NEW) } }
        assertEquals(1, f.inserts.get())
        assertTrue(vm.isSavingVehicle.value)
        gate.finish.complete(Unit)
        f.saved(vm, active = true)
        onMain { vm.saveInitialVehicle(NEW) }
        assertEquals(1, f.inserts.get())
    }

    @Test fun cancelledActivationRestoresFlagsAndNewViewModelReusesRoomId() = fixture { f ->
        val vm = f.home()
        val gate = Gate()
        f.preferences.selectionGate = gate
        onMain { vm.saveInitialVehicle(NEW) }
        await { gate.entered.await() }
        val stored = vm.initialVehicleCreation.value as VehicleCreationState.Saved
        requireNotNull(vm.viewModelScope.coroutineContext[Job]).cancelAndJoin()
        assertFalse(vm.isSavingVehicle.value)
        assertNull(vm.vehicleSaveError.value)
        assertNull(f.preferences.loadActiveVehicleId())
        f.preferences.selectionGate = null
        val replacement = f.home()
        onMain { replacement.saveInitialVehicle(NEW.copy(model = "Must not overwrite")) }
        assertEquals(stored.vehicleId, f.saved(replacement, active = true).vehicleId)
        assertEquals(NEW.copy(id = stored.vehicleId), f.database.vehicleDao().getFirstVehicle()?.toVehicle())
        assertEquals(1, f.inserts.get())
        assertEquals(0, f.updates.get())
    }

    @Test fun cancellationDuringInsertStillAcknowledgesCommitWithoutActivating() = fixture { f ->
        val vm = f.home()
        val gate = Gate()
        f.insertGate = gate
        onMain { vm.saveInitialVehicle(NEW) }
        await { gate.entered.await() }
        val cancelled = requireNotNull(vm.viewModelScope.coroutineContext[Job])
        cancelled.cancel()
        gate.finish.complete(Unit)
        cancelled.join()
        assertTrue(vm.initialVehicleCreation.value is VehicleCreationState.Saved)
        assertFalse((vm.initialVehicleCreation.value as VehicleCreationState.Saved).isActive)
        assertFalse(vm.isSavingVehicle.value)
        assertNull(f.preferences.loadActiveVehicleId())
        assertEquals(1, f.database.vehicleDao().observeAllVehicles().first().size)
    }

    @Test fun reopenedRoomDataStoreRepositoryAndNewViewModelDoNotDuplicatePartialSetup() = fixture { f ->
        val vm = f.home()
        f.preferences.selectionError = IOException("selection")
        onMain { vm.saveInitialVehicle(NEW) }
        val stored = f.saved(vm, active = false)
        f.restart()
        val replacement = f.home()
        await { replacement.vehicleLoadState.first { it is VehicleLoadState.Ready } }
        assertEquals(stored.vehicleId, f.repository.activeVehicleId.value)
        assertFalse(shouldShowSetup(replacement.vehicleLoadState.value, ResetState.IDLE))
        // Even a stale submission from a restored form cannot create or edit another row.
        onMain { replacement.saveInitialVehicle(NEW.copy(model = "Must not overwrite")) }
        assertEquals(NEW.copy(id = stored.vehicleId), f.database.vehicleDao().getFirstVehicle()?.toVehicle())
        assertEquals(1, f.inserts.get())
        assertEquals(0, f.updates.get())
        assertEquals(0, f.deletes.get())
    }

    @Test fun restartWithPersistingActivationErrorUsesLoadErrorThenRecoversWithoutInsert() = fixture { f ->
        val vm = f.home()
        f.preferences.selectionError = IOException("selection")
        onMain { vm.saveInitialVehicle(NEW) }
        f.saved(vm, active = false)
        f.restart(selectionError = IOException("still unavailable"))
        val restored = f.home()
        assertTrue(restored.vehicleLoadState.value is VehicleLoadState.Error)
        assertFalse(shouldShowSetup(restored.vehicleLoadState.value, ResetState.IDLE))
        f.preferences.selectionError = null
        onMain { restored.retryVehicleLoad() }
        await { restored.vehicleLoadState.first { it is VehicleLoadState.Ready } }
        assertEquals(1, f.inserts.get())
        assertEquals(1, f.database.vehicleDao().observeAllVehicles().first().size)
    }

    @Test fun productionSetupSurvivesActivityRecreationDuringActivationAndRetries() = fixture { f ->
        f.withActivity { scenario, reference ->
            val gate = Gate(IOException("selection"))
            f.preferences.selectionGate = gate
            scenario.onActivity { reference.get().saveInitialVehicle(NEW) }
            await { gate.entered.await() }
            val previous = reference.get()
            scenario.recreate()
            scenario.onActivity { assertSame(previous, reference.get()) }
            awaitText("El vehículo está guardado.")
            gate.finish.complete(Unit)
            f.saved(previous, active = false)
            awaitText("El vehículo está guardado, pero no pudo activarse. Puedes reintentar la activación.")
            f.preferences.selectionGate = null
            click("Reintentar activación")
            f.saved(previous, active = true)
            awaitText("Inicio de prueba")
            assertEquals(1, f.inserts.get())
        }
    }

    @Test fun productionSetupSurvivesActivityRecreationDuringInsertWithoutResubmission() = fixture { f ->
        f.withActivity { scenario, reference ->
            val gate = Gate()
            f.insertGate = gate
            scenario.onActivity { reference.get().saveInitialVehicle(NEW) }
            await { gate.entered.await() }
            val previous = reference.get()
            scenario.recreate()
            scenario.onActivity { assertSame(previous, reference.get()) }
            gate.finish.complete(Unit)
            f.saved(previous, active = true)
            awaitText("Inicio de prueba")
            assertEquals(1, f.inserts.get())
        }
    }

    @Test fun notesIOExceptionRemainsVisibleAndRetryPersistsOnlyAfterSuccess() = fixture(notes = true) { f ->
        val vm = f.home()
        f.preferences.notesError = IOException("private storage path")
        onMain { vm.dismissReleaseNotes() }
        await { vm.releaseNotesDismissError.first { it != null } }
        assertFalse(vm.isDismissingReleaseNotes.value)
        assertTrue(vm.showReleaseNotes.value)
        assertEquals(OLD_NOTES, f.preferences.lastSeenReleaseNotesVersion())
        assertFalse(vm.releaseNotesDismissError.value!!.contains("private"))
        f.preferences.notesError = null
        onMain { vm.dismissReleaseNotes() }
        await { vm.showReleaseNotes.first { !it } }
        assertNull(vm.releaseNotesDismissError.value)
        assertEquals(RELEASE_NOTES_VERSION, f.preferences.lastSeenReleaseNotesVersion())
    }

    @Test fun repeatedNotesClicksDoNotQueueConcurrentWrites() = fixture(notes = true) { f ->
        val vm = f.home()
        val gate = Gate()
        f.preferences.notesGate = gate
        onMain { vm.dismissReleaseNotes() }
        await { gate.entered.await() }
        onMain { repeat(4) { vm.dismissReleaseNotes() } }
        assertEquals(1, f.preferences.notesAttempts)
        gate.finish.complete(Unit)
        await { vm.showReleaseNotes.first { !it } }
        assertEquals(1, f.preferences.notesAttempts)
        assertFalse(vm.isDismissingReleaseNotes.value)
    }

    @Test fun cancelledNotesDismissRestoresFlagsAndPreservesPreviousVersion() = fixture(notes = true) { f ->
        val vm = f.home()
        val gate = Gate()
        f.preferences.notesGate = gate
        onMain { vm.dismissReleaseNotes() }
        await { gate.entered.await() }
        requireNotNull(vm.viewModelScope.coroutineContext[Job]).cancelAndJoin()
        assertFalse(vm.isDismissingReleaseNotes.value)
        assertNull(vm.releaseNotesDismissError.value)
        assertTrue(vm.showReleaseNotes.value)
        assertEquals(OLD_NOTES, f.preferences.lastSeenReleaseNotesVersion())
        f.preferences.notesGate = null
        val replacement = f.home()
        onMain { replacement.dismissReleaseNotes() }
        await { replacement.showReleaseNotes.first { !it } }
    }

    @Test fun alreadyCancelledViewModelDoesNotWriteOrLeaveEitherActionLocked() = fixture { f ->
        val vm = f.home()
        requireNotNull(vm.viewModelScope.coroutineContext[Job]).cancelAndJoin()
        onMain { vm.saveInitialVehicle(NEW); vm.dismissReleaseNotes() }
        assertFalse(vm.isSavingVehicle.value)
        assertFalse(vm.isDismissingReleaseNotes.value)
        assertEquals(0, f.inserts.get())
        assertEquals(0, f.preferences.legacyWrites)
        assertEquals(0, f.preferences.notesAttempts)
    }

    @Test fun productionNotesDialogKeepsErrorAcrossActivityRecreationAndRetries() = fixture(notes = true) { f ->
        f.withActivity { scenario, reference ->
            f.preferences.notesError = IOException("notes")
            awaitText("Entendido")
            click("Entendido")
            awaitText("No se pudo guardar la confirmación. Pulsa Entendido para reintentar.")
            val previous = reference.get()
            scenario.recreate()
            scenario.onActivity { assertSame(previous, reference.get()) }
            awaitText("No se pudo guardar la confirmación. Pulsa Entendido para reintentar.")
            assertEquals(OLD_NOTES, f.preferences.lastSeenReleaseNotesVersion())
            f.preferences.notesError = null
            click("Entendido")
            await { previous.showReleaseNotes.first { !it } }
            assertEquals(RELEASE_NOTES_VERSION, f.preferences.lastSeenReleaseNotesVersion())
        }
    }

    private fun fixture(notes: Boolean = false, test: suspend (Fixture) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "initial-setup-test-").toFile()
        val f = Fixture(directory)
        try { f.open(notes); test(f) } finally {
            OperationTestActivity.content = null
            f.close()
            withContext(Dispatchers.IO) { check(directory.deleteRecursively()) }
        }
    }

    private class Gate(private val failure: IOException? = null) {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        suspend fun enter() { entered.complete(Unit); finish.await(); failure?.let { throw it } }
    }

    private class Fixture(private val directory: File) {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private var repositoryScope = newScope()
        private var preferencesScope = newScope()
        private var store = ViewModelStore()
        lateinit var database: HybridCarDatabase
            private set
        lateinit var repository: VehicleRepository
            private set
        lateinit var preferences: FaultPreferences
            private set
        val inserts = AtomicInteger()
        val updates = AtomicInteger()
        val deletes = AtomicInteger()
        var insertError: SQLiteException? = null
        var insertGate: Gate? = null

        suspend fun open(notes: Boolean = false, selectionError: IOException? = null) {
            database = Room.databaseBuilder(context, HybridCarDatabase::class.java, File(directory, "personal.db").absolutePath).build()
            preferences = FaultPreferences(VehiclePreferences(PreferenceDataStoreFactory.create(
                scope = preferencesScope, produceFile = { File(directory, "vehicle.preferences_pb") }
            )))
            if (notes) {
                database.vehicleDao().insert(NEW.toEntity(id = 1, createdAt = 1))
                preferences.saveActiveVehicleId(1)
                preferences.markReleaseNotesAsSeen(OLD_NOTES)
            }
            preferences.selectionError = selectionError
            val delegate = database.vehicleDao()
            val dao = object : VehicleDao by delegate {
                override suspend fun insert(vehicle: VehicleEntity): Long {
                    inserts.incrementAndGet(); insertError?.let { throw it }; insertGate?.enter()
                    return delegate.insert(vehicle)
                }
                override suspend fun update(vehicle: VehicleEntity) { updates.incrementAndGet(); delegate.update(vehicle) }
                override suspend fun delete(vehicle: VehicleEntity) { deletes.incrementAndGet(); delegate.delete(vehicle) }
                override suspend fun deleteAll() { deletes.incrementAndGet(); delegate.deleteAll() }
            }
            repository = VehicleRepository(EmptyCatalog, preferences, dao, database.fuelEntryDao(), database.maintenanceDao(), repositoryScope)
            await { repository.loadState.first { it !is VehicleLoadState.Loading } }
            preferences.notesAttempts = 0
        }

        fun home(): HomeViewModel = onMain {
            HomeViewModel(FuelRepository(database.fuelEntryDao()), repository).also { store.put("home${store.keys().size}", it) }
        }

        suspend fun saved(vm: HomeViewModel, active: Boolean): VehicleCreationState.Saved {
            val result = await { vm.initialVehicleCreation.first { it is VehicleCreationState.Saved && it.isActive == active } }
            await { vm.isSavingVehicle.first { !it } }
            return result as VehicleCreationState.Saved
        }

        suspend fun restart(selectionError: IOException? = null) {
            close()
            repositoryScope = newScope(); preferencesScope = newScope(); store = ViewModelStore()
            open(selectionError = selectionError)
        }

        suspend fun close() {
            insertGate?.finish?.complete(Unit)
            if (::preferences.isInitialized) {
                preferences.selectionGate?.finish?.complete(Unit)
                preferences.notesGate?.finish?.complete(Unit)
            }
            onMain { store.clear() }
            requireNotNull(repositoryScope.coroutineContext[Job]).cancelAndJoin()
            requireNotNull(preferencesScope.coroutineContext[Job]).cancelAndJoin()
            if (::database.isInitialized) withContext(Dispatchers.IO) { database.close() }
        }

        suspend fun withActivity(test: suspend (ActivityScenario<OperationTestActivity>, AtomicReference<HomeViewModel>) -> Unit) {
            val reference = AtomicReference<HomeViewModel>()
            OperationTestActivity.content = { activity -> Content(activity, reference) }
            try { ActivityScenario.launch(OperationTestActivity::class.java).use { test(it, reference) } }
            finally { OperationTestActivity.content = null }
        }

        @Composable
        private fun Content(activity: OperationTestActivity, reference: AtomicReference<HomeViewModel>) {
            SideEffect { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            val vm = ViewModelProvider(activity, viewModelFactory {
                initializer { HomeViewModel(FuelRepository(database.fuelEntryDao()), repository) }
            })[HomeViewModel::class.java]
            reference.set(vm)
            val loaded by vm.vehicleLoadState.collectAsStateWithLifecycle()
            val showNotes by vm.showReleaseNotes.collectAsStateWithLifecycle()
            val dismissing by vm.isDismissingReleaseNotes.collectAsStateWithLifecycle()
            val dismissError by vm.releaseNotesDismissError.collectAsStateWithLifecycle()
            MaterialTheme {
                if (shouldShowSetup(loaded, ResetState.IDLE)) SetupScreen(vm)
                else Text("Inicio de prueba")
                if (showNotes) ReleaseNotesDialog(vm::dismissReleaseNotes, dismissing, dismissError)
            }
        }
    }

    private class FaultPreferences(private val delegate: VehiclePreferencesStore) : VehiclePreferencesStore by delegate {
        var selectionError: IOException? = null
        var legacyError: IOException? = null
        var notesError: IOException? = null
        var selectionGate: Gate? = null
        var notesGate: Gate? = null
        var legacyWrites = 0
        var notesAttempts = 0
        override suspend fun saveVehicle(vehicle: Vehicle) {
            legacyError?.let { throw it }; legacyWrites++; delegate.saveVehicle(vehicle)
        }
        override suspend fun saveActiveVehicleId(vehicleId: Long) {
            selectionGate?.enter(); selectionError?.let { throw it }; delegate.saveActiveVehicleId(vehicleId)
        }
        override suspend fun markReleaseNotesAsSeen(versionName: String) {
            notesAttempts++; notesGate?.enter(); notesError?.let { throw it }; delegate.markReleaseNotesAsSeen(versionName)
        }
        override suspend fun clearVehicle() { error("Unexpected preference deletion") }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }

    companion object {
        private const val OLD_NOTES = "1.4.0"
        private val NEW = Vehicle(brand = "Isolated", model = "Setup", year = 2026, type = VehicleType.GASOLINA, fuelTankCapacity = 40.0, initialKm = 1000.0)
        private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private suspend fun <T> await(action: suspend () -> T): T = withTimeout(10.seconds) { action() }
        private fun <T> onMain(action: () -> T): T {
            val result = AtomicReference<T>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
            return result.get()
        }
        private fun nodes(): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            return WindowInspector.getGlobalWindowViews().flatMap(::views).filterIsInstance<RootForTest>()
                .flatMap { descendants(it.semanticsOwner.unmergedRootSemanticsNode) }
        }
        private fun matches(node: SemanticsNode, text: String) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        private suspend fun awaitText(text: String) = await {
            while (!withContext(Dispatchers.Main.immediate) { nodes().any { matches(it, text) } }) delay(16.milliseconds)
        }
        private suspend fun click(text: String) = await {
            var clicked = false
            while (!clicked) {
                clicked = withContext(Dispatchers.Main.immediate) {
                    val all = nodes()
                    fun containsText(node: SemanticsNode): Boolean = matches(node, text) || node.children.any(::containsText)
                    all.firstOrNull { containsText(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
                        ?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
                }
                if (!clicked) delay(16.milliseconds)
            }
        }
    }
}
