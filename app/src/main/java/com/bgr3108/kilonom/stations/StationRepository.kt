package com.bgr3108.kilonom.stations

import androidx.room.withTransaction
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

    fun observeStations(filter: StationFilter): Flow<List<StationListItem>> =
        dao.observeStationsForProducts(
            productCodes = filter.fuelType.productCodesByPriority,
            province = filter.province,
            municipality = filter.municipality
        ).map { rows -> mapStationRowsForFuelType(rows, filter.fuelType) }

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

        fun isStale(metadata: StationCacheMetadataEntity?, nowMillis: Long = System.currentTimeMillis()): Boolean =
            metadata == null || nowMillis - metadata.downloadedAtMillis >= CACHE_STALE_AFTER_MILLIS
    }
}
