package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.chargers.ChargerCacheDao
import com.bgr3108.kilonom.chargers.ChargerCacheDatabase
import com.bgr3108.kilonom.chargers.ChargerCacheMetadataEntity
import com.bgr3108.kilonom.chargers.ChargerConnectorEntity
import com.bgr3108.kilonom.chargers.ChargerConnectorType
import com.bgr3108.kilonom.chargers.ChargerFilter
import com.bgr3108.kilonom.chargers.ChargerInstallationEntity
import com.bgr3108.kilonom.chargers.ChargerListRow
import com.bgr3108.kilonom.chargers.ChargerRepository
import com.bgr3108.kilonom.chargers.ChargerSortOrder
import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.stations.FuelStationEntity
import com.bgr3108.kilonom.stations.FuelStationPriceEntity
import com.bgr3108.kilonom.stations.StationCacheDao
import com.bgr3108.kilonom.stations.StationCacheDatabase
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.StationSortOrder
import com.bgr3108.kilonom.stations.StationsViewMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class StationsListMapConsistencyTest {
    private val origin = StationCoordinates(40.0, -3.0)
    private val wholeArea = StationMapBounds(39.99, -3.1, 40.5, -2.9)
    private val distantArea = StationMapBounds(40.17, -3.05, 40.23, -2.95)
    private val closeArea = StationMapBounds(40.0, -3.05, 40.1, -2.95)

    @Test
    fun nearbyTenKmRemainsTenKmWhenViewportContainsOnlyTwentyTwoKmPoint() = runBlocking {
        withFixture(List(12) { 5.0 } + 22.0) { f ->
            val vm = f.viewModel()
            f.collectResults(this, vm)
            onMain { vm.updateCurrentLocation(origin) }
            await { vm.stations.first { it.size == 12 } }
            onMain { vm.setViewMode(StationsViewMode.MAP); vm.updateStationMapViewport(wholeArea) }
            await { vm.mapStations.first { it.size == 12 } }
            onMain { vm.updateStationMapViewport(distantArea) }
            await { vm.mapStations.first { it.isEmpty() } }
            assertEquals(1, f.fuelDao.nearby.starts.value)

            onMain { vm.selectContentType(StationsContentType.CHARGERS); vm.setViewMode(StationsViewMode.LIST) }
            await { vm.chargers.first { it.size == 12 } }
            onMain { vm.setViewMode(StationsViewMode.MAP); vm.updateChargerMapViewport(wholeArea) }
            await { vm.mapChargers.first { it.size == 12 } }
            onMain { vm.updateChargerMapViewport(distantArea) }
            await { vm.mapChargers.first { it.isEmpty() } }
            assertEquals(1, f.chargerDao.nearby.starts.value)
            assertEquals(0, f.remoteCalls)
        }
    }

    @Test
    fun pointsAtTheSelectedRadiusEdgeAreNotLostByASmallerApproximateBoundingBox() = runBlocking {
        withFixture(List(12) { 5.0 } + 9.995) { f ->
            val longitude = origin.longitude + 9.995 / (111.195 * Math.cos(Math.toRadians(origin.latitude)))
            f.stations.stationCacheDao().insertStations(listOf(FuelStationEntity(
                "point12", "point12", "", "Madrid", "MADRID", null, origin.latitude, longitude,
                null, null, null, null, null
            )))
            // REPLACE cascades to child rows, so restore the fixture's price and connector too.
            f.stations.stationCacheDao().insertPrices(listOf(
                FuelStationPriceEntity("point12", "gasolina_95_e5", "Gasolina 95", 1.5, "GASOLINE_95", 0)
            ))
            val installation = f.chargers.chargerCacheDao().observeInstallations().first().single { it.externalId == "point12" }
            f.chargers.chargerCacheDao().insertInstallations(listOf(installation.copy(latitude = origin.latitude, longitude = longitude)))
            f.chargers.chargerCacheDao().insertConnectors(listOf(ChargerConnectorEntity(
                "point12|1", "point12", null, null, installation.connectorTypesEncoded, null, null, installation.maxPowerKw, null, null
            )))
            val viewport = StationMapBounds(39.9, -3.5, 40.1, -2.5)
            val fuel = f.fuelRepository.observeNearbyStationSelection(StationFilter(), origin).first()
            val charger = f.chargerRepository.observeNearbyChargerSelection(ChargerFilter(), origin).first()
            assertEquals(10.0, fuel.radiusKm, 0.0)
            assertEquals(10.0, charger.radiusKm, 0.0)
            assertEquals(13, fuel.items.size)
            assertEquals(fuel.items.map { it.externalId },
                f.fuelRepository.observeNearbyStationsForMap(StationFilter(), origin, viewport, fuel.radiusKm).first().map { it.externalId })
            assertEquals(charger.items.map { it.externalId },
                f.chargerRepository.observeNearbyChargersForMap(ChargerFilter(), origin, viewport, charger.radiusKm).first().map { it.externalId })
        }
    }

    @Test
    fun twentyFiveFiftyAndSparseSelectionsUseIdenticalListAndMapBoundaries() = runBlocking {
        val cases = listOf(
            Triple(List(11) { 5.0 } + listOf(22.0, 40.0, 55.0), 25.0, 12),
            Triple(List(11) { 22.0 } + listOf(40.0, 55.0), 50.0, 12),
            Triple(listOf(5.0, 22.0, 40.0, 55.0), 50.0, 3)
        )
        for ((distances, radius, count) in cases) withFixture(distances) { f ->
            val fuel = f.fuelRepository.observeNearbyStationSelection(StationFilter(), origin).first()
            val charger = f.chargerRepository.observeNearbyChargerSelection(ChargerFilter(), origin).first()
            assertEquals(radius, fuel.radiusKm, 0.0)
            assertEquals(radius, charger.radiusKm, 0.0)
            assertEquals(count, fuel.items.size)
            assertEquals(fuel.items, f.fuelRepository.observeNearbyStations(StationFilter(), origin).first())
            assertEquals(charger.items, f.chargerRepository.observeNearbyChargers(ChargerFilter(), origin).first())
            assertEquals(fuel.items.map { it.externalId },
                f.fuelRepository.observeNearbyStationsForMap(StationFilter(), origin, wholeArea, fuel.radiusKm).first().map { it.externalId })
            assertEquals(charger.items.map { it.externalId },
                f.chargerRepository.observeNearbyChargersForMap(ChargerFilter(), origin, wholeArea, charger.radiusKm).first().map { it.externalId })
        }
    }

    @Test
    fun fuelConnectorPowerAndOperatorFiltersRecalculateTheLogicalRadiusWithoutChangingViewport() = runBlocking {
        withFixture(List(12) { 5.0 } + List(12) { 22.0 }) { f ->
            val vm = f.viewModel()
            f.collectResults(this, vm)
            onMain { vm.updateCurrentLocation(origin); vm.setViewMode(StationsViewMode.MAP); vm.updateStationMapViewport(wholeArea) }
            await { vm.mapStations.first { it.size == 12 && it.first().externalId == "point00" } }
            onMain { vm.applyFilter(StationFilter(fuelType = StationFuelType.DIESEL)) }
            val diesel = await { vm.mapStations.first { it.size == 12 && it.first().externalId == "point12" } }
            assertTrue(diesel.all { it.productCode == "gasoleo_a" })

            onMain { vm.selectContentType(StationsContentType.CHARGERS); vm.updateChargerMapViewport(wholeArea) }
            await { vm.mapChargers.first { it.size == 12 && it.first().externalId == "point00" } }
            val filter = ChargerFilter(connectorType = ChargerConnectorType.CCS_COMBO_2, minimumPowerKw = 100.0, operatorName = "Fast")
            onMain { vm.applyChargerFilter(filter) }
            val filtered = await { vm.mapChargers.first { it.size == 12 && it.first().externalId == "point12" } }
            assertTrue(filtered.all { it.maxPowerKw == 150.0 && it.operatorName == "Fast" && "IEC_62196_T2_COMBO" in it.connectorTypes })
            val candidateQueries = f.chargerDao.nearby.starts.value
            onMain { vm.updateChargerMapViewport(closeArea) }
            await { vm.mapChargers.first { it.isEmpty() } }
            onMain { vm.updateChargerMapViewport(distantArea) }
            await { vm.mapChargers.first { it.size == 12 } }
            assertEquals(candidateQueries, f.chargerDao.nearby.starts.value)
            onMain { vm.setViewMode(StationsViewMode.LIST) }
            val list = await { vm.chargers.first { it.size == 12 && it.first().externalId == "point12" } }
            assertEquals(filtered.map { it.externalId }, list.map { it.externalId })
        }
    }

    @Test
    fun manualDistanceQueriesActuallyUnsubscribeInMapAndResumeWithSameFiltersInList() = runBlocking {
        withFixture(listOf(22.0, 5.0, 40.0)) { f ->
            val vm = f.viewModel()
            f.collectResults(this, vm)
            val fuelFilter = StationFilter(province = "MADRID", municipality = "Madrid", sortOrder = StationSortOrder.DISTANCE)
            val chargerFilter = ChargerFilter(province = "MADRID", municipality = "Madrid", sortOrder = ChargerSortOrder.DISTANCE)
            onMain {
                vm.applyFilter(fuelFilter.copy(sortOrder = StationSortOrder.PRICE))
                vm.applyChargerFilter(chargerFilter.copy(sortOrder = ChargerSortOrder.NAME))
                vm.updateCurrentLocation(origin)
                vm.applyFilter(fuelFilter); vm.applyChargerFilter(chargerFilter)
            }
            val initialFuel = await { vm.stations.first { it.size == 3 } }
            assertEquals(listOf("point01", "point00", "point02"), initialFuel.map { it.externalId })
            assertEquals(1, f.fuelDao.full.active.value)
            onMain { vm.setViewMode(StationsViewMode.MAP); vm.updateStationMapViewport(closeArea) }
            await { f.fuelDao.full.active.first { it == 0 } }
            assertEquals(listOf("point01"), await { vm.mapStations.first { it.size == 1 } }.map { it.externalId })
            val fuelQueries = f.fuelDao.full.starts.value
            onMain { vm.updateStationMapViewport(distantArea) }
            await { vm.mapStations.first { it.singleOrNull()?.externalId == "point00" } }
            assertEquals(fuelQueries, f.fuelDao.full.starts.value)
            onMain { vm.setViewMode(StationsViewMode.LIST) }
            assertEquals(initialFuel, await { vm.stations.first { it.size == 3 } })
            assertEquals(fuelFilter, await { vm.uiState.first { it.filter == fuelFilter } }.filter)

            onMain { vm.selectContentType(StationsContentType.CHARGERS) }
            await { f.fuelDao.full.active.first { it == 0 } }
            val initialChargers = await { vm.chargers.first { it.size == 3 } }
            assertEquals(listOf("point01", "point00", "point02"), initialChargers.map { it.externalId })
            onMain { vm.setViewMode(StationsViewMode.MAP); vm.updateChargerMapViewport(closeArea) }
            await { f.chargerDao.full.active.first { it == 0 } }
            await { vm.mapChargers.first { it.singleOrNull()?.externalId == "point01" } }
            val chargerQueries = f.chargerDao.full.starts.value
            onMain { vm.updateChargerMapViewport(distantArea) }
            await { vm.mapChargers.first { it.singleOrNull()?.externalId == "point00" } }
            assertEquals(chargerQueries, f.chargerDao.full.starts.value)
            onMain { vm.setViewMode(StationsViewMode.LIST) }
            assertEquals(initialChargers, await { vm.chargers.first { it.size == 3 } })
            assertEquals(chargerFilter, await { vm.chargerUiState.first { it.filter == chargerFilter } }.filter)
        }
    }

    @Test
    fun noScopeDoesNotQueryPointsEvenWithValidViewportAndActiveCollectors() = runBlocking {
        withFixture(listOf(5.0)) { f ->
            val vm = f.viewModel()
            f.collectResults(this, vm)
            onMain { vm.setViewMode(StationsViewMode.MAP); vm.updateStationMapViewport(wholeArea); vm.updateChargerMapViewport(wholeArea) }
            await { vm.uiState.first { it.viewMode == StationsViewMode.MAP } }
            onMain { vm.selectContentType(StationsContentType.CHARGERS) }
            await { vm.uiState.first { it.contentType == StationsContentType.CHARGERS } }
            assertEquals(0, f.fuelDao.full.starts.value + f.fuelDao.nearby.starts.value + f.fuelDao.viewport.starts.value)
            assertEquals(0, f.chargerDao.full.starts.value + f.chargerDao.nearby.starts.value + f.chargerDao.viewport.starts.value)
            assertTrue(vm.stations.value.isEmpty() && vm.chargers.value.isEmpty())
        }
    }

    @Test
    fun delayedOldViewportIsCancelledAndCannotOverwriteNewViewport() = runBlocking {
        withFixture(listOf(5.0, 22.0)) { f ->
            val vm = f.viewModel()
            f.collectResults(this, vm)
            onMain { vm.applyFilter(StationFilter(province = "MADRID")); vm.setViewMode(StationsViewMode.MAP) }
            val gate = CompletableDeferred<Unit>()
            f.fuelDao.viewport.gate = gate
            onMain { vm.updateStationMapViewport(closeArea) }
            await { f.fuelDao.viewport.active.first { it == 1 } }
            f.fuelDao.viewport.gate = null
            onMain { vm.updateStationMapViewport(distantArea) }
            await { vm.mapStations.first { it.singleOrNull()?.externalId == "point01" } }
            gate.complete(Unit)
            assertEquals(1, f.fuelDao.viewport.active.value)
            assertEquals(listOf("point01"), vm.mapStations.value.map { it.externalId })
        }
    }

    private suspend fun withFixture(distances: List<Double>, test: suspend CoroutineScope.(Fixture) -> Unit) = coroutineScope {
        val f = Fixture()
        try { f.seed(distances); test(f) }
        finally {
            f.collectors.forEach { it.cancelAndJoin() }
            f.vm?.let { vm -> onMain { f.store.clear() }; vm.viewModelScope.coroutineContext[Job]?.join() }
            f.stations.close(); f.chargers.close()
        }
    }

    private inner class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stations = Room.inMemoryDatabaseBuilder(context, StationCacheDatabase::class.java).build()
        val chargers = Room.inMemoryDatabaseBuilder(context, ChargerCacheDatabase::class.java).build()
        val fuelDao = TrackingStationDao(stations.stationCacheDao())
        val chargerDao = TrackingChargerDao(chargers.chargerCacheDao())
        var remoteCalls = 0
        val fuelRepository = StationRepository(stations, fuelDao) { remoteCalls++; error("Unexpected network call") }
        val chargerRepository = ChargerRepository(chargers, chargerDao) { remoteCalls++; error("Unexpected network call") }
        val store = ViewModelStore()
        val collectors = mutableListOf<Job>()
        var vm: StationsViewModel? = null
        fun viewModel() = onMain { StationsViewModel(fuelRepository, chargerRepository).also { vm = it; store.put("stations", it) } }
        fun collectResults(scope: CoroutineScope, viewModel: StationsViewModel) {
            collectors += scope.launch { viewModel.stations.collect() }
            collectors += scope.launch { viewModel.mapStations.collect() }
            collectors += scope.launch { viewModel.chargers.collect() }
            collectors += scope.launch { viewModel.mapChargers.collect() }
        }
        suspend fun seed(distances: List<Double>) {
            val fuelRows = distances.mapIndexed { i, km -> FuelStationEntity(
                id(i), id(i), "", "Madrid", "MADRID", null, origin.latitude + km / 111.195, origin.longitude,
                null, null, null, null, null
            ) }
            val prices = fuelRows.map { row -> FuelStationPriceEntity(row.externalId, "gasolina_95_e5", "Gasolina 95", 1.5, "GASOLINE_95", 0) } +
                fuelRows.filterIndexed { i, _ -> i >= 12 }.map { row -> FuelStationPriceEntity(row.externalId, "gasoleo_a", "Diésel", 1.4, "DIESEL", 0) }
            stations.stationCacheDao().replaceCache(fuelRows, prices,
                StationCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
            val installations = fuelRows.mapIndexed { i, row -> ChargerInstallationEntity(
                row.externalId, row.name, if (i >= 12) "Fast" else "Slow", null, "", "Madrid", "MADRID", null, null, null, null,
                row.latitude, row.longitude, null, if (i >= 12) 150.0 else 22.0, if (i >= 12) "IEC_62196_T2_COMBO" else "IEC_62196_T2"
            ) }
            val connectors = installations.map { row -> ChargerConnectorEntity(
                "${row.externalId}|1", row.externalId, null, null, row.connectorTypesEncoded, null, null, row.maxPowerKw, null, null
            ) }
            chargers.chargerCacheDao().replaceCache(installations, connectors,
                ChargerCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
        }
    }

    private class QueryCounter {
        val active = MutableStateFlow(0)
        val starts = MutableStateFlow(0)
        @Volatile var gate: CompletableDeferred<Unit>? = null
        fun <T> track(source: Flow<T>): Flow<T> = flow {
            val waitFor = gate
            active.update { it + 1 }; starts.update { it + 1 }
            try { waitFor?.await(); emitAll(source) }
            finally { active.update { it - 1 } }
        }
    }

    private class TrackingStationDao(private val delegate: StationCacheDao) : StationCacheDao by delegate {
        val full = QueryCounter()
        val nearby = QueryCounter()
        val viewport = QueryCounter()
        override fun observeStationsForMap(fuelType: String, province: String?, municipality: String?): Flow<List<StationListItem>> =
            full.track(delegate.observeStationsForMap(fuelType, province, municipality))
        override fun observeStationsInBounds(fuelType: String, minLatitude: Double, maxLatitude: Double, minLongitude: Double, maxLongitude: Double): Flow<List<StationListItem>> =
            nearby.track(delegate.observeStationsInBounds(fuelType, minLatitude, maxLatitude, minLongitude, maxLongitude))
        override fun observeStationsForMapViewport(fuelType: String, province: String?, municipality: String?, south: Double, west: Double, north: Double, east: Double, crossesAntimeridian: Int): Flow<List<StationListItem>> =
            viewport.track(delegate.observeStationsForMapViewport(fuelType, province, municipality, south, west, north, east, crossesAntimeridian))
    }

    private class TrackingChargerDao(private val delegate: ChargerCacheDao) : ChargerCacheDao by delegate {
        val full = QueryCounter()
        val nearby = QueryCounter()
        val viewport = QueryCounter()
        override fun observeChargersForMap(connectorFilterEnabled: Int, connectorCodes: List<String>, minimumPowerKw: Double?, operatorName: String?, province: String?, municipality: String?): Flow<List<ChargerListRow>> =
            full.track(delegate.observeChargersForMap(connectorFilterEnabled, connectorCodes, minimumPowerKw, operatorName, province, municipality))
        override fun observeChargersInBoundsWithoutConnector(minimumPowerKw: Double?, operatorName: String?, minLatitude: Double, maxLatitude: Double, minLongitude: Double, maxLongitude: Double): Flow<List<ChargerListRow>> =
            nearby.track(delegate.observeChargersInBoundsWithoutConnector(minimumPowerKw, operatorName, minLatitude, maxLatitude, minLongitude, maxLongitude))
        override fun observeChargersInBoundsWithConnector(connectorCodes: List<String>, minimumPowerKw: Double?, operatorName: String?, minLatitude: Double, maxLatitude: Double, minLongitude: Double, maxLongitude: Double): Flow<List<ChargerListRow>> =
            nearby.track(delegate.observeChargersInBoundsWithConnector(connectorCodes, minimumPowerKw, operatorName, minLatitude, maxLatitude, minLongitude, maxLongitude))
        override fun observeChargersForMapViewport(connectorFilterEnabled: Int, connectorCodes: List<String>, minimumPowerKw: Double?, operatorName: String?, province: String?, municipality: String?, south: Double, west: Double, north: Double, east: Double, crossesAntimeridian: Int): Flow<List<ChargerListRow>> =
            viewport.track(delegate.observeChargersForMapViewport(connectorFilterEnabled, connectorCodes, minimumPowerKw, operatorName, province, municipality, south, west, north, east, crossesAntimeridian))
    }

    private suspend fun <T> await(block: suspend () -> T): T = withTimeout(10.seconds) { block() }
    private fun id(index: Int) = "point" + index.toString().padStart(2, '0')
    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
        return result.get()
    }
}
