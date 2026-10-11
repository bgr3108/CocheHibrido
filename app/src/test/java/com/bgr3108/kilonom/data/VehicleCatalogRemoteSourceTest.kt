package com.bgr3108.kilonom.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class VehicleCatalogRemoteSourceTest {

    @Test
    fun githubRawSource_usesTheFixedPublishDirectoryAndConfiguredLimits() {
        val client = RecordingHttpClient()
        val source = GitHubRawVehicleCatalogRemoteSource(client)

        assertEquals("{}", source.downloadManifest())
        assertArrayEquals(byteArrayOf(1, 2), source.downloadCatalog(INSTALLED_RUNTIME_CATALOG_FILE_NAME))
        assertEquals(
            listOf(
                VEHICLE_CATALOG_MANIFEST_URL to VEHICLE_CATALOG_MANIFEST_MAX_BYTES,
                "${VEHICLE_CATALOG_PUBLISH_BASE_URL}catalog-runtime.json" to VEHICLE_CATALOG_RUNTIME_MAX_BYTES
            ),
            client.requests
        )
        assertThrows(IllegalArgumentException::class.java) { source.downloadCatalog("../other.json") }
    }

    @Test
    fun httpClient_handlesStatusEmptyBodiesLimitsAndIoFailures() {
        val url = URL("https://raw.githubusercontent.com/example/catalog.json")
        assertThrows(VehicleCatalogHttpException::class.java) {
            HttpUrlConnectionVehicleCatalogHttpClient { FakeConnection(url, 404, byteArrayOf(1)) }.get(url, 10)
        }
        assertThrows(IOException::class.java) {
            HttpUrlConnectionVehicleCatalogHttpClient { FakeConnection(url, 200, byteArrayOf()) }.get(url, 10)
        }
        assertThrows(IOException::class.java) {
            HttpUrlConnectionVehicleCatalogHttpClient { FakeConnection(url, 200, byteArrayOf(1), declaredLength = 11) }.get(url, 10)
        }
        assertThrows(IOException::class.java) {
            HttpUrlConnectionVehicleCatalogHttpClient { FakeConnection(url, 200, ByteArray(11), declaredLength = -1) }.get(url, 10)
        }
        assertThrows(IOException::class.java) {
            HttpUrlConnectionVehicleCatalogHttpClient { throw IOException("offline") }.get(url, 10)
        }
    }

    @Test
    fun httpClient_readsAValidBoundedResponseAndDisablesRedirectsAndCaches() {
        val url = URL("https://raw.githubusercontent.com/example/catalog.json")
        val connection = FakeConnection(url, 200, byteArrayOf(1, 2, 3))

        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            HttpUrlConnectionVehicleCatalogHttpClient { connection }.get(url, 10)
        )
        assertEquals("GET", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals("Kilonom", connection.getRequestProperty("User-Agent"))
        assertTrue(connection.disconnected)
    }

    private class RecordingHttpClient : VehicleCatalogHttpClient {
        val requests = mutableListOf<Pair<String, Int>>()

        override fun get(url: URL, maxBytes: Int): ByteArray {
            requests += url.toString() to maxBytes
            return if (url.toString().endsWith("manifest.json")) "{}".toByteArray() else byteArrayOf(1, 2)
        }
    }

    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val bytes: ByteArray,
        private val declaredLength: Long = bytes.size.toLong()
    ) : HttpURLConnection(url) {
        var disconnected = false
            private set

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getContentLengthLong(): Long = declaredLength
        override fun getInputStream() = ByteArrayInputStream(bytes)
    }
}
