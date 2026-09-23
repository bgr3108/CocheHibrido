package com.bgr3108.kilonom.stations

import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StationRepository(
    private val database: StationCacheDatabase,
    private val dao: StationCacheDao,
    private val remoteDataSource: StationRemoteDataSource = MitecoStationsRemoteDataSource()
) {
    fun observeMetadata(): Flow<StationCacheMetadataEntity?> = dao.observeMetadata()
    fun observeProvinces(): Flow<List<String>> = dao.observeProvinces()
    fun observeMunicipalities(province: String?): Flow<List<String>> = dao.observeMunicipalities(province)

    /**
     * The normal list path stays in SQLite: one visible product and one page of stations at a
     * time. Distance is intentionally separate because it requires the ephemeral user position.
     */
    fun pagingStations(filter: StationFilter): Flow<PagingData<StationListItem>> =
        Pager(PagingConfig(pageSize = LIST_PAGE_SIZE, initialLoadSize = INITIAL_LIST_PAGE_SIZE, prefetchDistance = 20)) {
            dao.pagingStationsForFuel(filter.fuelType.name, filter.province, filter.municipality)
        }.flow.map { page ->
            page.map { item -> item.copy(productName = filter.fuelType.displayName) }
        }

    fun observeStations(
        filter: StationFilter,
        currentLocation: StationCoordinates? = null
    ): Flow<List<StationListItem>> =
        dao.observeStationsForMap(
            fuelType = filter.fuelType.name,
            province = filter.province,
            municipality = filter.municipality
        ).map { rows ->
            withContext(Dispatchers.Default) {
                val stations = rows.map { it.copy(productName = filter.fuelType.displayName) }
                if (filter.sortOrder == StationSortOrder.DISTANCE) {
                    withDistancesAndSort(stations, currentLocation, filter.sortOrder)
                } else stations
            }
        }

    /** Nearby mode queries just the largest local bounding box, then narrows it adaptively. */
    fun observeNearbyStations(
        filter: StationFilter,
        origin: StationCoordinates
    ): Flow<List<StationListItem>> {
        val bounds = origin.boundingBox(NEARBY_SEARCH_RADII_KM.last())
        return dao.observeStationsInBounds(
            fuelType = filter.fuelType.name,
            minLatitude = bounds.minLatitude,
            maxLatitude = bounds.maxLatitude,
            minLongitude = bounds.minLongitude,
            maxLongitude = bounds.maxLongitude
        ).map { rows ->
            withContext(Dispatchers.Default) {
                nearbyStations(rows.map { it.copy(productName = filter.fuelType.displayName) }, origin)
            }
        }
    }

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = remoteDataSource.download()
            if (parsed.stations.isEmpty()) error("MITECO no devolvió estaciones válidas")
            database.withTransaction {
                dao.replaceCache(
                    parsed.stations,
                    parsed.prices,
                    StationCacheMetadataEntity(
                        downloadedAtMillis = System.currentTimeMillis(),
                        sourceUpdatedAtMillis = parsed.sourceUpdatedAtMillis,
                        sourceUrl = MITECO_STATIONS_URL
                    )
                )
            }
            parsed.stations.size
        }
    }

    suspend fun hasCache(): Boolean = dao.getMetadata() != null

    suspend fun clearCache() = database.withTransaction { dao.clearCache() }

    companion object {
        const val CACHE_STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
        const val LIST_PAGE_SIZE = 40
        const val INITIAL_LIST_PAGE_SIZE = 60

        fun isStale(metadata: StationCacheMetadataEntity?, nowMillis: Long = System.currentTimeMillis()): Boolean =
            metadata == null || nowMillis - metadata.downloadedAtMillis >= CACHE_STALE_AFTER_MILLIS
    }
}
