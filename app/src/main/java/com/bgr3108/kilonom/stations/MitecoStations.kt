package com.bgr3108.kilonom.stations

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.Normalizer

const val MITECO_STATIONS_URL =
    "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"

data class ParsedStations(
    val stations: List<FuelStationEntity>,
    val prices: List<FuelStationPriceEntity>,
    val sourceUpdatedAtMillis: Long?
)

/** Parses the documented public JSON feed defensively: an invalid row never aborts an import. */
internal object MitecoStationsParser {
    private val sourceDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun parse(payload: String): ParsedStations {
        val root = JSONObject(payload)
        val sourceUpdatedAtMillis = root.optString("Fecha")
            .takeIf(String::isNotBlank)
            ?.let(::parseSourceTimestamp)
        val rows = root.optJSONArray("ListaEESSPrecio")
        val stations = mutableListOf<FuelStationEntity>()
        val prices = mutableListOf<FuelStationPriceEntity>()
        if (rows != null) {
            for (index in 0 until rows.length()) {
                val jsonRow = rows.optJSONObject(index) ?: continue
                val raw = buildMap {
                    jsonRow.keys().forEach { key ->
                        put(key, jsonRow.opt(key)?.toString())
                    }
                }
                val mapped = MitecoStationMapper.map(raw, sourceUpdatedAtMillis) ?: continue
                stations += mapped.station
                prices += mapped.prices
            }
        }
        return ParsedStations(stations, prices, sourceUpdatedAtMillis)
    }

    private fun parseSourceTimestamp(value: String): Long? = runCatching {
        LocalDateTime.parse(value, sourceDateFormatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

internal data class MappedStation(
    val station: FuelStationEntity,
    val prices: List<FuelStationPriceEntity>
)

/** Pure mapping rules, deliberately separate from HTTP/JSON so they can be unit tested. */
internal object MitecoStationMapper {
    fun map(raw: Map<String, String?>, sourceUpdatedAtMillis: Long?): MappedStation? {
        val externalId = raw.value("IDEESS") ?: return null
        val latitude = raw.value("Latitud")?.toSpanishDouble()
        val longitude = raw.value("Longitud (WGS84)")?.toSpanishDouble()
        if (latitude != null && latitude !in -90.0..90.0) return null
        if (longitude != null && longitude !in -180.0..180.0) return null

        val station = FuelStationEntity(
            externalId = externalId,
            name = raw.value("Rótulo") ?: "Estación de servicio",
            address = raw.value("Dirección") ?: "",
            municipality = raw.value("Municipio") ?: raw.value("Localidad") ?: "",
            province = raw.value("Provincia") ?: "",
            postalCode = raw.value("C.P."),
            latitude = latitude,
            longitude = longitude,
            schedule = raw.value("Horario"),
            margin = raw.value("Margen"),
            saleType = raw.value("Tipo Venta"),
            submissionType = raw.value("Remisión"),
            sourceUpdatedAtMillis = sourceUpdatedAtMillis
        )
        val prices = raw.asSequence()
            .filter { (key, _) -> key.startsWith("Precio ") }
            .mapNotNull { (key, value) ->
                value?.toSpanishDouble()?.takeIf { it >= 0.0 }?.let { price ->
                    FuelStationPriceEntity(
                        stationId = externalId,
                        productCode = key.toProductCode(),
                        productName = key.removePrefix("Precio "),
                        price = price
                    )
                }
            }
            .toList()
        return MappedStation(station, prices)
    }

    private fun Map<String, String?>.value(key: String): String? = this[key]
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun String.toSpanishDouble(): Double? = trim()
        .replace('.', '_')
        .replace(',', '.')
        .replace("_", "")
        .toDoubleOrNull()

    private fun String.toProductCode(): String = Normalizer.normalize(
        removePrefix("Precio ").lowercase(),
        Normalizer.Form.NFD
    )
        .replace("\\p{M}+".toRegex(), "")
        .replace("[^a-z0-9]+".toRegex(), "_")
        .trim('_')
}

fun interface StationRemoteDataSource {
    fun download(): ParsedStations
}

class MitecoStationsRemoteDataSource : StationRemoteDataSource {
    override fun download(): ParsedStations {
        val connection = (URL(MITECO_STATIONS_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("MITECO respondió con HTTP ${connection.responseCode}")
            }
            MitecoStationsParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
