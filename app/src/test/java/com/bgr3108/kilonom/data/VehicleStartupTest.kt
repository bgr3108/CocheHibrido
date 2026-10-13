package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.database.FuelEntryDao
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class VehicleStartupTest {
    @Test
    fun emptyInstallation_isConfirmedOnlyAfterSuccessfulReads() = runBlocking {
        withFixture { repository, dao, _, _ ->
            assertEquals(VehicleLoadState.Empty, repository.completedLoad())
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun existingVehicles_loadTheSelectedSnapshotAndSummaries() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle)) { repository, dao, _, _ ->
            assertEquals(VehicleLoadState.Ready(vehicle), repository.completedLoad())
            assertEquals(vehicle, repository.activeVehicle.value)
            assertEquals(listOf(vehicle), repository.vehicleSummaries.value.map { it.vehicle })
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun initialDaoReadFailure_doesNotImportLegacyDataOrWritePreferences() = runBlocking {
        val error = IOException("Room read failed")
        withFixture(legacy = legacyVehicle(), prepare = { dao, _ -> dao.firstReadError = error }) { repository, dao, prefs, _ ->
            assertSame(error, (repository.completedLoad() as VehicleLoadState.Error).cause)
            assertTrue(dao.rows.isEmpty())
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun initialSummaryFlowFailure_doesNotEnrichTheRecoveryAnchor() = runBlocking {
        val recovery = Vehicle().toEntity(id = 1, createdAt = 0)
        withFixture(rows = listOf(recovery), legacy = legacyVehicle(), prepare = { dao, _ ->
            dao.summaryError.value = IOException("Room flow failed")
        }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(listOf(recovery), dao.rows)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun initialActiveVehicleFlowFailure_doesNotWriteAnyBootstrapState() = runBlocking {
        withFixture(rows = listOf(storedVehicle()), prepare = { dao, _ ->
            dao.activeError.value = IOException("active observation failed")
        }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun preferenceFailureWithAnEmptyDatabase_isAnErrorAndPerformsNoWrites() = runBlocking {
        withFixture(legacy = legacyVehicle(), prepare = { _, prefs -> prefs.readError = IOException("DataStore read failed") }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun activeIdReadFailure_preservesExistingRowsAndPreferences() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle), prepare = { _, prefs -> prefs.activeIdError = IOException("active id unavailable") }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(listOf(vehicle), dao.rows)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun releaseNotesReadFailure_happensBeforeAnyLegacyImport() = runBlocking {
        withFixture(legacy = legacyVehicle(), prepare = { _, prefs -> prefs.notesError = IOException("preferences unavailable") }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun retryAfterDaoRecovery_loadsExistingVehiclesWithoutCreatingAnother() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle), prepare = { dao, _ -> dao.firstReadError = IOException("temporary failure") }) { repository, dao, _, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            dao.firstReadError = null
            repository.retryVehicleLoad()
            assertEquals(VehicleLoadState.Ready(vehicle), repository.completedLoad())
            assertEquals(listOf(vehicle), dao.rows)
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun retryAfterPreferenceRecovery_importsLegacyVehicleExactlyOnce() = runBlocking {
        withFixture(legacy = legacyVehicle(), prepare = { _, prefs -> prefs.readError = IOException("temporary failure") }) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            prefs.readError = null
            repository.retryVehicleLoad()
            repository.retryVehicleLoad()
            assertTrue(repository.completedLoad() is VehicleLoadState.Ready)
            repository.retryVehicleLoad()
            assertEquals(1, dao.rows.size)
            assertEquals(1, dao.writes)
        }
    }

    @Test
    fun failedRoomObservation_retainsLastGoodSnapshotsAndRestartsOnRetry() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle)) { repository, dao, _, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Ready)
            val summaries = repository.vehicleSummaries.value
            dao.summaryError.value = IOException("later observation failed")
            withTimeout(5.seconds) { repository.loadState.first { it is VehicleLoadState.Error } }
            assertEquals(vehicle, repository.activeVehicle.value)
            assertEquals(summaries, repository.vehicleSummaries.value)
            val previousCollections = dao.summaryCollections
            dao.summaryError.value = null
            repository.retryVehicleLoad()
            assertEquals(VehicleLoadState.Ready(vehicle), repository.completedLoad())
            assertTrue(dao.summaryCollections > previousCollections)
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun failedActiveVehicleObservation_canBeRetriedWithoutClearingTheVehicle() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle)) { repository, dao, _, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Ready)
            dao.activeError.value = IOException("later active observation failed")
            withTimeout(5.seconds) { repository.loadState.first { it is VehicleLoadState.Error } }
            assertEquals(vehicle, repository.activeVehicle.value)
            dao.activeError.value = null
            repository.retryVehicleLoad()
            assertEquals(VehicleLoadState.Ready(vehicle), repository.completedLoad())
        }
    }

    @Test
    fun associatedUiReadFailure_isExposedAndTheReadRestartsOnRetry() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle)) { repository, dao, _, scope ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Ready)
            var failRead = true
            var successfulReads = 0
            val observed = CompletableDeferred<VehicleEntity?>()
            scope.launch {
                repository.observeLoadedVehicle { active ->
                    flow {
                        if (failRead) throw IOException("consumer Room read failed")
                        successfulReads++
                        emit(active)
                    }
                }.collect { observed.complete(it) }
            }
            withTimeout(5.seconds) { repository.loadState.first { it is VehicleLoadState.Error } }
            assertEquals(vehicle, repository.activeVehicle.value)
            failRead = false
            repository.retryVehicleLoad()
            assertEquals(vehicle, withTimeout(5.seconds) { observed.await() })
            assertEquals(1, successfulReads)
            assertEquals(0, dao.writes)
        }
    }

    @Test
    fun failedInitialHistoryRead_preservesTheRecoveryVehicleAndItsEntries() = runBlocking {
        val recovery = Vehicle().toEntity(id = 1, createdAt = 0)
        val entry = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = 1)
        val storedEntries = InMemoryFuelEntryDao(mutableListOf(entry))
        val failingEntries = object : FuelEntryDao by storedEntries {
            override fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>> = flow { throw IOException("history read failed") }
        }
        withFixture(rows = listOf(recovery), legacy = legacyVehicle(), fuelDao = failingEntries) { repository, dao, prefs, _ ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Error)
            assertEquals(listOf(recovery), dao.rows)
            assertEquals(listOf(entry), storedEntries.entries)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
        }
    }

    @Test
    fun cancelledAssociatedRead_propagatesWithoutPublishingAStorageError() = runBlocking {
        val vehicle = storedVehicle()
        withFixture(rows = listOf(vehicle)) { repository, _, _, scope ->
            assertTrue(repository.completedLoad() is VehicleLoadState.Ready)
            val readStarted = CompletableDeferred<Unit>()
            var readCancelled = false
            val consumer = scope.launch {
                repository.observeLoadedVehicle<VehicleEntity?> {
                    flow {
                        readStarted.complete(Unit)
                        try { awaitCancellation() } finally { readCancelled = true }
                    }
                }.collect {}
            }
            withTimeout(5.seconds) { readStarted.await() }
            consumer.cancelAndJoin()
            assertTrue(consumer.isCancelled)
            assertTrue(readCancelled)
            assertEquals(VehicleLoadState.Ready(vehicle), repository.loadState.value)
        }
    }

    @Test
    fun cancellationDuringInitialReads_propagatesAndDoesNotWriteOrPublishEmpty() = runBlocking {
        withFixture(legacy = legacyVehicle(), prepare = { _, prefs -> prefs.readGate = CompletableDeferred() }) { repository, dao, prefs, scope ->
            withTimeout(5.seconds) { prefs.readStarted.await() }
            val job = requireNotNull(scope.coroutineContext[Job])
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertTrue(prefs.readCancelled)
            assertEquals(VehicleLoadState.Loading, repository.loadState.value)
            assertEquals(0, dao.writes)
            assertEquals(0, prefs.writes)
            assertFalse(repository.loadState.value is VehicleLoadState.Error)
        }
    }

    private suspend fun VehicleRepository.completedLoad(): VehicleLoadState =
        withTimeout(5.seconds) { loadState.first { it !is VehicleLoadState.Loading } }

    private suspend fun withFixture(
        rows: List<VehicleEntity> = emptyList(),
        legacy: Vehicle = Vehicle(),
        fuelDao: FuelEntryDao = InMemoryFuelEntryDao(),
        prepare: (ReadFailureDao, ReadFailurePreferences) -> Unit = { _, _ -> },
        test: suspend (VehicleRepository, ReadFailureDao, ReadFailurePreferences, CoroutineScope) -> Unit
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dao = ReadFailureDao(InMemoryVehicleDao(rows))
        val prefs = ReadFailurePreferences(legacy)
        prepare(dao, prefs)
        val repository = VehicleRepository(EmptyCatalog, prefs, dao, fuelDao, InMemoryMaintenanceDao(), scope)
        try {
            test(repository, dao, prefs, scope)
        } finally {
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    private fun legacyVehicle() = Vehicle(brand = "Marca", model = "Modelo", year = 2026, type = VehicleType.GASOLINA, initialKm = 1_000.0)
    private fun storedVehicle() = legacyVehicle().toEntity(id = 1, createdAt = 1)

    private class ReadFailureDao(private val delegate: InMemoryVehicleDao) : VehicleDao by delegate {
        val rows: List<VehicleEntity> get() = delegate.vehicles.toList()
        var firstReadError: IOException? = null
        val summaryError = MutableStateFlow<IOException?>(null)
        val activeError = MutableStateFlow<IOException?>(null)
        var summaryCollections = 0
        var writes = 0

        override suspend fun getFirstVehicle(): VehicleEntity? {
            firstReadError?.let { throw it }
            return delegate.getFirstVehicle()
        }

        override fun observeAllVehicles(): Flow<List<VehicleEntity>> {
            summaryCollections++
            return combine(delegate.observeAllVehicles(), summaryError) { rows, error ->
                error?.let { throw it }
                rows
            }
        }

        override fun observeVehicle(id: Long): Flow<VehicleEntity?> =
            combine(delegate.observeVehicle(id), activeError) { vehicle, error ->
                error?.let { throw it }
                vehicle
            }

        override suspend fun insert(vehicle: VehicleEntity): Long { writes++; return delegate.insert(vehicle) }
        override suspend fun update(vehicle: VehicleEntity) { writes++; delegate.update(vehicle) }
        override suspend fun delete(vehicle: VehicleEntity) { writes++; delegate.delete(vehicle) }
        override suspend fun deleteAll() { writes++; delegate.deleteAll() }
    }

    private class ReadFailurePreferences(private val legacy: Vehicle) : VehiclePreferencesStore {
        var readError: IOException? = null
        var activeIdError: IOException? = null
        var notesError: IOException? = null
        var readGate: CompletableDeferred<Unit>? = null
        val readStarted = CompletableDeferred<Unit>()
        var readCancelled = false
        var writes = 0

        override suspend fun loadVehicle(): Vehicle {
            readStarted.complete(Unit)
            try { readGate?.await() } catch (error: kotlinx.coroutines.CancellationException) {
                readCancelled = true
                throw error
            }
            readError?.let { throw it }
            return legacy
        }

        override suspend fun loadActiveVehicleId(): Long? {
            activeIdError?.let { throw it }
            return null
        }
        override suspend fun lastSeenReleaseNotesVersion(): String {
            notesError?.let { throw it }
            return RELEASE_NOTES_VERSION
        }
        override suspend fun hasStoredAppState(): Boolean = legacy != Vehicle()
        override suspend fun saveVehicle(vehicle: Vehicle) { writes++ }
        override suspend fun saveActiveVehicleId(vehicleId: Long) { writes++ }
        override suspend fun markReleaseNotesAsSeen(versionName: String) { writes++ }
        override suspend fun clearVehicle() { writes++ }
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
}
