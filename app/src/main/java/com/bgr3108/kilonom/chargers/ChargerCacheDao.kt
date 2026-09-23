package com.bgr3108.kilonom.chargers

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargerCacheDao {
    @Query("SELECT * FROM charger_cache_metadata WHERE id = 1")
    fun observeMetadata(): Flow<ChargerCacheMetadataEntity?>

    @Query("SELECT * FROM charger_cache_metadata WHERE id = 1")
    suspend fun getMetadata(): ChargerCacheMetadataEntity?

    @Query("SELECT COUNT(*) FROM charger_installations")
    suspend fun getInstallationCount(): Int

    @Query("SELECT COUNT(*) FROM charger_connectors")
    suspend fun getConnectorCount(): Int

    @Query("SELECT DISTINCT province FROM charger_installations WHERE province != '' ORDER BY province COLLATE NOCASE")
    fun observeProvinces(): Flow<List<String>>

    @Query("SELECT DISTINCT municipality FROM charger_installations WHERE (:province IS NULL OR province = :province) AND municipality != '' ORDER BY municipality COLLATE NOCASE")
    fun observeMunicipalities(province: String?): Flow<List<String>>

    @Query("SELECT DISTINCT operatorName FROM charger_installations WHERE operatorName IS NOT NULL AND operatorName != '' ORDER BY operatorName COLLATE NOCASE")
    fun observeOperators(): Flow<List<String>>

    @Query("SELECT * FROM charger_installations")
    fun observeInstallations(): Flow<List<ChargerInstallationEntity>>

    @Query("SELECT installationId, connectorType, maxPowerKw FROM charger_connectors")
    fun observeConnectorSummaries(): Flow<List<ChargerConnectorSummary>>

    @Query("""
        SELECT externalId, name, operatorName, address, municipality, province, schedule, latitude, longitude,
               sourceUpdatedAtMillis, maxPowerKw, connectorTypesEncoded
        FROM charger_installations i
        WHERE (:connectorFilterEnabled = 0 OR EXISTS (
            SELECT 1 FROM charger_connectors c
            WHERE c.installationId = i.externalId AND c.connectorType IN (:connectorCodes)
        ))
          AND (:minimumPowerKw IS NULL OR i.maxPowerKw >= :minimumPowerKw)
          AND (:operatorName IS NULL OR i.operatorName = :operatorName)
          AND (:province IS NULL OR i.province = :province)
          AND (:municipality IS NULL OR i.municipality = :municipality)
        ORDER BY i.maxPowerKw DESC, i.name COLLATE NOCASE
    """)
    fun pagingChargersByPower(
        connectorFilterEnabled: Int,
        connectorCodes: List<String>,
        minimumPowerKw: Double?,
        operatorName: String?,
        province: String?,
        municipality: String?
    ): PagingSource<Int, ChargerListRow>

    @Query("""
        SELECT externalId, name, operatorName, address, municipality, province, schedule, latitude, longitude,
               sourceUpdatedAtMillis, maxPowerKw, connectorTypesEncoded
        FROM charger_installations i
        WHERE (:connectorFilterEnabled = 0 OR EXISTS (
            SELECT 1 FROM charger_connectors c
            WHERE c.installationId = i.externalId AND c.connectorType IN (:connectorCodes)
        ))
          AND (:minimumPowerKw IS NULL OR i.maxPowerKw >= :minimumPowerKw)
          AND (:operatorName IS NULL OR i.operatorName = :operatorName)
          AND (:province IS NULL OR i.province = :province)
          AND (:municipality IS NULL OR i.municipality = :municipality)
        ORDER BY COALESCE(i.operatorName, i.name) COLLATE NOCASE, i.name COLLATE NOCASE
    """)
    fun pagingChargersByName(
        connectorFilterEnabled: Int,
        connectorCodes: List<String>,
        minimumPowerKw: Double?,
        operatorName: String?,
        province: String?,
        municipality: String?
    ): PagingSource<Int, ChargerListRow>

    @Query("""
        SELECT externalId, name, operatorName, address, municipality, province, schedule, latitude, longitude,
               sourceUpdatedAtMillis, maxPowerKw, connectorTypesEncoded
        FROM charger_installations i
        WHERE (:connectorFilterEnabled = 0 OR EXISTS (
            SELECT 1 FROM charger_connectors c
            WHERE c.installationId = i.externalId AND c.connectorType IN (:connectorCodes)
        ))
          AND (:minimumPowerKw IS NULL OR i.maxPowerKw >= :minimumPowerKw)
          AND (:operatorName IS NULL OR i.operatorName = :operatorName)
          AND (:province IS NULL OR i.province = :province)
          AND (:municipality IS NULL OR i.municipality = :municipality)
        ORDER BY i.name COLLATE NOCASE
    """)
    fun observeChargersForMap(
        connectorFilterEnabled: Int,
        connectorCodes: List<String>,
        minimumPowerKw: Double?,
        operatorName: String?,
        province: String?,
        municipality: String?
    ): Flow<List<ChargerListRow>>

    /**
     * Local candidates only. The common nearby path has no connector filter, so it must not
     * visit the 43k-row connector table merely to prove that no filter is active.
     */
    @Query("""
        SELECT externalId, name, operatorName, address, municipality, province, schedule, latitude, longitude,
               sourceUpdatedAtMillis, maxPowerKw, connectorTypesEncoded
        FROM charger_installations i
        WHERE (:minimumPowerKw IS NULL OR i.maxPowerKw >= :minimumPowerKw)
          AND (:operatorName IS NULL OR i.operatorName = :operatorName)
          AND i.latitude BETWEEN :minLatitude AND :maxLatitude
          AND i.longitude BETWEEN :minLongitude AND :maxLongitude
    """)
    fun observeChargersInBoundsWithoutConnector(
        minimumPowerKw: Double?,
        operatorName: String?,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): Flow<List<ChargerListRow>>

    /** Same local candidate query when a connector filter is explicitly selected. */
    @Query("""
        SELECT externalId, name, operatorName, address, municipality, province, schedule, latitude, longitude,
               sourceUpdatedAtMillis, maxPowerKw, connectorTypesEncoded
        FROM charger_installations i
        WHERE EXISTS (
            SELECT 1 FROM charger_connectors c
            WHERE c.installationId = i.externalId AND c.connectorType IN (:connectorCodes)
        )
          AND (:minimumPowerKw IS NULL OR i.maxPowerKw >= :minimumPowerKw)
          AND (:operatorName IS NULL OR i.operatorName = :operatorName)
          AND i.latitude BETWEEN :minLatitude AND :maxLatitude
          AND i.longitude BETWEEN :minLongitude AND :maxLongitude
    """)
    fun observeChargersInBoundsWithConnector(
        connectorCodes: List<String>,
        minimumPowerKw: Double?,
        operatorName: String?,
        minLatitude: Double,
        maxLatitude: Double,
        minLongitude: Double,
        maxLongitude: Double
    ): Flow<List<ChargerListRow>>

    @Query("DELETE FROM charger_connectors") suspend fun deleteConnectors()
    @Query("DELETE FROM charger_installations") suspend fun deleteInstallations()
    @Query("DELETE FROM charger_cache_metadata") suspend fun deleteMetadata()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertInstallations(items: List<ChargerInstallationEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertConnectors(items: List<ChargerConnectorEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMetadata(item: ChargerCacheMetadataEntity)

    @Transaction
    suspend fun replaceCache(
        installations: List<ChargerInstallationEntity>,
        connectors: List<ChargerConnectorEntity>,
        metadata: ChargerCacheMetadataEntity
    ) {
        deleteConnectors()
        deleteInstallations()
        installations.chunked(500).forEach { insertInstallations(it) }
        connectors.chunked(1_000).forEach { insertConnectors(it) }
        insertMetadata(metadata)
    }

    @Transaction
    suspend fun clearCache() {
        deleteConnectors()
        deleteInstallations()
        deleteMetadata()
    }
}
