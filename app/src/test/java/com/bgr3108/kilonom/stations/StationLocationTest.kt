package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationLocationTest {
    @Test
    fun navigationRequest_isPlainGeoViewIntentWithoutTaskOrPackageFlags() {
        val request = stationNavigationRequest(station("canarias", 28.1235, -15.4363, 1.40))

        assertEquals("android.intent.action.VIEW", request?.action)
        assertTrue(request?.uri.orEmpty().startsWith("geo:"))
        assertEquals(0, request?.flags)
        assertNull(request?.packageName)
    }

    @Test
    fun haversine_returnsZeroForTheSamePoint() {
        val madrid = StationCoordinates(40.4168, -3.7038)

        assertEquals(0.0, madrid.distanceToMeters(madrid), 0.01)
    }

    @Test
    fun haversine_handlesCanaryCoordinatesWithNegativeLongitude() {
        val lasPalmas = StationCoordinates(28.1235, -15.4363)
        val santaCruz = StationCoordinates(28.4636, -16.2518)

        assertEquals(88_000.0, lasPalmas.distanceToMeters(santaCruz), 8_000.0)
    }

    @Test
    fun distanceSorting_usesOptionalInMemoryLocationAndKeepsMissingCoordinatesLast() {
        val origin = StationCoordinates(28.1235, -15.4363)
        val ordered = withDistancesAndSort(
            listOf(
                station("far", 28.4636, -16.2518, 1.40),
                station("near", 28.1240, -15.4370, 1.50),
                station("no-coordinates", null, null, 1.30)
            ),
            origin,
            StationSortOrder.DISTANCE
        )

        assertEquals(listOf("near", "far", "no-coordinates"), ordered.map { it.externalId })
        assertNotNull(ordered.first().distanceMeters)
        assertNull(ordered.last().distanceMeters)
    }

    @Test
    fun noLocation_keepsDistancesAbsentAndPriceOrdering() {
        val ordered = withDistancesAndSort(
            listOf(station("expensive", 40.4, -3.7, 1.70), station("cheap", 40.4, -3.7, 1.40)),
            origin = null,
            sortOrder = StationSortOrder.PRICE
        )

        assertEquals(listOf("cheap", "expensive"), ordered.map { it.externalId })
        assertTrue(ordered.all { it.distanceMeters == null })
    }

    @Test
    fun mapMarkers_keepTheAlreadyFilteredStationsAndExcludeOnlyInvalidCoordinates() {
        val selectedFuelResults = listOf(
            station("visible", 28.1235, -15.4363, 1.40),
            station("without-coordinates", null, null, 1.50)
        )

        assertEquals(listOf("visible"), stationsForMap(selectedFuelResults).map { it.externalId })
    }

    @Test
    fun onlyValidCoordinatesCreateANavigationIntent() {
        val uri = stationNavigationUri(station("canarias", 28.1235, -15.4363, 1.40))

        assertTrue(uri.orEmpty().startsWith("geo:28.1235,-15.4363?q="))
        assertNull(stationNavigationUri(station("without-coordinates", null, null, 1.40)))
        assertFalse(StationCoordinates(91.0, -15.0).isValid())
    }

    private fun station(id: String, latitude: Double?, longitude: Double?, price: Double) = StationListItem(
        externalId = id,
        name = "Estación $id",
        address = "Dirección",
        municipality = "Municipio",
        province = "Provincia",
        latitude = latitude,
        longitude = longitude,
        schedule = null,
        sourceUpdatedAtMillis = null,
        productCode = "gasolina_95_e5",
        price = price,
        productName = "Gasolina 95",
        hasSelectedFuel = true
    )
}
