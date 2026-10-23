package com.bgr3108.kilonom.viewmodel

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.chargers.*
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.domain.StatisticsPeriod
import com.bgr3108.kilonom.domain.StatisticsPeriodMode
import com.bgr3108.kilonom.stations.*
import com.bgr3108.kilonom.testing.OperationTestActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/** All Room databases are in memory. No AppContainer, real preferences or remote sources. */
@RunWith(AndroidJUnit4::class)
class SelectionsRestorationTest {
    @Test fun newViewModelRestoresBothSourcesFiltersModeAndSortWithoutRefresh() = fixture { f ->
        val handle = SavedStateHandle()
        val original = f.vm(handle)
        onMain {
            original.applyFilter(fuelFilter)
            original.applyChargerFilter(chargerFilter)
            original.selectContentType(StationsContentType.CHARGERS)
            original.setViewMode(StationsViewMode.MAP)
        }
        val restored = f.vm(copy(handle))
        assertEquals(fuelFilter, restored.uiState.value.filter)
        assertEquals(chargerFilter, restored.chargerUiState.value.filter)
        assertEquals(StationsContentType.CHARGERS, restored.uiState.value.contentType)
        assertEquals(StationsViewMode.MAP, restored.uiState.value.viewMode)
        assertEquals(StationSearchScope.ManualZone("Madrid", "Madrid"), restored.chargerUiState.value.searchScope)
        // The other source is stale: restoring Chargers must not start a fuel download.
        f.stations.stationCacheDao().deleteMetadata()
        onMain { restored.onStationsOpened() }
        await { restored.chargerUiState.first { it.metadata != null } }
        assertEquals(0, f.remoteCalls.get())
        assertFalse(restored.uiState.value.isRefreshing)
        assertFalse(restored.chargerUiState.value.refreshing)
    }

    @Test fun nearbyIntentRestoresWithoutCoordinatesViewportOrNationalQueries() = fixture { f ->
        val handle = SavedStateHandle()
        val original = f.vm(handle)
        onMain {
            original.updateCurrentLocation(StationCoordinates(40.4, -3.7))
            original.useNearbyStations()
            original.useNearbyChargers()
            original.updateStationMapViewport(StationMapBounds(40.0, -4.0, 41.0, -3.0))
            original.setViewMode(StationsViewMode.MAP)
        }
        assertEquals(setOf("stations.filter", "stations.chargerFilter", "stations.mode", "stations.source"), handle.keys())
        val restored = f.vm(copy(handle))
        onMain { restored.onStationsOpened() }
        assertNull(restored.uiState.value.currentLocation)
        assertEquals(StationSortOrder.DISTANCE, restored.uiState.value.filter.sortOrder)
        assertEquals(StationSearchScope.None, restored.uiState.value.searchScope)
        assertEquals(StationSearchScope.None, restored.chargerUiState.value.searchScope)
        assertTrue(restored.mapStations.first().isEmpty())
        assertTrue(restored.mapChargers.first().isEmpty())
        assertEquals(0, f.remoteCalls.get())
    }

    @Test fun incompatibleMunicipalityFallsBackToItsProvince() = fixture { f ->
        val restored = f.vm(SavedStateHandle(mapOf(
            "stations.filter" to fuelFilter.copy(municipality = "Barcelona").savedValues(),
            "stations.chargerFilter" to chargerFilter.copy(municipality = "Barcelona").savedValues()
        )))
        onMain { restored.onStationsOpened() }
        await { restored.uiState.first { it.filter.municipality == null } }
        await { restored.chargerUiState.first { it.filter.municipality == null } }
        assertEquals("MADRID", restored.uiState.value.filter.province)
        assertEquals("Madrid", restored.chargerUiState.value.filter.province)
        assertEquals(0, f.remoteCalls.get())
    }

    @Test fun missingProvinceFallsBackToNoScopeNotNationalSearch() = fixture { f ->
        val restored = f.vm(SavedStateHandle(mapOf("stations.filter" to fuelFilter.copy(province = "removed").savedValues())))
        onMain { restored.onStationsOpened() }
        await { restored.uiState.first { it.filter.province == null && it.searchScope == StationSearchScope.None } }
        assertTrue(restored.stations.first().isEmpty())
        assertEquals(0, f.remoteCalls.get())
    }

    @Test fun activityRecreationRetainsSelectionsWithExistingViewModel() = fixture { f ->
        val reference = AtomicReference<StationsViewModel>()
        val factory = viewModelFactory { initializer { f.newVm(createSavedStateHandle()) } }
        OperationTestActivity.content = { activity -> reference.set(ViewModelProvider(activity, factory)[StationsViewModel::class.java]) }
        try {
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                scenario.onActivity {
                    reference.get().applyFilter(fuelFilter)
                    reference.get().setViewMode(StationsViewMode.MAP)
                }
                val previous = reference.get()
                scenario.recreate()
                scenario.onActivity {
                    assertSame(previous, reference.get())
                }
                await { reference.get().uiState.first { it.filter == fuelFilter && it.viewMode == StationsViewMode.MAP } }
            }
        } finally { OperationTestActivity.content = null }
    }

    @Test fun newActivityAndNewViewModelRestoreAndroidSavedRegistry() = fixture { f ->
        val reference = AtomicReference<StationsViewModel>()
        val periodReference = AtomicReference<PeriodSummaryViewModel>()
        val saved = AtomicReference<Bundle>()
        val factory = viewModelFactory {
            initializer { f.newVm(createSavedStateHandle()) }
            initializer { f.newPeriodVm(createSavedStateHandle()) }
        }
        OperationTestActivity.content = { activity ->
            reference.set(ViewModelProvider(activity, factory)[StationsViewModel::class.java])
            periodReference.set(ViewModelProvider(activity, factory)[PeriodSummaryViewModel::class.java])
        }
        OperationTestActivity.onStateSaved = { saved.set(it) }
        try {
            lateinit var previous: StationsViewModel
            lateinit var previousPeriod: PeriodSummaryViewModel
            lateinit var chosenPeriod: StatisticsPeriod
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                scenario.onActivity {
                    previous = reference.get()
                    previous.applyChargerFilter(chargerFilter)
                    previous.selectContentType(StationsContentType.CHARGERS)
                    previous.setViewMode(StationsViewMode.MAP)
                    previousPeriod = periodReference.get()
                    previousPeriod.selectMode(StatisticsPeriodMode.MONTH)
                    previousPeriod.showPreviousPeriod()
                    chosenPeriod = previousPeriod.period.value
                }
                scenario.moveToState(Lifecycle.State.CREATED)
            }
            OperationTestActivity.restoredState = requireNotNull(saved.get())
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val restored = reference.get()
                    assertNotSame(previous, restored)
                    assertEquals(chargerFilter, restored.chargerUiState.value.filter)
                    assertEquals(StationsContentType.CHARGERS, restored.uiState.value.contentType)
                    assertEquals(StationsViewMode.MAP, restored.uiState.value.viewMode)
                    assertNull(restored.uiState.value.currentLocation)
                    assertNotSame(previousPeriod, periodReference.get())
                    periodReference.get().refreshCurrentPeriodOnResume()
                    assertEquals(chosenPeriod, periodReference.get().period.value)
                }
            }
            assertEquals(0, f.remoteCalls.get())
        } finally {
            OperationTestActivity.content = null
            OperationTestActivity.restoredState = null
            OperationTestActivity.onStateSaved = null
        }
    }

    @Test fun statisticsHistoricalPeriodSurvivesNewViewModelResumeAndVehicleChange() = fixture { f ->
        val handle = SavedStateHandle()
        val original = f.periodVm(handle)
        onMain { original.selectMode(StatisticsPeriodMode.YEAR); original.showPreviousPeriod() }
        val chosen = original.period.value
        val restoredHandle = copy(handle)
        val restored = f.periodVm(restoredHandle)
        onMain { restored.refreshCurrentPeriodOnResume() }
        assertEquals(chosen, restored.period.value)
        f.personal.vehicleDao().insert(Vehicle(brand = "Second", model = "Test", type = VehicleType.ELECTRICO).toEntity(id = 2, createdAt = 2))
        f.vehicles.selectActiveVehicle(2)
        await { restored.uiState.first { it.vehicleType == VehicleType.ELECTRICO } }
        assertEquals(chosen, restored.period.value)
        onMain { restored.selectMode(StatisticsPeriodMode.ALL) }
        assertEquals(StatisticsPeriod.All, f.periodVm(copy(restoredHandle)).period.value)
    }

    private fun fixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try { f.seed(); block(f) } finally {
            onMain { f.store.clear() }
            f.scope.coroutineContext[Job]?.cancelAndJoin()
            f.stations.close(); f.chargers.close(); f.personal.close()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stations = Room.inMemoryDatabaseBuilder(context, StationCacheDatabase::class.java).build()
        val chargers = Room.inMemoryDatabaseBuilder(context, ChargerCacheDatabase::class.java).build()
        val personal = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = ViewModelStore()
        val remoteCalls = AtomicInteger()
        private val fuelRepository = StationRepository(stations, stations.stationCacheDao()) { remoteCalls.incrementAndGet(); error("Unexpected remote request") }
        private val chargerRepository = ChargerRepository(chargers, chargers.chargerCacheDao()) { remoteCalls.incrementAndGet(); error("Unexpected remote request") }
        lateinit var vehicles: VehicleRepository
        fun newVm(handle: SavedStateHandle) = StationsViewModel(fuelRepository, chargerRepository, savedStateHandle = handle)
        fun vm(handle: SavedStateHandle) = onMain { newVm(handle).also { store.put("stations${store.keys().size}", it) } }
        fun newPeriodVm(handle: SavedStateHandle) = PeriodSummaryViewModel(FuelRepository(personal.fuelEntryDao()), vehicles, handle)
        fun periodVm(handle: SavedStateHandle) = onMain {
            newPeriodVm(handle).also { store.put("period${store.keys().size}", it) }
        }
        suspend fun seed() {
            val fuel = MitecoStationsParser.parse("""{"ListaEESSPrecio":[{"IDEESS":"site","Rótulo":"Estación","Provincia":"MADRID","Municipio":"Madrid","Precio Gasolina 95 E5":"1,50"}]}""")
            stations.stationCacheDao().replaceCache(fuel.stations, fuel.prices, StationCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
            val charger = MitecoChargersParser.parse(StringReader("COD.INSTALACION;ID. CONECTOR;PROVINCIA;MUNICIPIO;TIPO CONECTOR;POTENCIA MAXIMA\nsite;1;Madrid;Madrid;IEC_62196_T2_COMBO;150"))
            chargers.chargerCacheDao().replaceCache(charger.installations, charger.connectors, ChargerCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
            personal.vehicleDao().insert(Vehicle(brand = "First", model = "Test", type = VehicleType.GASOLINA).toEntity(id = 1, createdAt = 1))
            vehicles = VehicleRepository(EmptyCatalog, MemoryPreferences(), personal.vehicleDao(), personal.fuelEntryDao(), personal.maintenanceDao(), scope)
            await { vehicles.loadState.first { it is VehicleLoadState.Ready } }
        }
    }

    private class MemoryPreferences : VehiclePreferencesStore {
        private var active = 1L
        override suspend fun loadVehicle() = Vehicle()
        override suspend fun saveVehicle(vehicle: Vehicle) = Unit
        override suspend fun clearVehicle() = Unit
        override suspend fun loadActiveVehicleId() = active
        override suspend fun saveActiveVehicleId(vehicleId: Long) { active = vehicleId }
        override suspend fun lastSeenReleaseNotesVersion() = RELEASE_NOTES_VERSION
    }

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory) = VehicleSelectionCatalog(category, emptyList())
    }

    companion object {
        private val fuelFilter = StationFilter(StationFuelType.GASOLINE_95, "MADRID", "Madrid", StationSortOrder.PRICE)
        private val chargerFilter = ChargerFilter(ChargerConnectorType.CCS_COMBO_2, 100.0, null, "Madrid", "Madrid", ChargerSortOrder.NAME)
        private fun copy(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        private suspend fun <T> await(action: suspend () -> T): T = withTimeout(10.seconds) { action() }
        private fun <T> onMain(action: () -> T): T {
            val result = AtomicReference<T>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
            return result.get()
        }
    }
}
