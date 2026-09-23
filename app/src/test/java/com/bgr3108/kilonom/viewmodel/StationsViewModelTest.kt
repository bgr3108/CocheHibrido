package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationSearchScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsViewModelTest {
    @Test
    fun chargerPipeline_isInactiveWhileFuelStationsAreActive() {
        assertFalse(chargerPipelineIsActive(StationsContentType.FUEL))
    }

    @Test
    fun chargerPipeline_isActivatedOnlyForChargers() {
        assertTrue(chargerPipelineIsActive(StationsContentType.CHARGERS))
    }

    @Test
    fun onlyTheVisibleContentTypeLoadsNearbyResults() {
        val nearby = StationSearchScope.Nearby(StationCoordinates(28.1, -15.4))

        assertTrue(shouldLoadFuelResults(StationsContentType.FUEL, nearby))
        assertFalse(shouldLoadChargerResults(StationsContentType.FUEL, nearby))
        assertFalse(shouldLoadFuelResults(StationsContentType.CHARGERS, nearby))
        assertTrue(shouldLoadChargerResults(StationsContentType.CHARGERS, nearby))
    }
}
