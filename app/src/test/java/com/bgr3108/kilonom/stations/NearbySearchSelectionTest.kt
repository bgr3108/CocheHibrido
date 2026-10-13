package com.bgr3108.kilonom.stations

import com.bgr3108.kilonom.chargers.ChargerListItem
import com.bgr3108.kilonom.chargers.nearbyChargerSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySearchSelectionTest {
    private val origin = StationCoordinates(40.0, -3.0)

    @Test
    fun twelveValidResultsWithinTenKmChooseTenKmForBothSources() {
        assertSelection(List(12) { 5.0 } + listOf(22.0, 40.0), 10.0, 12)
    }

    @Test
    fun twelveResultsOnlyWithinTwentyFiveKmChooseTwentyFiveKmForBothSources() {
        assertSelection(List(11) { 5.0 } + listOf(22.0, 40.0), 25.0, 12)
    }

    @Test
    fun twelveResultsOnlyWithinFiftyKmChooseFiftyKmForBothSources() {
        assertSelection(List(11) { 22.0 } + listOf(40.0, 55.0), 50.0, 12)
    }

    @Test
    fun sparseResultsStillUseFiftyKmAndExcludeMoreDistantPoints() {
        assertSelection(listOf(5.0, 22.0, 40.0, 55.0), 50.0, 3)
    }

    @Test
    fun invalidCoordinatesDoNotSatisfyTheThreshold() {
        val stations = List(11) { station(it, 5.0) } + station(12, 22.0) + station(13, 5.0).copy(latitude = null)
        val chargers = stations.map(::charger)
        assertEquals(25.0, nearbyStationSelection(stations, origin).radiusKm, 0.0)
        assertEquals(25.0, nearbyChargerSelection(chargers, origin).radiusKm, 0.0)
        assertTrue(nearbyStationSelection(emptyList(), origin).items.isEmpty())
        assertEquals(50.0, nearbyChargerSelection(emptyList(), origin).radiusKm, 0.0)
    }

    private fun assertSelection(distancesKm: List<Double>, radiusKm: Double, count: Int) {
        val stations = distancesKm.mapIndexed(::station)
        val stationResult = nearbyStationSelection(stations, origin)
        val chargerResult = nearbyChargerSelection(stations.map(::charger), origin)
        assertEquals(radiusKm, stationResult.radiusKm, 0.0)
        assertEquals(radiusKm, chargerResult.radiusKm, 0.0)
        assertEquals(count, stationResult.items.size)
        assertEquals(stationResult.items.map { it.externalId }, chargerResult.items.map { it.externalId })
        assertTrue(stationResult.items.all { requireNotNull(it.distanceMeters) <= radiusKm * 1_000 })
    }

    private fun station(id: Int, distanceKm: Double) = StationListItem(
        externalId = id.toString(), name = "Point $id", address = "", municipality = "Madrid", province = "MADRID",
        latitude = origin.latitude + distanceKm / 111.195, longitude = origin.longitude,
        schedule = null, sourceUpdatedAtMillis = null, productCode = "gasolina_95_e5", price = 1.5,
        productName = "Gasolina 95", hasSelectedFuel = true
    )

    private fun charger(item: StationListItem) = ChargerListItem(
        externalId = item.externalId, name = item.name, operatorName = null, address = item.address,
        municipality = item.municipality, province = item.province, schedule = null,
        latitude = item.latitude, longitude = item.longitude, sourceUpdatedAtMillis = null,
        connectorTypes = emptyList(), maxPowerKw = 150.0
    )
}
