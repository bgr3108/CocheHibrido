package com.bgr3108.kilonom.chargers

import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.toStationMapBoundsOrNull
import com.bgr3108.kilonom.stations.boundingBox
import com.bgr3108.kilonom.stations.NEARBY_SEARCH_RADII_KM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChargerRepository(
    private val database: ChargerCacheDatabase,
    private val dao: ChargerCacheDao,
    private val remoteDataSource: ChargerRemoteDataSource = MitecoChargersRemoteDataSource()
) {
    fun observeMetadata(): Flow<ChargerCacheMetadataEntity?> = dao.observeMetadata()
    fun observeProvinces(): Flow<List<String>> = dao.observeProvinces()
    fun observeMunicipalities(province: String?): Flow<List<String>> = dao.observeMunicipalities(province)
    fun observeOperators(): Flow<List<String>> = dao.observeOperators()

    /** The default list uses indexed SQL and Room Paging; the complete installation list is map/distance-only. */
    fun pagingChargers(filter: ChargerFilter): Flow<PagingData<ChargerListItem>> {
        val args = filter.sqlArgs()
        val sourceFactory = {
            when (filter.sortOrder) {
                ChargerSortOrder.NAME -> dao.pagingChargersByName(
                    args.connectorFilterEnabled, args.connectorCodes, filter.minimumPowerKw,
                    filter.operatorName, filter.province, filter.municipality
                )
                else -> dao.pagingChargersByPower(
                    args.connectorFilterEnabled, args.connectorCodes, filter.minimumPowerKw,
                    filter.operatorName, filter.province, filter.municipality
                )
            }
        }
        return Pager(
            config = PagingConfig(
                pageSize = StationRepository.LIST_PAGE_SIZE,
                initialLoadSize = StationRepository.INITIAL_LIST_PAGE_SIZE,
                prefetchDistance = 20
            ),
            pagingSourceFactory = sourceFactory
        )
            .flow.map { page -> page.map(ChargerListRow::toListItem) }
    }

    fun observeChargers(filter: ChargerFilter, currentLocation: StationCoordinates?): Flow<List<ChargerListItem>> {
        val args = filter.sqlArgs()
        return dao.observeChargersForMap(
            args.connectorFilterEnabled, args.connectorCodes, filter.minimumPowerKw,
            filter.operatorName, filter.province, filter.municipality
        ).map { rows ->
            withContext(Dispatchers.Default) {
                val chargers = rows.map(ChargerListRow::toListItem)
                if (filter.sortOrder == ChargerSortOrder.DISTANCE) {
                    filterAndSortChargers(chargers, filter, currentLocation)
                } else chargers
            }
        }
    }

    /** The map uses the same logical filters as the list, constrained by the current viewport. */
    fun observeChargersForMap(
        filter: ChargerFilter,
        bounds: StationMapBounds,
        currentLocation: StationCoordinates?
    ): Flow<List<ChargerListItem>> {
        val args = filter.sqlArgs()
        return dao.observeChargersForMapViewport(
            connectorFilterEnabled = args.connectorFilterEnabled,
            connectorCodes = args.connectorCodes,
            minimumPowerKw = filter.minimumPowerKw,
            operatorName = filter.operatorName,
            province = filter.province,
            municipality = filter.municipality,
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            crossesAntimeridian = if (bounds.crossesAntimeridian) 1 else 0
        ).map { rows ->
            withContext(Dispatchers.Default) {
                val chargers = rows.map(ChargerListRow::toListItem)
                if (filter.sortOrder == ChargerSortOrder.DISTANCE) {
                    filterAndSortChargers(chargers, filter, currentLocation)
                } else chargers
            }
        }
    }

    /** One aggregate row positions a manual scope before the map asks Room for its viewport. */
    fun observeChargerMapBounds(filter: ChargerFilter): Flow<StationMapBounds?> {
        val args = filter.sqlArgs()
        return dao.observeChargerMapBounds(
            connectorFilterEnabled = args.connectorFilterEnabled,
            connectorCodes = args.connectorCodes,
            minimumPowerKw = filter.minimumPowerKw,
            operatorName = filter.operatorName,
            province = filter.province,
            municipality = filter.municipality
        ).map { it.toStationMapBoundsOrNull() }
    }

    /** Nearby mode never loads the national installation list into memory. */
    fun observeNearbyChargers(
        filter: ChargerFilter,
        origin: StationCoordinates
    ): Flow<List<ChargerListItem>> {
        val bounds = origin.boundingBox(NEARBY_SEARCH_RADII_KM.last())
        val source = if (requiresConnectorLookup(filter)) {
            val connector = requireNotNull(filter.connectorType)
            dao.observeChargersInBoundsWithConnector(
                connector.sourceValues.toList(), filter.minimumPowerKw, filter.operatorName,
                bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude
            )
        } else {
            dao.observeChargersInBoundsWithoutConnector(
                filter.minimumPowerKw, filter.operatorName,
                bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude
            )
        }
        return source.map { rows ->
            withContext(Dispatchers.Default) { nearbyChargers(rows.map(ChargerListRow::toListItem), origin) }
        }
    }

    /** Nearby maps intersect the local search radius with the visible viewport before Room runs. */
    fun observeNearbyChargersForMap(
        filter: ChargerFilter,
        origin: StationCoordinates,
        viewport: StationMapBounds
    ): Flow<List<ChargerListItem>> {
        val bounds = viewport.intersect(origin.boundingBox(NEARBY_SEARCH_RADII_KM.last())) ?: return flowOf(emptyList())
        val args = filter.sqlArgs()
        return dao.observeChargersForMapViewport(
            connectorFilterEnabled = args.connectorFilterEnabled,
            connectorCodes = args.connectorCodes,
            minimumPowerKw = filter.minimumPowerKw,
            operatorName = filter.operatorName,
            province = null,
            municipality = null,
            south = bounds.south,
            west = bounds.west,
            north = bounds.north,
            east = bounds.east,
            crossesAntimeridian = if (bounds.crossesAntimeridian) 1 else 0
        ).map { rows ->
            withContext(Dispatchers.Default) { nearbyChargers(rows.map(ChargerListRow::toListItem), origin) }
        }
    }

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        val trace = ChargerRefreshDebugTrace.create()
        runCatching {
            val parsed = (remoteDataSource as? MitecoChargersRemoteDataSource)?.download(trace) ?: remoteDataSource.download()
            if (parsed.installations.isEmpty() || parsed.connectors.isEmpty()) error("RIPREE no devolvió cargadores válidos")
            trace?.summaryStarted()
            val installations = parsed.installations.withConnectorSummaries(parsed.connectors)
            trace?.summaryCompleted(installations.size, parsed.connectors.size)
            trace?.roomStarted()
            database.withTransaction {
                dao.replaceCache(
                    installations,
                    parsed.connectors,
                    ChargerCacheMetadataEntity(
                        downloadedAtMillis = System.currentTimeMillis(),
                        sourceUpdatedAtMillis = parsed.sourceUpdatedAtMillis,
                        sourceUrl = MITECO_CHARGERS_URL
                    ),
                    trace
                )
            }
            trace?.roomCompleted()
            trace?.completed(installations.size, parsed.connectors.size, trace.lastDownloadedBytes)
            parsed.installations.size
        }.onFailure { trace?.failed(it) }
    }

    suspend fun getMetadata(): ChargerCacheMetadataEntity? = dao.getMetadata()
    suspend fun clearCache() = database.withTransaction { dao.clearCache() }
    fun isStale(metadata: ChargerCacheMetadataEntity?, nowMillis: Long = System.currentTimeMillis()): Boolean =
        shouldRefreshChargerCache(metadata, nowMillis)
}

private data class ChargerSqlArgs(val connectorFilterEnabled: Int, val connectorCodes: List<String>)

private fun ChargerFilter.sqlArgs(): ChargerSqlArgs = connectorType?.let {
    ChargerSqlArgs(1, it.sourceValues.toList())
} ?: ChargerSqlArgs(0, listOf("__no_connector_filter__"))

internal fun requiresConnectorLookup(filter: ChargerFilter): Boolean = filter.connectorType != null

/** Builds the list/map summary once at public-cache import time, rather than for each filter change. */
internal fun List<ChargerInstallationEntity>.withConnectorSummaries(
    connectors: List<ChargerConnectorEntity>
): List<ChargerInstallationEntity> {
    val summaries = HashMap<String, MutableList<ChargerConnectorEntity>>()
    connectors.forEach { connector -> summaries.getOrPut(connector.installationId, ::mutableListOf).add(connector) }
    return map { installation ->
        val installationConnectors = summaries[installation.externalId].orEmpty()
        installation.copy(
            maxPowerKw = installationConnectors.mapNotNull(ChargerConnectorEntity::maxPowerKw).maxOrNull(),
            connectorTypesEncoded = installationConnectors.mapNotNull(ChargerConnectorEntity::connectorType)
                .distinct()
                .sorted()
                .joinToString(CHARGER_CONNECTOR_SEPARATOR)
        )
    }
}

internal fun shouldRefreshChargerCache(
    metadata: ChargerCacheMetadataEntity?,
    nowMillis: Long
): Boolean = metadata == null || nowMillis - metadata.downloadedAtMillis >= StationRepository.CACHE_STALE_AFTER_MILLIS
