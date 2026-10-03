package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelStationsRefreshCoordinatorTest {

    @Test
    fun creationWithoutOpeningStations_neverStartsARefresh() {
        var refreshes = 0
        FuelStationsRefreshCoordinator(
            metadataProvider = { null },
            refresh = { refreshes++ }
        )

        assertEquals(0, refreshes)
    }

    @Test
    fun openingStations_refreshesWhenThereIsNoCache() = runBlocking {
        var refreshes = 0
        val coordinator = FuelStationsRefreshCoordinator(
            metadataProvider = { null },
            refresh = { refreshes++ }
        )

        coordinator.refreshIfNeededOnStationsOpened()
        coordinator.refreshIfNeededOnStationsOpened()

        assertEquals(1, refreshes)
    }

    @Test
    fun openingStations_withAFreshCacheDoesNotUseNetwork() = runBlocking {
        var refreshes = 0
        val coordinator = FuelStationsRefreshCoordinator(
            metadataProvider = {
                StationCacheMetadataEntity(
                    downloadedAtMillis = System.currentTimeMillis(),
                    sourceUpdatedAtMillis = null,
                    sourceUrl = "https://example.invalid/stations"
                )
            },
            refresh = { refreshes++ }
        )

        coordinator.refreshIfNeededOnStationsOpened()

        assertEquals(0, refreshes)
    }

    @Test
    fun openingStations_withAStaleCacheRefreshesInBackground() = runBlocking {
        var refreshes = 0
        val coordinator = FuelStationsRefreshCoordinator(
            metadataProvider = {
                StationCacheMetadataEntity(
                    downloadedAtMillis = 0,
                    sourceUpdatedAtMillis = null,
                    sourceUrl = "https://example.invalid/stations"
                )
            },
            refresh = { refreshes++ }
        )

        coordinator.refreshIfNeededOnStationsOpened()

        assertEquals(1, refreshes)
    }

    @Test
    fun manualRefresh_stillRunsRegardlessOfCacheAge() = runBlocking {
        var refreshes = 0
        val coordinator = FuelStationsRefreshCoordinator(
            metadataProvider = { null },
            refresh = { refreshes++ }
        )

        coordinator.refreshManually()

        assertEquals(1, refreshes)
    }
}
