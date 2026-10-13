package com.bgr3108.kilonom.chargers

import java.io.InputStreamReader
import java.io.FilterInputStream
import java.io.InputStream
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

    fun parse(reader: Reader, trace: ChargerRefreshDebugTrace? = null): ParsedChargers {
        trace?.parseStarted()
        val records = SemicolonCsvRecords(reader)
        val headers = records.next() ?: return ParsedChargers(emptyList(), emptyList(), null).also {
            trace?.parseCompleted(rows = 0, discardedRows = 0, installations = 0, connectors = 0)
        }
        val columns = ChargerCsvColumns(headers)
        val installations = LinkedHashMap<String, ChargerInstallationEntity>()
        val connectors = LinkedHashMap<String, ChargerConnectorEntity>()
        val parsedDates = HashMap<String, Long?>()
        var latestUpdate: Long? = null
        var rowIndex = 0
        var discardedRows = 0
        while (true) {
            val row = records.next() ?: break
            rowIndex++
            val installationId = columns.value(row, "COD.INSTALACION")
            if (installationId == null) {
                discardedRows++
                continue
            }
            val mapped = MitecoChargerMapper.map(
                values = { key -> columns.value(row, key) },
                rowIndex = rowIndex,
                installationId = installationId,
                createInstallation = installationId !in installations,
                parseDate = { value -> parsedDates.parse(value) }
            )
            if (mapped == null) {
                discardedRows++
                continue
            }
            mapped.installation?.let { installations.putIfAbsent(it.externalId, it) }
            connectors[mapped.connector.externalId] = mapped.connector
            latestUpdate = maxOfNullable(latestUpdate, mapped.sourceUpdatedAtMillis)
        }
        return ParsedChargers(installations.values.toList(), connectors.values.toList(), latestUpdate).also {
            trace?.parseCompleted(rowIndex, discardedRows, it.installations.size, it.connectors.size)
        }
    }

    internal fun parseDate(value: String?): Long? = value?.trim()?.takeIf(String::isNotEmpty)?.let {
        runCatching { LocalDateTime.parse(it, dateFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun MutableMap<String, Long?>.parse(value: String?): Long? {
        if (value == null) return null
        if (containsKey(value)) return this[value]
        return parseDate(value).also { put(value, it) }
    }
}

internal data class MappedCharger(
    val installation: ChargerInstallationEntity?,
    val connector: ChargerConnectorEntity,
    val sourceUpdatedAtMillis: Long?
)

internal object MitecoChargerMapper {
    fun map(raw: Map<String, String>, rowIndex: Int): MappedCharger? {
        val values: (String) -> String? = { key -> raw[key]?.trim()?.takeIf(String::isNotEmpty) }
        val installationId = values("COD.INSTALACION") ?: return null
        return map(values, rowIndex, installationId, createInstallation = true, MitecoChargersParser::parseDate)
    }

    fun map(
        values: (String) -> String?,
        rowIndex: Int,
        installationId: String,
        createInstallation: Boolean,
        parseDate: (String?) -> Long?
    ): MappedCharger? {
        val latitude = values("LATITUD")?.toSpanishNumber()
        val longitude = values("LONGITUD")?.toSpanishNumber()
        if (latitude != null && latitude !in -90.0..90.0) return null
        if (longitude != null && longitude !in -180.0..180.0) return null
        val updatedAt = parseDate(values("FECHA DE ULTIMA MODIFICACION"))
        val installation = if (createInstallation) {
            ChargerInstallationEntity(
                externalId = installationId,
                name = values("NOMBRE INSTALACION") ?: "Punto de recarga",
                operatorName = values("NOMBRE OPERADOR"),
                operatorCode = values("COD.OPERADOR"),
                address = values("DIRECCIÓN") ?: "",
                municipality = values("MUNICIPIO") ?: "",
                province = values("PROVINCIA") ?: "",
                postalCode = values("CODIGO POSTAL"),
                locality = values("LOCALIZACION"),
                schedule = values("HORARIO APERTURA"),
                scheduleType = values("TIPO HORARIO APERTURA"),
                latitude = latitude,
                longitude = longitude,
                sourceUpdatedAtMillis = updatedAt
            )
        } else null
        val pointId = values("ID. PUNTO DE RECARGA")
        val pointCode = values("COD. PUNTO DE RECARGA")
        val sourceConnectorId = values("ID. CONECTOR")
        // RIPREE can number connectors from one again for each EVSE in the same installation,
        // so the connector id must be scoped by the public charging-point identifier too.
        // The deterministic fallback prevents a malformed row without connector id from aborting
        // the rest of the export.
        val pointKey = pointId ?: pointCode
        val connectorId = sourceConnectorId?.let { "$installationId|${pointKey ?: "point"}|$it" }
            ?: listOf(installationId, pointCode ?: pointId ?: "point", values("TIPO CONECTOR") ?: "connector", rowIndex.toString())
                .joinToString("|")
        val connector = ChargerConnectorEntity(
            externalId = connectorId,
            installationId = installationId,
            pointId = pointId,
            pointCode = pointCode,
            connectorType = values("TIPO CONECTOR"),
            chargeType = values("TIPO DE CARGA"),
            connectorFormat = values("FORMATO"),
            maxPowerKw = values("POTENCIA MAXIMA")?.toKw(),
            voltage = values("VOLTAJE")?.toSpanishNumber(),
            amperage = values("INTENSIDAD")?.toSpanishNumber()
        )
        return MappedCharger(installation, connector, updatedAt)
    }

}

/** Resolves normalized RIPREE headers once, avoiding a header-keyed map for every CSV row. */
internal class ChargerCsvColumns(headers: List<String>) {
    private val indices = HashMap<String, Int>(headers.size)

    init {
        headers.forEachIndexed { index, name -> indices[name.trim().uppercase()] = index }
    }

    fun value(row: List<String>, key: String): String? = indices[key]
        ?.let(row::getOrNull)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun maxOfNullable(first: Long?, second: Long?): Long? = when {
    first == null -> second
    second == null -> first
    else -> maxOf(first, second)
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
                check(!inQuotes) { "CSV RIPREE truncado: final de archivo dentro de un campo entrecomillado" }
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
    override fun download(): ParsedChargers = download(trace = null)

    internal fun download(trace: ChargerRefreshDebugTrace?): ParsedChargers {
        trace?.httpStarted()
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
            val statusCode = connection.responseCode
            trace?.responseAvailable(
                statusCode = statusCode,
                contentLength = connection.contentLengthLong,
                contentType = connection.contentType,
                contentEncoding = connection.contentEncoding,
                transferEncoding = connection.getHeaderField("Transfer-Encoding")
            )
            if (statusCode !in 200..299) error("RIPREE respondió con HTTP $statusCode")
            CountingInputStream(connection.inputStream, measureReadDuration = trace != null).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_16LE).buffered().use { reader ->
                    MitecoChargersParser.parse(reader, trace).also {
                        trace?.streamConsumed(input.bytesRead, input.readElapsedMs)
                        input.requireCompleteResponse(
                            expectedContentLength = connection.contentLengthLong,
                            contentEncoding = connection.contentEncoding
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal class CountingInputStream(
    input: InputStream,
    private val elapsedRealtime: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val measureReadDuration: Boolean = true
) : FilterInputStream(input) {
    var bytesRead: Long = 0
        private set
    var readElapsedMs: Long = 0
        private set

    override fun read(): Int {
        if (!measureReadDuration) return super.read().also { if (it != -1) bytesRead++ }
        val startedAt = elapsedRealtime()
        return super.read().also {
            readElapsedMs += elapsedRealtime() - startedAt
            if (it != -1) bytesRead++
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!measureReadDuration) return super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
        val startedAt = elapsedRealtime()
        return super.read(buffer, offset, length).also {
            readElapsedMs += elapsedRealtime() - startedAt
            if (it > 0) bytesRead += it
        }
    }

    override fun skip(byteCount: Long): Long = super.skip(byteCount).also { bytesRead += it }

    /**
     * A declared length can only be compared to bytes delivered to this stream when the response
     * is unencoded. Transparent gzip handling intentionally clears Content-Length on Android; a
     * non-identity encoding must not be compared to decoded bytes.
     */
    fun requireCompleteResponse(expectedContentLength: Long, contentEncoding: String?) {
        if (expectedContentLength < 0 || !contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", ignoreCase = true)) {
            return
        }
        check(bytesRead == expectedContentLength) {
            "Descarga RIPREE incompleta: se recibieron $bytesRead de $expectedContentLength bytes"
        }
    }
}
