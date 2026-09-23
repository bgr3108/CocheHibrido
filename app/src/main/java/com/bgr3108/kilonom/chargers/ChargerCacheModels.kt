package com.bgr3108.kilonom.chargers

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.stations.distanceToMeters
import com.bgr3108.kilonom.stations.NEARBY_MINIMUM_RESULTS
import com.bgr3108.kilonom.stations.NEARBY_SEARCH_RADII_KM

/** Public RIPREE installation data. This cache is separate from Kilonom's personal database. */
@Entity(
    tableName = "charger_installations",
    indices = [
        Index("province"),
        Index("municipality"),
        Index("operatorName"),
        Index("maxPowerKw"),
        Index(value = ["province", "municipality"]),
        Index(value = ["latitude", "longitude"])
    ]
)
data class ChargerInstallationEntity(
    @PrimaryKey val externalId: String,
    val name: String,
    val operatorName: String?,
    val operatorCode: String?,
    val address: String,
    val municipality: String,
    val province: String,
    val postalCode: String?,
    val locality: String?,
    val schedule: String?,
    val scheduleType: String?,
    val latitude: Double?,
    val longitude: Double?,
    val sourceUpdatedAtMillis: Long?,
    /** Public-cache summaries built once during import, so lists never aggregate every connector. */
    val maxPowerKw: Double? = null,
    val connectorTypesEncoded: String = ""
)

/** A CSV row represents one connector associated with an installation / charging point. */
@Entity(
    tableName = "charger_connectors",
    foreignKeys = [
        ForeignKey(
            entity = ChargerInstallationEntity::class,
            parentColumns = ["externalId"],
            childColumns = ["installationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("installationId"), Index("connectorType"), Index(value = ["installationId", "connectorType"])]
)
data class ChargerConnectorEntity(
    @PrimaryKey val externalId: String,
    val installationId: String,
    val pointId: String?,
    val pointCode: String?,
    val connectorType: String?,
    val chargeType: String?,
    val connectorFormat: String?,
    val maxPowerKw: Double?,
    val voltage: Double?,
    val amperage: Double?
)

@Entity(tableName = "charger_cache_metadata")
data class ChargerCacheMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val downloadedAtMillis: Long,
    val sourceUpdatedAtMillis: Long?,
    val sourceUrl: String
) {
    companion object { const val SINGLETON_ID = 1 }
}

/** Lightweight connector projection used to assemble one UI item per installation. */
data class ChargerConnectorSummary(
    val installationId: String,
    val connectorType: String?,
    val maxPowerKw: Double?
)

data class ChargerListItem(
    val externalId: String,
    val name: String,
    val operatorName: String?,
    val address: String,
    val municipality: String,
    val province: String,
    val schedule: String?,
    val latitude: Double?,
    val longitude: Double?,
    val sourceUpdatedAtMillis: Long?,
    /** Original values from RIPREE, retained for precise future presentation. */
    val connectorTypes: List<String>,
    val maxPowerKw: Double?,
    val distanceMeters: Double? = null,
    /** RIPREE's CSV currently exposes no official operator URL. */
    val operatorUrl: String? = null
)

/** SQL projection for a paged installation. Connector details stay in the cache, not in each row. */
data class ChargerListRow(
    val externalId: String,
    val name: String,
    val operatorName: String?,
    val address: String,
    val municipality: String,
    val province: String,
    val schedule: String?,
    val latitude: Double?,
    val longitude: Double?,
    val sourceUpdatedAtMillis: Long?,
    val maxPowerKw: Double?,
    val connectorTypesEncoded: String
)

internal fun ChargerListRow.toListItem(): ChargerListItem = ChargerListItem(
    externalId = externalId,
    name = name,
    operatorName = operatorName,
    address = address,
    municipality = municipality,
    province = province,
    schedule = schedule,
    latitude = latitude,
    longitude = longitude,
    sourceUpdatedAtMillis = sourceUpdatedAtMillis,
    connectorTypes = connectorTypesEncoded.split(CHARGER_CONNECTOR_SEPARATOR).filter(String::isNotBlank),
    maxPowerKw = maxPowerKw
)

internal const val CHARGER_CONNECTOR_SEPARATOR = "\u001F"

/** Presentation groups map only the documented connector values found in the RIPREE export. */
enum class ChargerConnectorType(val displayName: String, val sourceValues: Set<String>) {
    CCS_COMBO_2("CCS / Combo 2", setOf("IEC_62196_T2_COMBO")),
    TYPE_2("Tipo 2", setOf("IEC_62196_T2")),
    CHADEMO("CHAdeMO", setOf("CHADEMO")),
    DOMESTIC("Enchufe doméstico", setOf("DOMESTIC_E", "DOMESTIC_F")),
    TESLA("Tesla", setOf("TeslaConnectorEurope"));

    fun matches(values: Collection<String>): Boolean = values.any { it in sourceValues }
}

/** Keeps RIPREE's original codes in cache while exposing clear, verified names in the UI. */
internal fun List<String>.toPresentationConnectorNames(): List<String> = map { raw ->
    ChargerConnectorType.entries.firstOrNull { raw in it.sourceValues }?.displayName ?: raw
}.distinct()

enum class ChargerSortOrder(val displayName: String) {
    DISTANCE("Más cercanos"),
    POWER("Mayor potencia"),
    NAME("Nombre / operador")
}

/** The two independent public-data sources shown within the Estaciones destination. */
enum class StationsContentType {
    FUEL,
    CHARGERS
}

data class ChargerFilter(
    val connectorType: ChargerConnectorType? = null,
    val minimumPowerKw: Double? = null,
    val operatorName: String? = null,
    val province: String? = null,
    val municipality: String? = null,
    val sortOrder: ChargerSortOrder = ChargerSortOrder.POWER
)

internal fun normalizeChargerFilterForScope(
    newFilter: ChargerFilter,
    hadNearbyLocation: Boolean,
    hasLocation: Boolean
): ChargerFilter = when {
    newFilter.sortOrder == ChargerSortOrder.DISTANCE && !hasLocation -> newFilter.copy(sortOrder = ChargerSortOrder.POWER)
    hadNearbyLocation && newFilter.province != null && newFilter.sortOrder == ChargerSortOrder.DISTANCE -> newFilter.copy(sortOrder = ChargerSortOrder.POWER)
    else -> newFilter
}

internal fun ChargerFilter.searchScope(location: StationCoordinates?): StationSearchScope = when {
    province != null -> StationSearchScope.ManualZone(province, municipality)
    location?.isValid() == true -> StationSearchScope.Nearby(location)
    else -> StationSearchScope.None
}

internal fun ChargerFilter.isDefault(): Boolean = this == ChargerFilter()

internal fun ChargerFilter.summary(): String = buildList {
    add(connectorType?.displayName ?: "Todos los conectores")
    minimumPowerKw?.let { add("≥ ${it.formatPower()} kW") }
    province?.let(::add)
    municipality?.let(::add)
    operatorName?.let(::add)
    if (sortOrder != ChargerSortOrder.POWER) add(sortOrder.displayName)
}.joinToString(" · ")

fun ChargerListItem.coordinatesOrNull(): StationCoordinates? =
    latitude?.let { lat -> longitude?.let { lon -> StationCoordinates(lat, lon) } }?.takeIf { it.isValid() }

internal fun chargersForMap(chargers: List<ChargerListItem>): List<ChargerListItem> =
    chargers.asSequence()
        .filter { it.coordinatesOrNull() != null }
        // A map point represents an installation, never one of its connector rows.
        .distinctBy(ChargerListItem::externalId)
        .toList()

internal fun filterAndSortChargers(
    chargers: List<ChargerListItem>,
    filter: ChargerFilter,
    origin: StationCoordinates?
): List<ChargerListItem> = chargers.asSequence()
    .filter { filter.connectorType?.matches(it.connectorTypes) != false }
    .filter { filter.minimumPowerKw == null || (it.maxPowerKw ?: Double.NEGATIVE_INFINITY) >= filter.minimumPowerKw }
    .filter { filter.operatorName == null || it.operatorName == filter.operatorName }
    .filter { filter.province == null || it.province == filter.province }
    .filter { filter.municipality == null || it.municipality == filter.municipality }
    .map { charger ->
        charger.copy(distanceMeters = origin?.takeIf { it.isValid() }?.let { start ->
            charger.coordinatesOrNull()?.let(start::distanceToMeters)
        })
    }
    .sortedWith(
        when (filter.sortOrder) {
            ChargerSortOrder.DISTANCE -> compareBy<ChargerListItem> { it.distanceMeters == null }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }.thenBy { it.name }
            ChargerSortOrder.POWER -> compareByDescending<ChargerListItem> { it.maxPowerKw ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.name }
            ChargerSortOrder.NAME -> compareBy<ChargerListItem> { it.operatorName ?: it.name }.thenBy { it.name }
        }
    )
    .toList()

internal fun nearbyChargers(
    chargers: List<ChargerListItem>,
    origin: StationCoordinates
): List<ChargerListItem> {
    val withDistance = chargers.mapNotNull { charger ->
        charger.coordinatesOrNull()?.let { point -> charger.copy(distanceMeters = origin.distanceToMeters(point)) }
    }
    val countsByRadius = NEARBY_SEARCH_RADII_KM.associateWith { radius ->
        withDistance.count { (it.distanceMeters ?: Double.MAX_VALUE) <= radius * 1_000 }
    }
    val selectedRadiusKm = NEARBY_SEARCH_RADII_KM.firstOrNull { radius ->
        (countsByRadius[radius] ?: 0) >= NEARBY_MINIMUM_RESULTS
    } ?: NEARBY_SEARCH_RADII_KM.last()
    return withDistance.asSequence()
        .filter { (it.distanceMeters ?: Double.MAX_VALUE) <= selectedRadiusKm * 1_000 }
        .sortedWith(compareBy<ChargerListItem> { it.distanceMeters }.thenBy { it.name })
        .toList()
}

internal fun Double.formatPower(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f", this)
