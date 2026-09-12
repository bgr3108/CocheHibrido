package com.bgr3108.kilonom.stations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StationCacheDao {
    @Query("SELECT * FROM station_cache_metadata WHERE id = 1")
    fun observeMetadata(): Flow<StationCacheMetadataEntity?>

    @Query("SELECT * FROM station_cache_metadata WHERE id = 1")
    suspend fun getMetadata(): StationCacheMetadataEntity?

    @Query("SELECT DISTINCT province FROM fuel_stations WHERE province != '' ORDER BY province COLLATE NOCASE")
    fun observeProvinces(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT municipality FROM fuel_stations
        WHERE (:province IS NULL OR province = :province) AND municipality != ''
        ORDER BY municipality COLLATE NOCASE
    """)
    fun observeMunicipalities(province: String?): Flow<List<String>>

    @Query("""
        SELECT s.externalId, s.name, s.address, s.municipality, s.province, s.schedule,
               s.sourceUpdatedAtMillis, p.productCode, p.price, p.productName,
               1 AS hasSelectedFuel
        FROM fuel_stations s
        INNER JOIN fuel_station_prices p ON p.stationId = s.externalId
        WHERE p.productCode IN (:productCodes)
          AND (:province IS NULL OR s.province = :province)
          AND (:municipality IS NULL OR s.municipality = :municipality)
        ORDER BY p.price ASC, s.name COLLATE NOCASE
    """)
    fun observeStationsForProducts(
        productCodes: List<String>,
        province: String?,
        municipality: String?
    ): Flow<List<StationListItem>>

    @Query("DELETE FROM fuel_station_prices")
    suspend fun deleteAllPrices()

    @Query("DELETE FROM fuel_stations")
    suspend fun deleteAllStations()

    @Query("DELETE FROM station_cache_metadata")
    suspend fun deleteMetadata()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(items: List<FuelStationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(items: List<FuelStationPriceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: StationCacheMetadataEntity)

    @Transaction
    suspend fun replaceCache(
        stations: List<FuelStationEntity>,
        prices: List<FuelStationPriceEntity>,
        metadata: StationCacheMetadataEntity
    ) {
        deleteAllPrices()
        deleteAllStations()
        for (chunk in stations.chunked(500)) insertStations(chunk)
        for (chunk in prices.chunked(1_000)) insertPrices(chunk)
        insertMetadata(metadata)
    }

    @Transaction
    suspend fun clearCache() {
        deleteAllPrices()
        deleteAllStations()
        deleteMetadata()
    }
}
