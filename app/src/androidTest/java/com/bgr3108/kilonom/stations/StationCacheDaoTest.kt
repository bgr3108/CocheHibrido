package com.bgr3108.kilonom.stations

import androidx.room.Room
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class StationCacheDaoTest {
    private lateinit var database: StationCacheDatabase
    private lateinit var dao: StationCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            StationCacheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.stationCacheDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replaceCache_replacesOnlyStationCache_andQueriesSelectedFuelByPrice() = runBlocking {
        dao.replaceCache(
            stations = listOf(station("a", "Primera"), station("b", "Segunda")),
            prices = listOf(
                price("a", 1.70),
                price("b", 1.60)
            ),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )

        val firstCache = dao.observeStationsForProducts(StationFuelType.GASOLINE_95.productCodesByPriority, null, null).first()
        assertEquals(listOf("b", "a"), firstCache.map { it.externalId })

        dao.replaceCache(
            stations = listOf(station("c", "Nueva")),
            prices = listOf(price("c", 1.50)),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 2L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )

        assertEquals(listOf("c"), dao.observeStationsForProducts(StationFuelType.GASOLINE_95.productCodesByPriority, null, null).first().map { it.externalId })
        assertEquals(2L, dao.getMetadata()!!.downloadedAtMillis)
    }

    @Test
    fun failedRefresh_preservesExistingCache() = runBlocking {
        dao.replaceCache(
            stations = listOf(station("a", "Conservada")),
            prices = listOf(price("a", 1.70)),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )
        val repository = StationRepository(database, dao) { throw IOException("offline") }

        assertTrue(repository.refresh().isFailure)
        assertEquals(listOf("a"), dao.observeStationsForProducts(StationFuelType.GASOLINE_95.productCodesByPriority, null, null).first().map { it.externalId })
    }

    @Test
    fun pagingStations_filtersInSql_andUsesThePreferredVisibleFuelPrice() = runBlocking {
        dao.replaceCache(
            stations = listOf(station("a", "Madrid"), station("b", "Tenerife")),
            prices = listOf(
                price("a", 1.70, "gasolina_95_e5", 0),
                price("a", 1.71, "gasolina_95_e10", 1),
                price("b", 1.60, "gasolina_95_e5", 0)
            ),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )

        val page = dao.pagingStationsForFuel(StationFuelType.GASOLINE_95.name, "MADRID", null)
            .load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false))
            .requirePage()

        assertEquals(listOf("a"), page.data.map { it.externalId })
        assertEquals(1.70, page.data.single().price, 0.0)
        assertEquals(listOf("Madrid", "Tenerife"), dao.observeMunicipalities(null).first())
        assertEquals(listOf("Madrid"), dao.observeMunicipalities("MADRID").first())
    }

    @Test
    fun nearbyBounds_queryReturnsOnlyLocalCandidates() = runBlocking {
        dao.replaceCache(
            stations = listOf(
                station("near", "Cerca", 28.10, -15.40),
                station("far", "Lejos", 40.40, -3.70)
            ),
            prices = listOf(price("near", 1.60), price("far", 1.50)),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )

        val results = dao.observeStationsInBounds(
            StationFuelType.GASOLINE_95.name, 28.0, 28.2, -15.5, -15.3
        ).first()

        assertEquals(listOf("near"), results.map { it.externalId })
    }

    @Test
    fun detailFuelPrices_returnOnlyPreferredAvailableVisibleFuelsInPresentationOrder() = runBlocking {
        dao.replaceCache(
            stations = listOf(station("detail", "Detalle")),
            prices = listOf(
                price("detail", 1.50),
                price("detail", 1.55, "gasolina_95_e10", 1),
                price("detail", 1.65, "gasolina_98_e5", 0, StationFuelType.GASOLINE_98),
                price("detail", 1.40, "gasoleo_a", 0, StationFuelType.DIESEL),
                price("detail", 0.0, "adblue", 0, StationFuelType.ADBLUE),
                price("detail", 0.90, "gases_licuados_del_petroleo", 0, StationFuelType.GLP)
            ),
            metadata = StationCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://miteco.test")
        )

        val prices = dao.getVisibleFuelPrices("detail")

        assertEquals(
            listOf(
                StationFuelType.GASOLINE_95.name,
                StationFuelType.GASOLINE_98.name,
                StationFuelType.DIESEL.name,
                StationFuelType.GLP.name
            ),
            prices.map { it.fuelType }
        )
        assertEquals(listOf(1.50, 1.65, 1.40, 0.90), prices.map { it.price })
        assertFalse(prices.any { it.fuelType == StationFuelType.ADBLUE.name })
    }

    private fun station(id: String, name: String, latitude: Double? = null, longitude: Double? = null) = FuelStationEntity(
        externalId = id, name = name, address = "", municipality = name, province = if (name == "Madrid") "MADRID" else "SANTA CRUZ DE TENERIFE",
        postalCode = null, latitude = latitude, longitude = longitude, schedule = null, margin = null,
        saleType = null, submissionType = null, sourceUpdatedAtMillis = null
    )

    private fun price(
        id: String,
        value: Double,
        code: String = "gasolina_95_e5",
        priority: Int = 0,
        fuelType: StationFuelType = StationFuelType.GASOLINE_95
    ) = FuelStationPriceEntity(
        stationId = id, productCode = code, productName = fuelType.displayName, price = value,
        visibleFuelType = fuelType.name, visibleFuelPriority = priority
    )
}

private fun <T : Any> PagingSource.LoadResult<Int, T>.requirePage(): PagingSource.LoadResult.Page<Int, T> =
    this as? PagingSource.LoadResult.Page<Int, T> ?: error("Expected a Paging page")
