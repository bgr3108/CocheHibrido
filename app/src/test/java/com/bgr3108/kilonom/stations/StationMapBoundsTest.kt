package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMapBoundsTest {
    @Test
    fun normalViewport_containsOnlyItsCoordinates() {
        val bounds = StationMapBounds(south = 40.0, west = -3.9, north = 40.6, east = -3.4)

        assertTrue(bounds.contains(StationCoordinates(40.4, -3.7)))
        assertFalse(bounds.contains(StationCoordinates(40.7, -3.7)))
        assertFalse(bounds.contains(StationCoordinates(40.4, -4.0)))
    }

    @Test
    fun antimeridianViewport_containsBothSidesWithoutIncludingTheMiddle() {
        val bounds = StationMapBounds(south = -10.0, west = 170.0, north = 10.0, east = -170.0)

        assertTrue(bounds.crossesAntimeridian)
        assertTrue(bounds.contains(StationCoordinates(0.0, 179.0)))
        assertTrue(bounds.contains(StationCoordinates(0.0, -179.0)))
        assertFalse(bounds.contains(StationCoordinates(0.0, 0.0)))
    }

    @Test
    fun nearbyIntersection_keepsOnlyTheViewportPartOfTheLocalSearchBox() {
        val viewport = StationMapBounds(south = 40.3, west = -3.8, north = 40.7, east = -3.2)
        val nearby = StationBoundingBox(40.4, 40.5, -3.7, -3.5)

        assertEquals(StationMapBounds(40.4, -3.7, 40.5, -3.5), viewport.intersect(nearby))
    }
}
