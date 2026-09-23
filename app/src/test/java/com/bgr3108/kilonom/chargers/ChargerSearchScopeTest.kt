package com.bgr3108.kilonom.chargers

import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationSearchScope
import org.junit.Assert.assertEquals
import org.junit.Test

class ChargerSearchScopeTest {
    @Test
    fun chargerSearchDoesNotHaveANationalDefault() {
        assertEquals(StationSearchScope.None, ChargerFilter().searchScope(null))
    }

    @Test
    fun manualZoneOverridesAvailableLocation() {
        val location = StationCoordinates(28.11, -15.41)
        assertEquals(StationSearchScope.Nearby(location), ChargerFilter().searchScope(location))
        assertEquals(
            StationSearchScope.ManualZone("LAS PALMAS", null),
            ChargerFilter(province = "LAS PALMAS").searchScope(location)
        )
    }

    @Test
    fun manualChargerZoneAfterNearbyRestoresPowerOrder() {
        val result = normalizeChargerFilterForScope(
            ChargerFilter(province = "PALMAS (LAS)", sortOrder = ChargerSortOrder.DISTANCE),
            hadNearbyLocation = true,
            hasLocation = true
        )

        assertEquals(ChargerSortOrder.POWER, result.sortOrder)
    }
}
