package com.bgr3108.kilonom.chargers

import androidx.room.Room
import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
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
class ChargerCacheDaoTest {
    private lateinit var database: ChargerCacheDatabase
    private lateinit var dao: ChargerCacheDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ChargerCacheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.chargerCacheDao()
    }

    @After fun tearDown() = database.close()

    @Test
    fun replaceAndClear_affectOnlyIndependentChargerCache() = runBlocking {
        dao.replaceCache(
            installations = listOf(installation("a")),
            connectors = listOf(connector("a|1", "a", 50.0)),
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://ripree.test")
        )
        assertEquals(listOf("a"), dao.observeInstallations().first().map { it.externalId })
        assertEquals(listOf("a"), dao.observeConnectorSummaries().first().map { it.installationId })
        dao.clearCache()
        assertTrue(dao.observeInstallations().first().isEmpty())
        assertTrue(dao.observeConnectorSummaries().first().isEmpty())
        assertEquals(null, dao.getMetadata())
    }

    @Test
    fun failedRefresh_preservesExistingChargerCache() = runBlocking {
        dao.replaceCache(
            installations = listOf(installation("preserved")),
            connectors = listOf(connector("preserved|1", "preserved", 22.0)),
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://ripree.test")
        )
        val repository = ChargerRepository(database, dao) { throw IOException("offline") }
        assertTrue(repository.refresh().isFailure)
        assertEquals(listOf("preserved"), dao.observeInstallations().first().map { it.externalId })
    }

    @Test
    fun successfulRefresh_replacesTheSeedOnlyAfterTheNewDatasetIsReady() = runBlocking {
        dao.replaceCache(
            installations = listOf(installation("seed")),
            connectors = listOf(connector("seed|1", "seed", 22.0)),
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "seed")
        )
        val refreshedInstallation = installation("fresh")
        val refreshedConnector = connector("fresh|1", "fresh", 150.0)
        val repository = ChargerRepository(database, dao) {
            ParsedChargers(listOf(refreshedInstallation), listOf(refreshedConnector), 2L)
        }

        assertTrue(repository.refresh().isSuccess)
        assertEquals(listOf("fresh"), dao.observeInstallations().first().map { it.externalId })
        assertEquals(150.0, dao.observeConnectorSummaries().first().single().maxPowerKw!!, 0.0)
    }

    @Test
    fun pagingChargers_filtersByConnectorPowerAndProvinceInSql() = runBlocking {
        val connectors = listOf(
            connector("a|1", "a", 50.0),
            connector("b|1", "b", 11.0)
        )
        dao.replaceCache(
            installations = listOf(installation("a"), installation("b")).withConnectorSummaries(connectors),
            connectors = connectors,
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://ripree.test")
        )
        val page = dao.pagingChargersByPower(
            connectorFilterEnabled = 1,
            connectorCodes = listOf("IEC_62196_T2"),
            minimumPowerKw = 22.0,
            operatorName = null,
            province = "Las Palmas",
            municipality = null
        ).load(PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)).requirePage()

        assertEquals(listOf("a"), page.data.map { it.externalId })
        assertEquals(50.0, page.data.single().maxPowerKw!!, 0.0)
        assertEquals(listOf("Las Palmas"), dao.observeProvinces().first())
        assertEquals(listOf("Las Palmas"), dao.observeMunicipalities("Las Palmas").first())
    }

    @Test
    fun nearbyBounds_queryDoesNotLoadInstallationsOutsideTheLocalArea() = runBlocking {
        val connectors = listOf(connector("near|1", "near", 50.0), connector("far|1", "far", 50.0))
        dao.replaceCache(
            installations = listOf(
                installation("near"),
                installation("far").copy(latitude = 40.40, longitude = -3.70)
            ).withConnectorSummaries(connectors),
            connectors = connectors,
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "https://ripree.test")
        )

        val results = dao.observeChargersInBoundsWithoutConnector(null, null, 28.0, 28.2, -15.5, -15.3).first()

        assertEquals(listOf("near"), results.map { it.externalId })
    }

    @Test
    fun prepackagedSeed_createsAValidPublicCacheWithNearbyResults() {
        runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "charger_seed_asset_test"
        context.deleteDatabase(name)
        val startedAt = SystemClock.elapsedRealtime()
        val seeded = ChargerCacheDatabase.buildDatabase(context, name)
        try {
            val seededDao = seeded.chargerCacheDao()
            assertTrue(seededDao.getInstallationCount() > 10_000)
            assertTrue(seededDao.getConnectorCount() > 30_000)
            assertTrue(
                seededDao.observeChargersInBoundsWithoutConnector(
                    minimumPowerKw = null, operatorName = null,
                    minLatitude = 27.8, maxLatitude = 28.4,
                    minLongitude = -15.9, maxLongitude = -15.1
                ).first().isNotEmpty()
            )
            Log.i("KilonomSeed", "bootstrapToNearbyQueryMs=${SystemClock.elapsedRealtime() - startedAt}")
        } finally {
            seeded.close()
            context.deleteDatabase(name)
        }
        }
    }

    @Test
    fun prepackagedSeed_neverOverwritesAnExistingPublicCache() {
        runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "charger_seed_existing_cache_test"
        context.deleteDatabase(name)
        val existing = Room.databaseBuilder(context, ChargerCacheDatabase::class.java, name).build()
        existing.chargerCacheDao().replaceCache(
            installations = listOf(installation("existing")),
            connectors = listOf(connector("existing|1", "existing", 22.0)),
            metadata = ChargerCacheMetadataEntity(downloadedAtMillis = 1L, sourceUpdatedAtMillis = null, sourceUrl = "existing")
        )
        existing.close()

        val reopened = ChargerCacheDatabase.buildDatabase(context, name)
        try {
            assertEquals(listOf("existing"), reopened.chargerCacheDao().observeInstallations().first().map { it.externalId })
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
        }
    }

    @Test
    fun destructivePublicCacheRecreation_bootstrapsAgainFromTheSeed() {
        runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "charger_seed_recreation_test"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { legacy ->
            // Version 3 used connector ids that were not scoped by EVSE. A version change
            // recreates only this public cache from the corrected packaged seed.
            legacy.version = 3
            legacy.execSQL("CREATE TABLE legacy_cache (id INTEGER PRIMARY KEY)")
        }

        val recreated = ChargerCacheDatabase.buildDatabase(context, name)
        try {
            assertTrue(recreated.chargerCacheDao().getInstallationCount() > 10_000)
        } finally {
            recreated.close()
            context.deleteDatabase(name)
        }
        }
    }

    private fun installation(id: String) = ChargerInstallationEntity(
        externalId = id, name = id, operatorName = null, operatorCode = null, address = "", municipality = "Las Palmas",
        province = "Las Palmas", postalCode = null, locality = null, schedule = null, scheduleType = null,
        latitude = 28.1, longitude = -15.4, sourceUpdatedAtMillis = null
    )

    private fun connector(id: String, installationId: String, power: Double) = ChargerConnectorEntity(
        externalId = id, installationId = installationId, pointId = null, pointCode = null,
        connectorType = "IEC_62196_T2", chargeType = "AC", connectorFormat = "Cable",
        maxPowerKw = power, voltage = null, amperage = null
    )
}

private fun <T : Any> PagingSource.LoadResult<Int, T>.requirePage(): PagingSource.LoadResult.Page<Int, T> =
    this as? PagingSource.LoadResult.Page<Int, T> ?: error("Expected a Paging page")
