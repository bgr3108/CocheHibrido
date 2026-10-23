package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.viewmodel.ScopedMapBounds
import org.junit.Assert.*
import org.junit.Test

class StationMapCameraTest {
    @Test fun provinceChangesInvalidateSavedCamera() {
        assertNotEquals(stationMapCameraScopeKey(zone), stationMapCameraScopeKey(zone.copy(province = "SEVILLA")))
    }
    @Test fun municipalityChangesInvalidateSavedCamera() {
        assertNotEquals(stationMapCameraScopeKey(zone), stationMapCameraScopeKey(zone.copy(municipality = "Madrid")))
        assertNotEquals(stationMapCameraScopeKey(zone), stationMapCameraScopeKey(zone.copy(municipality = "null")))
    }
    @Test fun nearbyLocationsReuseOneMapAndAreCentredByExplicitRequests() {
        assertEquals(stationMapCameraScopeKey(StationSearchScope.Nearby(StationCoordinates(40.0, -3.0))),
            stationMapCameraScopeKey(StationSearchScope.Nearby(StationCoordinates(41.0, -4.0))))
    }
    @Test fun boundsAreAvailableForTheirOwnScope() {
        assertEquals(bounds, ScopedMapBounds(zone, bounds).forScope(zone))
    }
    @Test fun delayedBoundsNeverApplyToAnotherProvinceOrMunicipality() {
        val frame = ScopedMapBounds(zone, bounds)
        assertNull(frame.forScope(zone.copy(province = "SEVILLA")))
        assertNull(frame.forScope(zone.copy(municipality = "Madrid")))
    }
    @Test fun missingBoundsAndNoScopeDoNotInventACameraTarget() {
        assertNull(ScopedMapBounds(zone, null).forScope(zone))
        assertNull(ScopedMapBounds(zone, bounds).forScope(StationSearchScope.None))
    }
    private val zone = StationSearchScope.ManualZone("MADRID", null)
    private val bounds = StationMapBounds(40.0, -4.0, 41.0, -3.0)
}
