package com.bgr3108.kilonom.chargers

import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val MITECO_CHARGERS_URL = "https://energia.serviciosmin.gob.es/Ripree/ExportarInstalaciones/Export"
private const val MITECO_CHARGERS_EXPORT_URL = "https://energia.serviciosmin.gob.es/Ripree/ExportarInstalaciones/GenerarExcel"

data class ParsedChargers(
    val installations: List<ChargerInstallationEntity>,
    val connectors: List<ChargerConnectorEntity>,
    val sourceUpdatedAtMillis: Long?
)

/**
 * RIPREE exports UTF-16LE, semicolon-delimited CSV. Its text fields are quoted and may contain
 * delimiters, so this parser reads CSV records character by character instead of splitting lines.
 */
internal object MitecoChargersParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun parse(reader: Reader): ParsedChargers {
        val records = SemicolonCsvRecords(reader)
        val headers = records.next() ?: return ParsedChargers(emptyList(), emptyList(), null)
        val headerIndex = headers.mapIndexed { index, name -> name.trim().uppercase() to index }.toMap()
        val installations = LinkedHashMap<String, ChargerInstallationEntity>()
        val connectors = LinkedHashMap<String, ChargerConnectorEntity>()
        var latestUpdate: Long? = null
        var rowIndex = 0
        while (true) {
            val row = records.next() ?: break
            rowIndex++
            val raw = headerIndex.mapValues { (_, index) -> row.getOrNull(index)?.trim().orEmpty() }
            val mapped = MitecoChargerMapper.map(raw, rowIndex) ?: continue
            installations.putIfAbsent(mapped.installation.externalId, mapped.installation)
            connectors[mapped.connector.externalId] = mapped.connector
            latestUpdate = listOfNotNull(latestUpdate, mapped.installation.sourceUpdatedAtMillis).maxOrNull()
        }
        return ParsedChargers(installations.values.toList(), connectors.values.toList(), latestUpdate)
    }

    internal fun parseDate(value: String?): Long? = value?.trim()?.takeIf(String::isNotEmpty)?.let {
        runCatching { LocalDateTime.parse(it, dateFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }
}

internal data class MappedCharger(
    val installation: ChargerInstallationEntity,
    val connector: ChargerConnectorEntity
)

internal object MitecoChargerMapper {
    fun map(raw: Map<String, String>, rowIndex: Int): MappedCharger? {
        val installationId = raw.value("COD.INSTALACION") ?: return null
        val latitude = raw.value("LATITUD")?.toSpanishNumber()
        val longitude = raw.value("LONGITUD")?.toSpanishNumber()
        if (latitude != null && latitude !in -90.0..90.0) return null
        if (longitude != null && longitude !in -180.0..180.0) return null
        val updatedAt = MitecoChargersParser.parseDate(raw.value("FECHA DE ULTIMA MODIFICACION"))
        val installation = ChargerInstallationEntity(
            externalId = installationId,
            name = raw.value("NOMBRE INSTALACION") ?: "Punto de recarga",
            operatorName = raw.value("NOMBRE OPERADOR"),
            operatorCode = raw.value("COD.OPERADOR"),
            address = raw.value("DIRECCIÓN") ?: "",
            municipality = raw.value("MUNICIPIO") ?: "",
            province = raw.value("PROVINCIA") ?: "",
            postalCode = raw.value("CODIGO POSTAL"),
            locality = raw.value("LOCALIZACION"),
            schedule = raw.value("HORARIO APERTURA"),
            scheduleType = raw.value("TIPO HORARIO APERTURA"),
            latitude = latitude,
            longitude = longitude,
            sourceUpdatedAtMillis = updatedAt
        )
        val pointId = raw.value("ID. PUNTO DE RECARGA")
        val pointCode = raw.value("COD. PUNTO DE RECARGA")
        val sourceConnectorId = raw.value("ID. CONECTOR")
        // RIPREE can number connectors from one again for each EVSE in the same installation,
        // so the connector id must be scoped by the public charging-point identifier too.
        // The deterministic fallback prevents a malformed row without connector id from aborting
        // the rest of the export.
        val pointKey = pointId ?: pointCode
        val connectorId = sourceConnectorId?.let { "$installationId|${pointKey ?: "point"}|$it" }
            ?: listOf(installationId, pointCode ?: pointId ?: "point", raw.value("TIPO CONECTOR") ?: "connector", rowIndex.toString())
                .joinToString("|")
        val connector = ChargerConnectorEntity(
            externalId = connectorId,
            installationId = installationId,
            pointId = pointId,
            pointCode = pointCode,
            connectorType = raw.value("TIPO CONECTOR"),
            chargeType = raw.value("TIPO DE CARGA"),
            connectorFormat = raw.value("FORMATO"),
            maxPowerKw = raw.value("POTENCIA MAXIMA")?.toKw(),
            voltage = raw.value("VOLTAJE")?.toSpanishNumber(),
            amperage = raw.value("INTENSIDAD")?.toSpanishNumber()
        )
        return MappedCharger(installation, connector)
    }

    private fun Map<String, String>.value(key: String): String? = this[key]?.trim()?.takeIf(String::isNotEmpty)
}

internal fun String.toSpanishNumber(): Double? {
    val cleaned = trim()
        .replace("=", "")
        .replace("kW", "", ignoreCase = true)
        .replace("V", "", ignoreCase = true)
        .replace("A", "", ignoreCase = true)
        .trim()
    // RIPREE uses comma for power values but its exported coordinates are decimal points.
    // Retain a dot-only decimal and only treat dots as grouping separators when a comma exists.
    val normalized = if (cleaned.contains(',')) cleaned.replace(".", "").replace(',', '.') else cleaned
    return normalized.toDoubleOrNull()
}

internal fun String.toKw(): Double? = toSpanishNumber()?.takeIf { it >= 0.0 }

/** Minimal RFC-4180-style reader for the actual RIPREE delimiter and quoted values. */
internal class SemicolonCsvRecords(reader: Reader) {
    private val reader = PushbackReader(reader, 1)

    fun next(): List<String>? {
        val values = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var readAny = false
        while (true) {
            val next = reader.read()
            if (next == -1) {
                if (!readAny) return null
                values += field.toString()
                return values
            }
            readAny = true
            when (val char = next.toChar()) {
                '"' -> {
                    if (inQuotes) {
                        val following = reader.read()
                        if (following == '"'.code) field.append('"') else {
                            inQuotes = false
                            if (following != -1) reader.unread(following)
                        }
                    // RIPREE exports some values as Excel-style formulas (="…"). A quote
                    // only starts a quoted value at a field boundary (or straight after that
                    // initial equals); a literal quote elsewhere must not swallow later rows.
                    } else if (field.isEmpty() || field.toString() == "=") inQuotes = true else field.append(char)
                }
                ';' -> if (inQuotes) field.append(char) else {
                    values += field.toString()
                    field.clear()
                }
                '\n' -> if (inQuotes) field.append(char) else {
                    values += field.toString().removeSuffix("\r")
                    return values
                }
                else -> field.append(char)
            }
        }
    }
}

fun interface ChargerRemoteDataSource { fun download(): ParsedChargers }

class MitecoChargersRemoteDataSource : ChargerRemoteDataSource {
    override fun download(): ParsedChargers {
        val connection = (URL(MITECO_CHARGERS_EXPORT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 120_000
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/csv, application/octet-stream")
        }
        return try {
            connection.outputStream.use { it.write("{\"model\":null,\"soloConsolidado\":true}".toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) error("RIPREE respondió con HTTP ${connection.responseCode}")
            InputStreamReader(connection.inputStream, StandardCharsets.UTF_16LE).buffered().use(MitecoChargersParser::parse)
        } finally {
            connection.disconnect()
        }
    }
}
