package com.bgr3108.kilonom.stations

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_stations",
    indices = [Index("province"), Index("municipality")]
)
data class FuelStationEntity(
    @PrimaryKey val externalId: String,
    val name: String,
    val address: String,
    val municipality: String,
    val province: String,
    val postalCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val schedule: String?,
    val margin: String?,
    val saleType: String?,
    val submissionType: String?,
    val sourceUpdatedAtMillis: Long?
)

/** Every price product supplied by MITECO is retained instead of limiting the cache to fuels shown today. */
@Entity(
    tableName = "fuel_station_prices",
    primaryKeys = ["stationId", "productCode"],
    foreignKeys = [
        ForeignKey(
            entity = FuelStationEntity::class,
            parentColumns = ["externalId"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("stationId"), Index("productCode")]
)
data class FuelStationPriceEntity(
    val stationId: String,
    val productCode: String,
    val productName: String,
    val price: Double
)

@Entity(tableName = "station_cache_metadata")
data class StationCacheMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val downloadedAtMillis: Long,
    val sourceUpdatedAtMillis: Long?,
    val sourceUrl: String
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

data class StationListItem(
    val externalId: String,
    val name: String,
    val address: String,
    val municipality: String,
    val province: String,
    val schedule: String?,
    val sourceUpdatedAtMillis: Long?,
    val productCode: String,
    val price: Double,
    val productName: String,
    val hasSelectedFuel: Boolean
)

/** Curated presentation categories. The cache still retains every MITECO product. */
enum class StationFuelType(
    val displayName: String,
    val productCodesByPriority: List<String>
) {
    GASOLINE_95("Gasolina 95", listOf("gasolina_95_e5", "gasolina_95_e10", "gasolina_95_e5_premium")),
    GASOLINE_98("Gasolina 98", listOf("gasolina_98_e5", "gasolina_98_e10")),
    DIESEL("Diésel", listOf("gasoleo_a")),
    ADBLUE("AdBlue", listOf("adblue")),
    GLP("GLP", listOf("gases_licuados_del_petroleo"))
}

data class StationFilter(
    val fuelType: StationFuelType = StationFuelType.GASOLINE_95,
    val province: String? = null,
    val municipality: String? = null,
    val sortByPrice: Boolean = true
)

internal fun mapStationRowsForFuelType(
    rows: List<StationListItem>,
    fuelType: StationFuelType
): List<StationListItem> = rows
    .groupBy { it.externalId }
    .mapNotNull { (_, stationRows) ->
        stationRows.minByOrNull { row ->
            fuelType.productCodesByPriority.indexOf(row.productCode)
        }?.copy(productName = fuelType.displayName)
    }
    .sortedWith(compareBy<StationListItem> { it.price }.thenBy { it.name })
