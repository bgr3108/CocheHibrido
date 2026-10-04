package com.bgr3108.kilonom.stations

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.paging.PagingSource
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
        SELECT s.externalId, s.name, s.address, s.municipality, s.province, s.latitude, s.longitude, s.schedule,
               s.sourceUpdatedAtMillis, p.productCode, p.price, p.productName,
               1 AS hasSelectedFuel, NULL AS distanceMeters
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

    @Query("""
        WITH preferred_price AS (
            SELECT stationId, MIN(visibleFuelPriority) AS priority
            FROM fuel_station_prices
            WHERE visibleFuelType = :fuelType
            GROUP BY stationId
        )
        SELECT s.externalId, s.name, s.address, s.municipality, s.province, s.latitude, s.longitude, s.schedule,
               s.sourceUpdatedAtMillis, p.productCode, p.price, p.productName,
               1 AS hasSelectedFuel, NULL AS distanceMeters
        FROM preferred_price best
        INNER JOIN fuel_station_prices p ON p.stationId = best.stationId
            AND p.visibleFuelType = :fuelType AND p.visibleFuelPriority = best.priority
        INNER JOIN fuel_stations s ON s.externalId = p.stationId
        WHERE (:province IS NULL OR s.province = :province)
          AND (:municipality IS NULL OR s.municipality = :municipality)
        ORDER BY p.price ASC, s.name COLLATE NOCASE
    """)
    fun pagingStationsForFuel(
        fuelType: String,
        province: String?,
        municipality: String?
    ): PagingSource<Int, StationListItem>

    @Query("""
        WITH preferred_price AS (
            SELECT stationId, MIN(visibleFuelPriority) AS priority
            FROM fuel_station_prices
            WHERE visibleFuelType = :fuelType
            GROUP BY stationId
        )
        SELECT s.externalId, s.name, s.address, s.municipality, s.province, s.latitude, s.longitude, s.schedule,
               s.sourceUpdatedAtMillis, p.productCode, p.price, p.productName,
               1 AS hasSelectedFuel, NULL AS distanceMeters
        FROM preferred_price best
        INNER JOIN fuel_station_prices p ON p.stationId = best.stationId
            AND p.visibleFuelType = :fuelType AND p.visibleFuelPriority = best.priority
        INNER JOIN fuel_stations s ON s.externalId = p.stationId
        WHERE (:province IS NULL OR s.province = :province)
          AND (:municipality IS NULL OR s.municipality = :municipality)
        ORDER BY p.price ASC, s.name COLLATE NOCASE
    """)
    fun observeStationsForMap(
        fuelType: String,
        province: String?,
        municipality: String?
    ): Flow<List<StationListItem>>

    /** Local candidates only. The repository chooses an adaptive radius and applies Haversine. */
    @Query("""
        WITH preferred_price AS (
            SELECT stationId, MIN(visibleFuelPriority) AS priority
            FROM fuel_station_prices
            WHERE visibleFuelType = :fuelType
            GROUP BY stationId
        )
        SELECT s.externalId, s.name, s.address, s.municipality, s.province, s.latitude, s.longitude, s.schedule,
               s.sourceUpdatedAtMillis, p.productCode, p.price, p.productName,
               1 AS hasSelectedFuel, NULL AS distanceMeters
        FROM preferred_price best
        INNER JOIN fuel_station_prices p ON p.stationId = best.stationId
            AND p.visibleFuelType = :fuelType AND p.visibleFuelPriority = best.priority
        INNER JOIN fuel_stations s ON s.externalId = p.stationId
        WHERE s.latitude BETWEEN :minLatitude AND :maxLatitude
          AND s.longitude BETWEEN :minLongitude AND :maxLongitude
        ORDER BY p.price ASC, s.name COLLATE NOCASE
    """)
    fun observeStationsInBounds(
        fuelType: String,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): Flow<List<StationListItem>>

    /**
     * The list query intentionally exposes one selected fuel only. This small detail query keeps
     * all other visible prices lazy and chooses the same preferred product used by list queries.
     */
    @Query("""
        WITH preferred_price AS (
            SELECT visibleFuelType, MIN(visibleFuelPriority) AS priority
            FROM fuel_station_prices
            WHERE stationId = :stationId
              AND visibleFuelType IS NOT NULL
              AND price > 0
            GROUP BY visibleFuelType
        )
        SELECT p.visibleFuelType AS fuelType, p.price
        FROM preferred_price best
        INNER JOIN fuel_station_prices p ON p.stationId = :stationId
            AND p.visibleFuelType = best.visibleFuelType
            AND p.visibleFuelPriority = best.priority
        ORDER BY CASE p.visibleFuelType
            WHEN 'GASOLINE_95' THEN 1
            WHEN 'GASOLINE_98' THEN 2
            WHEN 'DIESEL' THEN 3
            WHEN 'ADBLUE' THEN 4
            WHEN 'GLP' THEN 5
            ELSE 6
        END
    """)
    suspend fun getVisibleFuelPrices(stationId: String): List<StationFuelPriceRow>

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
