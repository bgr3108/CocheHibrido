package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationRepository

/** Defers the first MITECO request until the user actually opens Estaciones. */
internal class FuelStationsRefreshCoordinator(
    private val metadataProvider: suspend () -> StationCacheMetadataEntity?,
    private val refresh: suspend () -> Unit,
    private val isStale: (StationCacheMetadataEntity?) -> Boolean = StationRepository::isStale
) {
    private var stationEntryHandled = false

    suspend fun refreshIfNeededOnStationsOpened() {
        if (stationEntryHandled) return
        stationEntryHandled = true
        if (isStale(metadataProvider())) refresh()
    }

    suspend fun refreshManually() = refresh()
}
