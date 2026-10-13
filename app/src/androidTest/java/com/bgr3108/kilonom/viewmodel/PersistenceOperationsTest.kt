package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.chargers.ChargerCacheDao
import com.bgr3108.kilonom.chargers.ChargerCacheDatabase
import com.bgr3108.kilonom.chargers.ChargerCacheMetadataEntity
import com.bgr3108.kilonom.chargers.ChargerRepository
import com.bgr3108.kilonom.chargers.MitecoChargersParser
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.stations.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.runner.RunWith
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/** All failures use isolated in-memory databases and fakes, never the user's data or network. */
@RunWith(AndroidJUnit4::class)
class PersistenceOperationsTest {
    @Test
    fun fuelDeleteFailurePreservesEntryAndOnlySuccessfulRetryNotifiesUi() = runBlocking {
        withFuel { vm, dao, database ->
            dao.error = SQLiteException("private database error")
            var successes = 0
            onMain { vm.deleteEntry(ENTRY) { successes++ } }
            await { vm.deleteError.first { it != null } }
            await { vm.isDeleting.first { !it } }
            assertEquals(0, successes)
            assertEquals(ENTRY, database.fuelEntryDao().getEntryForVehicle(1, 1))
            assertEquals("No se pudo eliminar el consumo. Inténtalo de nuevo.", vm.deleteError.value)
            dao.error = null
            val deleted = CompletableDeferred<Unit>()
            onMain { vm.deleteEntry(ENTRY) { successes++; deleted.complete(Unit) } }
            await { deleted.await() }
            assertEquals(1, successes)
            assertEquals(2, dao.calls)
            assertNull(database.fuelEntryDao().getEntryForVehicle(1, 1))
            assertNull(vm.deleteError.value)
        }
    }

    @Test
    fun fuelCancellationRestoresStateDropsDuplicateAndAllowsRetry() = runBlocking {
        withFuel { vm, dao, database ->
            dao.error = CancellationException()
            dao.gate = CompletableDeferred()
            val started = dao.started
            var successes = 0
            onMain { vm.deleteEntry(ENTRY) { successes++ } }
            await { started.await() }
            onMain { vm.deleteEntry(ENTRY) { successes++ } }
            assertEquals(1, dao.calls)
            dao.gate?.complete(Unit)
            await { vm.isDeleting.first { !it } }
            assertNull(vm.deleteError.value)
            assertEquals(0, successes)
            assertEquals(ENTRY, database.fuelEntryDao().getEntryForVehicle(1, 1))
            dao.error = null
            val deleted = CompletableDeferred<Unit>()
            onMain { vm.deleteEntry(ENTRY) { deleted.complete(Unit) } }
            await { deleted.await() }
            assertEquals(2, dao.calls)
        }
    }

    @Test
    fun catalogPersistenceFailureAndCancellationRestoreCheckingAndAllowRetry() = runBlocking {
        val store = ViewModelStore()
        val checker = TestCatalogChecker()
        val vm = onMain { VehicleCatalogUpdateViewModel(checker).also { store.put("catalog", it) } }
        try {
            await { vm.uiState.first { it.activeCatalogVersion != null } }
            checker.error = IOException("metadata read failed")
            onMain { vm.checkForUpdate() }
            assertEquals(VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG), vm.uiState.value.status)
            checker.error = CancellationException()
            onMain { vm.checkForUpdate() }
            assertEquals(VehicleCatalogUpdateStatus.Idle, vm.uiState.value.status)
            checker.error = null
            onMain { vm.checkForUpdate() }
            assertEquals(VehicleCatalogUpdateStatus.UpToDate, vm.uiState.value.status)
            assertEquals(3, checker.calls)
        } finally { onMain { store.clear() } }
    }

    @Test
    fun catalogInstalledResultStillReachesUiWhenTimestampWriteFails() = runBlocking {
        val store = ViewModelStore()
        val checker = TestCatalogChecker().apply { result = VehicleCatalogUpdateResult.Updated(2) }
        val timestamps = object : VehicleCatalogUpdateTimestampStore {
            override suspend fun lastCatalogCheck(): Long? = null
            override suspend fun saveLastCatalogCheck(epochMillis: Long) { throw IOException() }
        }
        val coordinator = VehicleCatalogUpdateCoordinator(checker, timestamps)
        val vm = onMain { VehicleCatalogUpdateViewModel(coordinator).also { store.put("catalog", it) } }
        try {
            await { vm.uiState.first { it.activeCatalogVersion != null } }
            checker.version = 2
            onMain { vm.checkForUpdate() }
            await { vm.uiState.first { it.status == VehicleCatalogUpdateStatus.Updated } }
            assertEquals(2, vm.uiState.value.activeCatalogVersion)
            assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        } finally { onMain { store.clear() } }
    }

    @Test
    fun stationPreferenceReadFailureDoesNotPublishPartialDefaultsAndCanRetry() = runBlocking {
        withStations { fixture ->
            fixture.preferences.readError = IOException("private preference error")
            val vm = fixture.viewModel()
            onMain { vm.onStationsOpened() }
            await { vm.persistenceError.first { it != null } }
            val before = await { vm.uiState.first { it.metadata != null } }
            assertFalse(before.showLocationIntro)
            assertFalse(before.locationPermissionRequested)
            fixture.preferences.readError = null
            onMain { vm.retryPersistenceOperation() }
            await { vm.uiState.first { it.showLocationIntro } }
            assertNull(vm.persistenceError.value)
            assertNotNull(fixture.stations.stationCacheDao().getMetadata())
        }
    }

    @Test
    fun stationPreferenceWritesFailWithoutChangingStoredFlagsAndCanRetry() = runBlocking {
        withStations { fixture ->
            val vm = fixture.viewModel()
            fixture.preferences.writeError = IOException()
            onMain { vm.dismissLocationIntro() }
            await { vm.persistenceError.first { it != null } }
            assertFalse(fixture.preferences.seenIntro)
            fixture.preferences.writeError = null
            onMain { vm.retryPersistenceOperation() }
            assertTrue(fixture.preferences.seenIntro)
            fixture.preferences.writeError = IOException()
            onMain { vm.markLocationPermissionRequested() }
            await { vm.persistenceError.first { it != null } }
            assertFalse(fixture.preferences.requestedPermission)
            fixture.preferences.writeError = null
            onMain { vm.retryPersistenceOperation() }
            assertTrue(fixture.preferences.requestedPermission)
            assertNull(vm.persistenceError.value)
        }
    }

    @Test
    fun bothCacheClearFailuresRollbackRowsAndDatesThenRetrySuccessfully() = runBlocking {
        withStations { fixture ->
            val fuelMetadata = fixture.stations.stationCacheDao().getMetadata()
            val chargerMetadata = fixture.chargers.chargerCacheDao().getMetadata()
            val vm = fixture.viewModel()
            fixture.fuelDao.clearError = SQLiteException("failed after deletion")
            vm.clearCache().join()
            assertNotNull(vm.persistenceError.value)
            assertEquals(fuelMetadata, fixture.stations.stationCacheDao().getMetadata())
            assertEquals(listOf("fuel"), fixture.stations.stationCacheDao().observeStationsForProducts(StationFuelType.GASOLINE_95.productCodesByPriority, null, null).first().map { it.externalId })
            fixture.fuelDao.clearError = null
            onMain { vm.retryPersistenceOperation() }
            await { vm.metadata.first { it == null } }
            // Wait on Room itself: StateFlow's initial null does not prove the deletion finished.
            await { fixture.stations.stationCacheDao().observeMetadata().first { it == null } }
            fixture.chargerDao.clearError = SQLiteException("failed after deletion")
            vm.clearChargerCache().join()
            assertNotNull(vm.persistenceError.value)
            assertEquals(chargerMetadata, fixture.chargers.chargerCacheDao().getMetadata())
            assertEquals(listOf("site"), fixture.chargers.chargerCacheDao().observeInstallations().first().map { it.externalId })
            assertEquals(1, fixture.chargers.chargerCacheDao().getConnectorCount())
            fixture.chargerDao.clearError = null
            onMain { vm.retryPersistenceOperation() }
            await { fixture.chargers.chargerCacheDao().observeMetadata().first { it == null } }
        }
    }

    @Test
    fun cacheCancellationRollsBackAndPropagatesWithoutErrorFeedback() = runBlocking {
        withStations { fixture ->
            val vm = fixture.viewModel()
            val before = fixture.stations.stationCacheDao().getMetadata()
            fixture.fuelDao.clearError = CancellationException()
            val job = vm.clearCache()
            job.join()
            assertTrue(job.isCancelled)
            assertNull(vm.persistenceError.value)
            assertEquals(before, fixture.stations.stationCacheDao().getMetadata())
            fixture.fuelDao.clearError = null
            vm.clearCache().join()
            assertNull(fixture.stations.stationCacheDao().getMetadata())
        }
    }

    @Test
    fun automaticMetadataReadFailureIsVisibleAndDoesNotStartRemoteDownload() = runBlocking {
        withStations { fixture ->
            fixture.fuelDao.metadataError = SQLiteException("read failed")
            val vm = fixture.viewModel()
            onMain { vm.onStationsOpened() }
            await { vm.persistenceError.first { it != null } }
            assertNotNull(fixture.stations.stationCacheDao().getMetadata())
            assertEquals(0, fixture.remoteCalls)
            assertFalse(await { vm.uiState.first { it.metadata != null } }.isRefreshing)
        }
    }

    private suspend fun withFuel(test: suspend (FuelEntryViewModel, DeleteDao, HybridCarDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        try {
            database.vehicleDao().insert(Vehicle(type = VehicleType.GASOLINA).toEntity(id = 1, createdAt = 1))
            database.fuelEntryDao().insertEntry(ENTRY)
            val dao = DeleteDao(database.fuelEntryDao())
            val repository = VehicleRepository(EmptyCatalog, MemoryPreferences(), database.vehicleDao(), dao, database.maintenanceDao(), scope)
            await { repository.loadState.first { it is VehicleLoadState.Ready } }
            val vm = onMain { FuelEntryViewModel(FuelRepository(dao), repository).also { store.put("fuel", it) } }
            test(vm, dao, database)
        } finally {
            onMain { store.clear() }
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
            database.close()
        }
    }

    private suspend fun withStations(test: suspend (StationFixture) -> Unit) {
        val fixture = StationFixture()
        try { fixture.seed(); test(fixture) }
        finally { onMain { fixture.store.clear() }; fixture.stations.close(); fixture.chargers.close() }
    }

    private class StationFixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stations = Room.inMemoryDatabaseBuilder(context, StationCacheDatabase::class.java).build()
        val chargers = Room.inMemoryDatabaseBuilder(context, ChargerCacheDatabase::class.java).build()
        val fuelDao = FailingStationDao(stations.stationCacheDao())
        val chargerDao = FailingChargerDao(chargers.chargerCacheDao())
        val preferences = TestStationPreferences()
        val store = ViewModelStore()
        var remoteCalls = 0
        suspend fun seed() {
            val fuel = MitecoStationsParser.parse("""{"ListaEESSPrecio":[{"IDEESS":"fuel","Rótulo":"Estación","Provincia":"MADRID","Municipio":"Madrid","Precio Gasolina 95 E5":"1,50"}]}""")
            val charger = MitecoChargersParser.parse(StringReader("COD.INSTALACION;ID. CONECTOR;PROVINCIA;MUNICIPIO;TIPO CONECTOR;POTENCIA MAXIMA\nsite;1;Madrid;Madrid;IEC_62196_T2_COMBO;150"))
            stations.stationCacheDao().replaceCache(fuel.stations, fuel.prices, StationCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
            chargers.chargerCacheDao().replaceCache(charger.installations, charger.connectors, ChargerCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
        }
        fun viewModel() = onMain {
            StationsViewModel(
                StationRepository(stations, fuelDao) { remoteCalls++; throw IOException("network disabled in test") },
                ChargerRepository(chargers, chargerDao) { remoteCalls++; throw IOException("network disabled in test") },
                preferences
            ).also { store.put("stations", it) }
        }
    }

    private class FailingStationDao(private val delegate: StationCacheDao) : StationCacheDao by delegate {
        var clearError: Exception? = null
        var metadataError: Exception? = null
        override suspend fun clearCache() { delegate.clearCache(); clearError?.let { throw it } }
        override suspend fun getMetadata(): StationCacheMetadataEntity? { metadataError?.let { throw it }; return delegate.getMetadata() }
    }
    private class FailingChargerDao(private val delegate: ChargerCacheDao) : ChargerCacheDao by delegate {
        var clearError: Exception? = null
        override suspend fun clearCache() { delegate.clearCache(); clearError?.let { throw it } }
    }
    private class DeleteDao(private val delegate: FuelEntryDao) : FuelEntryDao by delegate {
        var error: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        var calls = 0
        override suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int {
            calls++
            started.complete(Unit)
            gate?.await()
            error?.let { throw it }
            return delegate.deleteEntryForVehicle(entryId, vehicleId)
        }
    }
    private class TestCatalogChecker : VehicleCatalogUpdateChecker {
        var error: Exception? = null
        var result: VehicleCatalogUpdateResult = VehicleCatalogUpdateResult.UpToDate(1)
        var version = 1
        var calls = 0
        override fun activeCatalogVersion(): Int = version
        override suspend fun checkForUpdate(): VehicleCatalogUpdateResult { calls++; error?.let { throw it }; return result }
    }
    private class TestStationPreferences : StationPreferencesStore {
        var readError: Exception? = null
        var writeError: Exception? = null
        var seenIntro = false
        var requestedPermission = false
        override suspend fun hasSeenLocationIntro(): Boolean = seenIntro
        override suspend fun hasRequestedLocationPermission(): Boolean { readError?.let { throw it }; return requestedPermission }
        override suspend fun markLocationIntroSeen() { writeError?.let { throw it }; seenIntro = true }
        override suspend fun markLocationPermissionRequested() { writeError?.let { throw it }; requestedPermission = true }
    }
    private class MemoryPreferences : VehiclePreferencesStore {
        override suspend fun loadVehicle() = Vehicle()
        override suspend fun loadActiveVehicleId() = 1L
        override suspend fun lastSeenReleaseNotesVersion() = RELEASE_NOTES_VERSION
        override suspend fun hasStoredAppState() = true
        override suspend fun saveVehicle(vehicle: Vehicle) = Unit
        override suspend fun saveActiveVehicleId(vehicleId: Long) = Unit
        override suspend fun clearVehicle() = Unit
    }
    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }
    private companion object {
        val ENTRY = FuelEntry(id = 1, fecha = 1, cantidad = 10.0, precio = 15.0, tipo = FuelType.GASOLINA, km = 1_200.0, vehicleId = 1)
    }
}

private suspend fun <T> await(block: suspend () -> T): T = withTimeout(5.seconds) { block() }

private fun <T> onMain(action: () -> T): T {
    val value = AtomicReference<T>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync { value.set(action()) }
    return value.get()
}
