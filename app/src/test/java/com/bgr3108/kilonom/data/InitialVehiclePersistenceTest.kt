package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.VehicleDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class InitialVehiclePersistenceTest {
    @Test fun emptySetupAcknowledgesRoomBeforeSelectingAndKeepsLegacySnapshot() = fixture { f ->
        var acknowledged: VehicleEntity? = null
        f.preferences.beforeSelection = {
            assertNotNull(acknowledged)
            assertEquals(acknowledged, f.dao.getFirstVehicle())
            assertNull(f.repository.activeVehicleId.value)
        }
        val stored = f.repository.saveVehicle(VEHICLE) { acknowledged = it }
        assertEquals(stored.id, f.repository.activeVehicleId.value)
        assertEquals(VEHICLE, f.preferences.legacy)
        assertEquals(1, f.dao.inserts)
    }

    @Test fun failedActivationAndRepositoryRetryReuseTheConfirmedRowWithoutChangingIt() = fixture { f ->
        f.preferences.selectionError = IOException("selection")
        var id: Long? = null
        assertTrue(runCatching { f.repository.saveVehicle(VEHICLE) { id = it.id } }.isFailure)
        val confirmed = f.dao.getFirstVehicle()
        assertEquals(confirmed?.id, id)
        assertNull(f.repository.activeVehicleId.value)
        f.preferences.selectionError = null
        val retried = f.repository.saveVehicle(VEHICLE.copy(brand = "Must not overwrite"))
        assertEquals(confirmed, retried)
        assertEquals(confirmed, f.dao.getFirstVehicle())
        assertEquals(1, f.dao.inserts)
        assertEquals(0, f.dao.updates)
        assertEquals(VEHICLE, f.preferences.legacy)
        assertEquals(0, f.dao.deletes)
    }

    @Test fun failedRoomReadDoesNotWriteLegacyOrInsert() = fixture { f ->
        f.dao.readError = IOException("read")
        assertTrue(runCatching { f.repository.saveVehicle(VEHICLE) }.isFailure)
        assertEquals(Vehicle(), f.preferences.legacy)
        assertEquals(0, f.dao.inserts)
    }

    @Test fun failedLegacyWriteDoesNotInsertOrAcknowledge() = fixture { f ->
        f.preferences.legacyError = IOException("legacy")
        var acknowledged = false
        assertTrue(runCatching { f.repository.saveVehicle(VEHICLE) { acknowledged = true } }.isFailure)
        assertFalse(acknowledged)
        assertEquals(0, f.dao.inserts)
    }

    @Test fun failedInsertDoesNotAcknowledgeOrSelect() = fixture { f ->
        f.dao.insertError = IOException("insert")
        var acknowledged = false
        assertTrue(runCatching { f.repository.saveVehicle(VEHICLE) { acknowledged = true } }.isFailure)
        assertFalse(acknowledged)
        assertNull(f.preferences.activeId)
        assertNull(f.dao.getFirstVehicle())
        assertEquals(VEHICLE, f.preferences.legacy)
    }

    @Test fun cancellationAtAcknowledgementKeepsRoomAndCanRetryOnlySelection() = fixture { f ->
        coroutineScope {
            val operation = async {
                val job = currentCoroutineContext()[Job]!!
                f.repository.saveVehicle(VEHICLE) { job.cancel() }
            }
            assertTrue(runCatching { operation.await() }.exceptionOrNull() is CancellationException)
        }
        val row = requireNotNull(f.dao.getFirstVehicle())
        assertNull(f.preferences.activeId)
        assertTrue(f.repository.activateCreatedVehicle(row.id))
        assertEquals(1, f.dao.inserts)
        assertEquals(0, f.dao.deletes)
    }

    @Test fun recoveryAnchorIsConfiguredWithoutReplacingItsIdentity() = fixture { f ->
        f.dao.delegate.insert(Vehicle().toEntity(id = 7, createdAt = 0))
        val result = f.repository.saveVehicle(VEHICLE)
        assertEquals(7L, result.id)
        assertEquals(0, f.dao.inserts)
        assertEquals(1, f.dao.updates)
        assertEquals(VEHICLE.copy(id = 7), result.toVehicle())
    }

    @Test fun incompleteSnapshotStillAllowsSetupWithoutCreatingAnotherRow() = fixture { f ->
        f.dao.delegate.insert(Vehicle(brand = "Legacy", model = "Incomplete").toEntity(id = 7, createdAt = 0))
        val result = f.repository.saveVehicle(VEHICLE)
        assertEquals(VEHICLE.copy(id = 7), result.toVehicle())
        assertEquals(0, f.dao.inserts)
        assertEquals(1, f.dao.updates)
    }

    @Test fun notesIOExceptionPreservesPreviousVersionAndAllowsRetry() = fixture(oldNotes = "1.4.0") { f ->
        f.preferences.notesError = IOException("notes")
        assertTrue(runCatching { f.repository.dismissReleaseNotes() }.exceptionOrNull() is IOException)
        assertEquals("1.4.0", f.preferences.notesVersion)
        assertTrue(f.repository.showReleaseNotes.value)
        f.preferences.notesError = null
        f.repository.dismissReleaseNotes()
        assertEquals(RELEASE_NOTES_VERSION, f.preferences.notesVersion)
        assertFalse(f.repository.showReleaseNotes.value)
    }

    @Test fun notesCancellationPropagatesAndDoesNotMarkSeen() = fixture(oldNotes = "1.4.0") { f ->
        f.preferences.notesError = CancellationException("cancel")
        assertTrue(runCatching { f.repository.dismissReleaseNotes() }.exceptionOrNull() is CancellationException)
        assertEquals("1.4.0", f.preferences.notesVersion)
        assertTrue(f.repository.showReleaseNotes.value)
        f.preferences.notesError = null
        f.repository.dismissReleaseNotes()
        assertFalse(f.repository.showReleaseNotes.value)
    }

    @Test fun simultaneousNotesDismissalsWriteTheVersionOnce() = fixture(oldNotes = "1.4.0") { f ->
        coroutineScope {
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            f.preferences.beforeNotes = { entered.complete(Unit); finish.await() }
            val first = async { f.repository.dismissReleaseNotes() }
            withTimeout(5.seconds) { entered.await() }
            val second = async { f.repository.dismissReleaseNotes() }
            finish.complete(Unit)
            first.await(); second.await()
        }
        assertEquals(1, f.preferences.notesWrites)
    }

    private fun fixture(oldNotes: String? = null, test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture(oldNotes)
        try {
            withTimeout(5.seconds) { f.repository.loadState.first { it !is VehicleLoadState.Loading } }
            test(f)
        } finally { requireNotNull(f.scope.coroutineContext[Job]).cancelAndJoin() }
    }

    private class Fixture(oldNotes: String?) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dao = TrackingDao()
        val preferences = Preferences(oldNotes)
        val repository = VehicleRepository(EmptyCatalog, preferences, dao, InMemoryFuelEntryDao(), InMemoryMaintenanceDao(), scope)
    }

    private class TrackingDao(val delegate: InMemoryVehicleDao = InMemoryVehicleDao()) : VehicleDao by delegate {
        var readError: IOException? = null
        var insertError: IOException? = null
        var inserts = 0
        var updates = 0
        var deletes = 0
        override suspend fun getFirstVehicle(): VehicleEntity? { readError?.let { throw it }; return delegate.getFirstVehicle() }
        override suspend fun insert(vehicle: VehicleEntity): Long { insertError?.let { throw it }; inserts++; return delegate.insert(vehicle) }
        override suspend fun update(vehicle: VehicleEntity) { updates++; delegate.update(vehicle) }
        override suspend fun delete(vehicle: VehicleEntity) { deletes++; delegate.delete(vehicle) }
    }

    private class Preferences(var notesVersion: String?) : VehiclePreferencesStore {
        var legacy = Vehicle()
        var activeId: Long? = null
        var selectionError: IOException? = null
        var legacyError: IOException? = null
        var notesError: Exception? = null
        var beforeSelection: suspend () -> Unit = {}
        var beforeNotes: suspend () -> Unit = {}
        var notesWrites = 0
        override suspend fun loadVehicle() = legacy
        override suspend fun saveVehicle(vehicle: Vehicle) { legacyError?.let { throw it }; legacy = vehicle }
        override suspend fun clearVehicle() { error("Unexpected deletion") }
        override suspend fun loadActiveVehicleId() = activeId
        override suspend fun saveActiveVehicleId(vehicleId: Long) { beforeSelection(); selectionError?.let { throw it }; activeId = vehicleId }
        override suspend fun hasStoredAppState() = notesVersion != null
        override suspend fun lastSeenReleaseNotesVersion() = notesVersion
        override suspend fun markReleaseNotesAsSeen(versionName: String) {
            beforeNotes(); notesError?.let { throw it }; notesWrites++; notesVersion = versionName
        }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }

    companion object {
        private val VEHICLE = Vehicle(brand = "Test", model = "Model", year = 2026, type = VehicleType.GASOLINA, fuelTankCapacity = 40.0, initialKm = 500.0)
    }
}
