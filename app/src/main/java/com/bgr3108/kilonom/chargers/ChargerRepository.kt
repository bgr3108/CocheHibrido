package com.bgr3108.kilonom.chargers

import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.boundingBox
import com.bgr3108.kilonom.stations.NEARBY_SEARCH_RADII_KM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = remoteDataSource.download()
            if (parsed.installations.isEmpty() || parsed.connectors.isEmpty()) error("RIPREE no devolvió cargadores válidos")
            database.withTransaction {
                dao.replaceCache(
                    parsed.installations.withConnectorSummaries(parsed.connectors),
                    parsed.connectors,
                    ChargerCacheMetadataEntity(
                        downloadedAtMillis = System.currentTimeMillis(),
                        sourceUpdatedAtMillis = parsed.sourceUpdatedAtMillis,
                        sourceUrl = MITECO_CHARGERS_URL
                    )
                )
            }
            parsed.installations.size
        }
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

internal fun List<ChargerInstallationEntity>.toChargers(
    connectors: List<ChargerConnectorSummary>
): List<ChargerListItem> {
    val connectorsByInstallation = HashMap<String, MutableList<ChargerConnectorSummary>>()
    connectors.forEach { connector ->
        connectorsByInstallation.getOrPut(connector.installationId, ::mutableListOf).add(connector)
    }
    return map { installation ->
        val installationConnectors = connectorsByInstallation[installation.externalId].orEmpty()
        ChargerListItem(
            externalId = installation.externalId,
            name = installation.name,
            operatorName = installation.operatorName,
            address = installation.address,
            municipality = installation.municipality,
            province = installation.province,
            schedule = installation.schedule,
            latitude = installation.latitude,
            longitude = installation.longitude,
            sourceUpdatedAtMillis = installation.sourceUpdatedAtMillis,
            connectorTypes = installationConnectors.mapNotNull(ChargerConnectorSummary::connectorType).distinct().sorted(),
            maxPowerKw = installationConnectors.mapNotNull(ChargerConnectorSummary::maxPowerKw).maxOrNull()
        )
    }
}
