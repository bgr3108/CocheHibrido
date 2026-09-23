package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchScopeTest {
    private val location = StationCoordinates(28.123, -15.432)

    @Test
    fun noLocationAndNoProvince_hasNoNationalSearchScope() {
        assertEquals(StationSearchScope.None, StationFilter().searchScope(null))
    }

    @Test
    fun locationCreatesNearbyScope_andManualProvinceOverridesIt() {
        assertEquals(StationSearchScope.Nearby(location), StationFilter().searchScope(location))
        assertEquals(
            StationSearchScope.ManualZone("LAS PALMAS", "TELDE"),
            StationFilter(province = "LAS PALMAS", municipality = "TELDE").searchScope(location)
        )
    }

    @Test
    fun nearbySelectionUsesOnlyTheSmallestUsefulAdaptiveRadius() {
        val origin = StationCoordinates(28.0, -15.0)
        val stations = (1..12).map { index -> station(index, 28.0 + index * 0.005) } +
            station(99, 28.7)

        val result = nearbyStations(stations, origin)

        assertEquals(12, result.size)
        assertTrue(result.all { (it.distanceMeters ?: Double.MAX_VALUE) <= 10_000 })
    }

    @Test
    fun manualZoneAfterNearbyRestoresTheManualPriceDefault() {
        val result = normalizeStationFilterForScope(
            StationFilter(province = "PALMAS (LAS)", sortOrder = StationSortOrder.DISTANCE),
            hadNearbyLocation = true,
            hasLocation = true
        )

        assertEquals(StationSortOrder.PRICE, result.sortOrder)
    }

    private fun station(id: Int, latitude: Double) = StationListItem(
        externalId = id.toString(), name = "Estación $id", address = "", municipality = "", province = "",
        latitude = latitude, longitude = -15.0, schedule = null, sourceUpdatedAtMillis = null,
        productCode = "gasolina_95_e5", price = 1.5, productName = "Gasolina 95", hasSelectedFuel = true
    )
}
