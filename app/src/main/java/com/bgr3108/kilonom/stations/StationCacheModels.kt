package com.bgr3108.kilonom.stations

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_stations",
    indices = [Index("province"), Index("municipality"), Index(value = ["latitude", "longitude"])]
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
    indices = [
        Index("stationId"),
        Index("productCode"),
        Index(value = ["visibleFuelType", "visibleFuelPriority", "price"])
    ]
)
data class FuelStationPriceEntity(
    val stationId: String,
    val productCode: String,
    val productName: String,
    val price: Double,
    /** Derived only for Kilonom's five visible filters; every original MITECO product remains stored. */
    val visibleFuelType: String? = null,
    val visibleFuelPriority: Int? = null
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
    val latitude: Double?,
    val longitude: Double?,
    val schedule: String?,
    val sourceUpdatedAtMillis: Long?,
    val productCode: String,
    val price: Double,
    val productName: String,
    val hasSelectedFuel: Boolean,
    /** Ephemeral value calculated from the optional, non-persisted current location. */
    val distanceMeters: Double? = null
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

    ;

    companion object {
        fun forProductCode(productCode: String): Pair<StationFuelType, Int>? =
            entries.firstNotNullOfOrNull { type ->
                type.productCodesByPriority.indexOf(productCode)
                    .takeIf { it >= 0 }
                    ?.let { type to it }
            }
    }
}

data class StationFilter(
    val fuelType: StationFuelType = StationFuelType.GASOLINE_95,
    val province: String? = null,
    val municipality: String? = null,
    val sortOrder: StationSortOrder = StationSortOrder.PRICE
)

/**
 * One shared geographic scope drives both the list and the map.  A manual province always wins
 * over a remembered position, so changing from "Cerca de mí" to a zone is predictable.
 */
sealed interface StationSearchScope {
    data object None : StationSearchScope
    data class Nearby(val origin: StationCoordinates) : StationSearchScope
    data class ManualZone(val province: String, val municipality: String?) : StationSearchScope
}

internal fun StationFilter.searchScope(location: StationCoordinates?): StationSearchScope = when {
    province != null -> StationSearchScope.ManualZone(province, municipality)
    location?.isValid() == true -> StationSearchScope.Nearby(location)
    else -> StationSearchScope.None
}

/** The current presentation of Estaciones; it is kept in the screen state, not persisted. */
enum class StationsViewMode { LIST, MAP }

internal fun StationFilter.isDefault(): Boolean = this == StationFilter()

internal fun StationFilter.summary(): String = buildList {
    add(fuelType.displayName)
    add(province ?: "Todas las provincias")
    municipality?.let(::add)
    if (sortOrder == StationSortOrder.DISTANCE) add(sortOrder.displayName)
}.joinToString(" · ")

enum class StationSortOrder(val displayName: String) {
    PRICE("Más baratas"),
    DISTANCE("Más cercanas")
}

internal fun canSortStationsByDistance(location: StationCoordinates?): Boolean = location?.isValid() == true

/** A manual zone replaces nearby mode, so its first list is price-oriented rather than remote. */
internal fun normalizeStationFilterForScope(
    newFilter: StationFilter,
    hadNearbyLocation: Boolean,
    hasLocation: Boolean
): StationFilter = when {
    newFilter.sortOrder == StationSortOrder.DISTANCE && !hasLocation -> newFilter.copy(sortOrder = StationSortOrder.PRICE)
    hadNearbyLocation && newFilter.province != null && newFilter.sortOrder == StationSortOrder.DISTANCE -> newFilter.copy(sortOrder = StationSortOrder.PRICE)
    else -> newFilter
}

/** A point used only in memory while Estaciones is open; it is never written to Room. */
data class StationCoordinates(val latitude: Double, val longitude: Double) {
    fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/** Bounding box for a small local query; Haversine is applied afterwards for exact ordering. */
data class StationBoundingBox(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)

internal fun StationCoordinates.boundingBox(radiusKm: Double): StationBoundingBox {
    val latitudeDelta = radiusKm / 110.574
    val longitudeDelta = radiusKm / (111.320 * Math.cos(Math.toRadians(latitude))).coerceAtLeast(0.01)
    return StationBoundingBox(
        minLatitude = (latitude - latitudeDelta).coerceAtLeast(-90.0),
        maxLatitude = (latitude + latitudeDelta).coerceAtMost(90.0),
        minLongitude = (longitude - longitudeDelta).coerceAtLeast(-180.0),
        maxLongitude = (longitude + longitudeDelta).coerceAtMost(180.0)
    )
}

internal const val NEARBY_MINIMUM_RESULTS = 12
internal val NEARBY_SEARCH_RADII_KM = listOf(10.0, 25.0, 50.0)

fun StationListItem.coordinatesOrNull(): StationCoordinates? =
    latitude?.let { lat -> longitude?.let { lon -> StationCoordinates(lat, lon) } }?.takeIf { it.isValid() }

/** The map receives the same already-filtered list as the screen, excluding only invalid map points. */
internal fun stationsForMap(stations: List<StationListItem>): List<StationListItem> =
    stations.filter { it.coordinatesOrNull() != null }

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

internal fun withDistancesAndSort(
    stations: List<StationListItem>,
    origin: StationCoordinates?,
    sortOrder: StationSortOrder
): List<StationListItem> {
    val withDistances = stations.map { station ->
        station.copy(distanceMeters = origin?.takeIf { it.isValid() }?.let { station.coordinatesOrNull()?.let(it::distanceToMeters) })
    }
    return when (sortOrder) {
        StationSortOrder.PRICE -> withDistances.sortedWith(compareBy<StationListItem> { it.price }.thenBy { it.name })
        StationSortOrder.DISTANCE -> withDistances.sortedWith(
            compareBy<StationListItem> { it.distanceMeters == null }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                .thenBy { it.price }
                .thenBy { it.name }
        )
    }
}

/**
 * Expands only as far as necessary for a useful local result. Queries already use the largest
 * bounding box, so no national cache is ever materialised to calculate nearby distances.
 */
internal fun nearbyStations(
    stations: List<StationListItem>,
    origin: StationCoordinates
): List<StationListItem> {
    val withDistance = stations.mapNotNull { station ->
        station.coordinatesOrNull()?.let { point -> station.copy(distanceMeters = origin.distanceToMeters(point)) }
    }
    val selectedRadiusKm = NEARBY_SEARCH_RADII_KM.firstOrNull { radius ->
        withDistance.count { (it.distanceMeters ?: Double.MAX_VALUE) <= radius * 1_000 } >= NEARBY_MINIMUM_RESULTS
    } ?: NEARBY_SEARCH_RADII_KM.last()
    return withDistance.asSequence()
        .filter { (it.distanceMeters ?: Double.MAX_VALUE) <= selectedRadiusKm * 1_000 }
        .sortedWith(compareBy<StationListItem> { it.distanceMeters }.thenBy { it.price }.thenBy { it.name })
        .toList()
}

/** Great-circle distance. Suitable for the approximate distances shown in the station list. */
fun StationCoordinates.distanceToMeters(destination: StationCoordinates): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(destination.latitude - latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - longitude)
    val latitudeStart = Math.toRadians(latitude)
    val latitudeEnd = Math.toRadians(destination.latitude)
    val a = Math.sin(latitudeDelta / 2).let { it * it } +
        Math.cos(latitudeStart) * Math.cos(latitudeEnd) * Math.sin(longitudeDelta / 2).let { it * it }
    return earthRadiusMeters * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}
