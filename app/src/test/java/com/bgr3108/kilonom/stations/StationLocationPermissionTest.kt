package com.bgr3108.kilonom.stations

import android.Manifest
import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationLocationPermissionTest {
    @Test
    fun coarsePermissionFromTheDialog_continuesTheLocationFlow() {
        assertTrue(
            locationPermissionGranted(
                mapOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION to true,
                    Manifest.permission.ACCESS_FINE_LOCATION to false
                )
            )
        )
    }

    @Test
    fun finePermissionFromTheDialog_continuesTheLocationFlow() {
        assertTrue(
            locationPermissionGranted(
                mapOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION to true,
                    Manifest.permission.ACCESS_FINE_LOCATION to true
                )
            )
        )
    }

    @Test
    fun rejectedPermission_keepsTheLocationFeatureInactive() {
        assertFalse(
            locationPermissionGranted(
                mapOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION to false,
                    Manifest.permission.ACCESS_FINE_LOCATION to false
                )
            )
        )
    }

    @Test
    fun coarseLocation_usesTheNetworkProviderWithoutRequiringFinePermission() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER),
            locationProviderOrder(hasFinePermission = false, networkEnabled = true, gpsEnabled = true)
        )
    }

    @Test
    fun fineLocation_triesNetworkThenGpsAsOneShotFallbacks() {
        assertEquals(
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER),
            locationProviderOrder(hasFinePermission = true, networkEnabled = true, gpsEnabled = true)
        )
    }

    @Test
    fun validInMemoryLocation_enablesDistanceSortingWithoutPersistingIt() {
        assertTrue(canSortStationsByDistance(StationCoordinates(40.4168, -3.7038)))
        assertFalse(canSortStationsByDistance(null))
    }
}
