package com.bgr3108.kilonom.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal const val VEHICLE_CATALOG_PUBLISH_BASE_URL =
    "https://raw.githubusercontent.com/bgr3108/CocheHibrido/master/catalog/publish/"
internal const val VEHICLE_CATALOG_MANIFEST_URL = "${VEHICLE_CATALOG_PUBLISH_BASE_URL}manifest.json"
internal const val VEHICLE_CATALOG_MANIFEST_MAX_BYTES = 64 * 1024
internal const val VEHICLE_CATALOG_RUNTIME_MAX_BYTES = 5 * 1024 * 1024

/** Network-only boundary. It neither validates nor installs catalog artifacts. */
internal interface VehicleCatalogRemoteSource {
    fun downloadManifest(): String
    fun downloadCatalog(fileName: String): ByteArray
}

/** HTTPS-only GitHub RAW source for the fixed publish directory of the configured origin. */
internal class GitHubRawVehicleCatalogRemoteSource(
    private val httpClient: VehicleCatalogHttpClient = HttpUrlConnectionVehicleCatalogHttpClient()
) : VehicleCatalogRemoteSource {

    override fun downloadManifest(): String = httpClient.get(
        url = URL(VEHICLE_CATALOG_MANIFEST_URL),
        maxBytes = VEHICLE_CATALOG_MANIFEST_MAX_BYTES
    ).toString(Charsets.UTF_8)

    override fun downloadCatalog(fileName: String): ByteArray {
        require(fileName == INSTALLED_RUNTIME_CATALOG_FILE_NAME) {
            "Unexpected remote catalog file: $fileName"
        }
        return httpClient.get(
            url = URL("$VEHICLE_CATALOG_PUBLISH_BASE_URL$fileName"),
            maxBytes = VEHICLE_CATALOG_RUNTIME_MAX_BYTES
        )
    }
}

/** Small injectable HTTP boundary so the updater and HTTP behavior need no real-network tests. */
internal interface VehicleCatalogHttpClient {
    fun get(url: URL, maxBytes: Int): ByteArray
}

internal class HttpUrlConnectionVehicleCatalogHttpClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    }
) : VehicleCatalogHttpClient {

    override fun get(url: URL, maxBytes: Int): ByteArray {
        require(url.protocol == "https") { "Vehicle catalog requires HTTPS" }
        require(maxBytes > 0) { "maxBytes must be positive" }

        val connection = connectionFactory(url).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "Kilonom")
        }
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw VehicleCatalogHttpException(status)
            val contentLength = connection.contentLengthLong
            if (contentLength > maxBytes) {
                throw IOException("Vehicle catalog response exceeds $maxBytes bytes")
            }
            connection.inputStream.use { input -> input.readBoundedBytes(maxBytes) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
    }
}

internal class VehicleCatalogHttpException(statusCode: Int) : IOException(
    "Vehicle catalog server responded with HTTP $statusCode"
)

private fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        totalBytes += read
        if (totalBytes > maxBytes) throw IOException("Vehicle catalog response exceeds $maxBytes bytes")
        output.write(buffer, 0, read)
    }
    if (totalBytes == 0) throw IOException("Vehicle catalog response is empty")
    return output.toByteArray()
}
