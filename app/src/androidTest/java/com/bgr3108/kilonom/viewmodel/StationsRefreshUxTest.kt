package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.chargers.ChargerCacheDatabase
import com.bgr3108.kilonom.chargers.ChargerCacheMetadataEntity
import com.bgr3108.kilonom.chargers.ChargerConnectorType
import com.bgr3108.kilonom.chargers.ChargerFilter
import com.bgr3108.kilonom.chargers.ChargerRepository
import com.bgr3108.kilonom.chargers.MitecoChargersParser
import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.stations.MitecoStationsParser
import com.bgr3108.kilonom.stations.StationCacheDatabase
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.stations.StationsViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.StringReader
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class StationsRefreshUxTest {
    private lateinit var stations: StationCacheDatabase
    private lateinit var chargers: ChargerCacheDatabase
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        stations = Room.inMemoryDatabaseBuilder(context, StationCacheDatabase::class.java).build()
        chargers = Room.inMemoryDatabaseBuilder(context, ChargerCacheDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        store.clear()
        stations.close()
        chargers.close()
    }

    @Test
    fun manualChargerRefreshWithFreshCachePreservesFiltersScopeAndMode() = runBlocking {
        val parsed = MitecoChargersParser.parse(StringReader("COD.INSTALACION;ID. CONECTOR;PROVINCIA;MUNICIPIO;TIPO CONECTOR;POTENCIA MAXIMA\nsite;1;Madrid;Madrid;IEC_62196_T2_COMBO;150"))
        chargers.chargerCacheDao().replaceCache(parsed.installations, parsed.connectors,
            ChargerCacheMetadataEntity(downloadedAtMillis = System.currentTimeMillis(), sourceUpdatedAtMillis = null, sourceUrl = "fixture"))
        var fuelCalls = 0
        var chargerCalls = 0
        val vm = StationsViewModel(
            StationRepository(stations, stations.stationCacheDao()) { fuelCalls++; throw IOException() },
            ChargerRepository(chargers, chargers.chargerCacheDao()) { chargerCalls++; parsed }
        )
        store.put("stations", vm)
        val filter = ChargerFilter(connectorType = ChargerConnectorType.CCS_COMBO_2, minimumPowerKw = 100.0,
            operatorName = "Operador", province = "Madrid", municipality = "Madrid")
        vm.applyChargerFilter(filter)
        vm.selectContentType(StationsContentType.CHARGERS)
        vm.setViewMode(StationsViewMode.MAP)
        vm.refreshChargers().join()
        val state = withTimeout(5.seconds) { vm.chargerUiState.first { it.refreshSucceeded && it.searchScope is StationSearchScope.ManualZone } }
        val navigation = withTimeout(5.seconds) { vm.uiState.first { it.contentType == StationsContentType.CHARGERS } }
        assertEquals(1, chargerCalls)
        assertEquals(0, fuelCalls)
        assertEquals(filter, state.filter)
        assertEquals(StationSearchScope.ManualZone("Madrid", "Madrid"), state.searchScope)
        assertFalse(state.refreshing)
        assertEquals(StationsViewMode.MAP, navigation.viewMode)
    }

    @Test
    fun failedManualFuelRefreshRetainsPreviousDateFiltersAndSavedCache() = runBlocking {
        val parsed = MitecoStationsParser.parse("""{"ListaEESSPrecio":[{"IDEESS":"site","Rótulo":"Estación","Provincia":"MADRID","Municipio":"Madrid","Precio Gasolina 95 E5":"1,50"}]}""")
        val metadata = StationCacheMetadataEntity(downloadedAtMillis = 123L, sourceUpdatedAtMillis = null, sourceUrl = "fixture")
        stations.stationCacheDao().replaceCache(parsed.stations, parsed.prices, metadata)
        var chargerCalls = 0
        val vm = StationsViewModel(
            StationRepository(stations, stations.stationCacheDao()) { throw IOException("private technical details") },
            ChargerRepository(chargers, chargers.chargerCacheDao()) { chargerCalls++; throw IOException() }
        )
        store.put("stations", vm)
        val filter = StationFilter(fuelType = StationFuelType.GASOLINE_95, province = "MADRID", municipality = "Madrid")
        vm.applyFilter(filter)
        vm.refresh()
        val state = withTimeout(5.seconds) { vm.uiState.first { it.refreshError != null && it.metadata != null && it.searchScope is StationSearchScope.ManualZone } }
        assertFalse(state.isRefreshing)
        assertFalse(state.refreshSucceeded)
        assertEquals(metadata, state.metadata)
        assertEquals(filter, state.filter)
        assertEquals(StationSearchScope.ManualZone("MADRID", "Madrid"), state.searchScope)
        assertEquals(0, chargerCalls)
        assertEquals(listOf("site"), stations.stationCacheDao().observeStationsForProducts(StationFuelType.GASOLINE_95.productCodesByPriority, null, null).first().map { it.externalId })
    }
}
