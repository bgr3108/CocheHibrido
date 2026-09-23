package com.bgr3108.kilonom.stations

import androidx.room.Room
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun station(id: String, name: String, latitude: Double? = null, longitude: Double? = null) = FuelStationEntity(
        externalId = id, name = name, address = "", municipality = name, province = if (name == "Madrid") "MADRID" else "SANTA CRUZ DE TENERIFE",
        postalCode = null, latitude = latitude, longitude = longitude, schedule = null, margin = null,
        saleType = null, submissionType = null, sourceUpdatedAtMillis = null
    )

    private fun price(id: String, value: Double, code: String = "gasolina_95_e5", priority: Int = 0) = FuelStationPriceEntity(
        stationId = id, productCode = code, productName = "Gasolina 95 E5", price = value,
        visibleFuelType = StationFuelType.GASOLINE_95.name, visibleFuelPriority = priority
    )
}

private fun <T : Any> PagingSource.LoadResult<Int, T>.requirePage(): PagingSource.LoadResult.Page<Int, T> =
    this as? PagingSource.LoadResult.Page<Int, T> ?: error("Expected a Paging page")
