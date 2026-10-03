package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.stations.StationListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMapTest {

    @Test
    fun preparation_buildsMinimalGeoJsonAndStableLookupForValidStations() {
        val item = station("station-a", latitude = 28.1, longitude = -15.4)

        val preparation = prepareStationMap(listOf(item, station("missing", null, null)))
            as StationMapPreparation.Ready

        assertEquals(1, preparation.geoJson.featureCount())
        assertTrue(preparation.geoJson.contains("\"station_id\":\"station-a\""))
        assertFalse(preparation.geoJson.contains("\"schedule\""))
        assertEquals(item, preparation.stationsById["station-a"])
    }

    @Test
    fun preparationFailure_returnsSafeStateInsteadOfThrowingIntoCompose() {
        val preparation = prepareStationMap(listOf(station("station-a", 28.1, -15.4))) {
            error("Synthetic serialization failure")
        }

        assertEquals(StationMapPreparation.Failed, preparation)
    }

    private fun station(id: String, latitude: Double?, longitude: Double?) = StationListItem(
        externalId = id,
        name = "Station $id",
        address = "Address",
        municipality = "Municipality",
        province = "Province",
        latitude = latitude,
        longitude = longitude,
        schedule = null,
        sourceUpdatedAtMillis = null,
        productCode = "gasolina_95_e5",
        price = 1.5,
        productName = "Gasolina 95",
        hasSelectedFuel = true
    )

    private fun String.featureCount(): Int = "\"type\":\"Feature\"".toRegex().findAll(this).count()
}
