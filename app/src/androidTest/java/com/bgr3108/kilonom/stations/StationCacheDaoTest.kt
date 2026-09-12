package com.bgr3108.kilonom.stations

import androidx.room.Room
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

    private fun station(id: String, name: String) = FuelStationEntity(
        externalId = id, name = name, address = "", municipality = "Madrid", province = "MADRID",
        postalCode = null, latitude = null, longitude = null, schedule = null, margin = null,
        saleType = null, submissionType = null, sourceUpdatedAtMillis = null
    )

    private fun price(id: String, value: Double) = FuelStationPriceEntity(
        stationId = id, productCode = "gasolina_95_e5", productName = "Gasolina 95 E5", price = value
    )
}
