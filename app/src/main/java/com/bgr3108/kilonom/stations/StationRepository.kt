package com.bgr3108.kilonom.stations

import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    /** The map never materialises a whole manual zone: Room intersects it with the current viewport. */
    fun observeStationsForMap(
        filter: StationFilter,
        bounds: StationMapBounds,
        currentLocation: StationCoordinates? = null
    ): Flow<List<StationListItem>> =
        dao.observeStationsForMapViewport(
            fuelType = filter.fuelType.name,
            province = filter.province,
            municipality = filter.municipality,
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            crossesAntimeridian = if (bounds.crossesAntimeridian) 1 else 0
        ).map { rows ->
            withContext(Dispatchers.Default) {
                val stations = rows.map { it.copy(productName = filter.fuelType.displayName) }
                if (filter.sortOrder == StationSortOrder.DISTANCE) {
                    withDistancesAndSort(stations, currentLocation, filter.sortOrder)
                } else stations
            }
        }

    /** One aggregate row positions a manual scope before the map asks Room for its viewport. */
    fun observeStationMapBounds(filter: StationFilter): Flow<StationMapBounds?> =
        dao.observeStationMapBounds(
            fuelType = filter.fuelType.name,
            province = filter.province,
            municipality = filter.municipality
        ).map(StationMapBoundsRow::toStationMapBoundsOrNull)

    /** Nearby mode queries just the largest local bounding box, then narrows it adaptively. */
    fun observeNearbyStations(
        filter: StationFilter,
        origin: StationCoordinates
    ): Flow<List<StationListItem>> = observeNearbyStationSelection(filter, origin).map { it.items }

    internal fun observeNearbyStationSelection(
        filter: StationFilter,
        origin: StationCoordinates
    ): Flow<NearbySearchResult<StationListItem>> {
        val bounds = origin.boundingBox(NEARBY_SEARCH_RADII_KM.last())
        return dao.observeStationsInBounds(
            fuelType = filter.fuelType.name,
            minLatitude = bounds.minLatitude,
            maxLatitude = bounds.maxLatitude,
            minLongitude = bounds.minLongitude,
            maxLongitude = bounds.maxLongitude
        ).map { rows ->
            withContext(Dispatchers.Default) {
                nearbyStationSelection(rows.map { it.copy(productName = filter.fuelType.displayName) }, origin)
            }
        }
    }

    /** The viewport can restrict the selected nearby boundary, never expand it. */
    fun observeNearbyStationsForMap(
        filter: StationFilter,
        origin: StationCoordinates,
        viewport: StationMapBounds,
        radiusKm: Double
    ): Flow<List<StationListItem>> {
        // Keep the list's candidate box: a smaller approximate box could drop boundary points.
        val bounds = viewport.intersect(origin.boundingBox(NEARBY_SEARCH_RADII_KM.last())) ?: return flowOf(emptyList())
        return dao.observeStationsForMapViewport(
            fuelType = filter.fuelType.name,
            province = null,
            municipality = null,
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            crossesAntimeridian = if (bounds.crossesAntimeridian) 1 else 0
        ).map { rows ->
            withContext(Dispatchers.Default) {
                withDistancesAndSort(
                    rows.map { it.copy(productName = filter.fuelType.displayName) }, origin, StationSortOrder.DISTANCE
                ).filter { it.distanceMeters?.let { distance -> distance <= radiusKm * 1_000 } == true }
            }
        }
    }

    /** Loads full supported fuel prices only when the user opens a station detail. */
    suspend fun getVisibleFuelPrices(stationId: String): List<StationFuelPrice> =
        withContext(Dispatchers.IO) {
            dao.getVisibleFuelPrices(stationId).mapNotNull { row ->
                runCatching { StationFuelType.valueOf(row.fuelType) }.getOrNull()
                    ?.let { type -> StationFuelPrice(type, row.price) }
            }
        }

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        try {
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
            Result.success(parsed.stations.size)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun getMetadata(): StationCacheMetadataEntity? = dao.getMetadata()

    suspend fun clearCache() = database.withTransaction { dao.clearCache() }

    companion object {
        const val CACHE_STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
        const val LIST_PAGE_SIZE = 40
        const val INITIAL_LIST_PAGE_SIZE = 60

        fun isStale(metadata: StationCacheMetadataEntity?, nowMillis: Long = System.currentTimeMillis()): Boolean =
            metadata == null || nowMillis - metadata.downloadedAtMillis >= CACHE_STALE_AFTER_MILLIS
    }
}
