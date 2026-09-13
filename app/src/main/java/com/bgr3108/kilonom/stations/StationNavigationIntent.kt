package com.bgr3108.kilonom.stations

import android.content.Intent
import androidx.core.net.toUri
import java.net.URLEncoder

fun createStationNavigationIntent(station: StationListItem): Intent? {
    return stationNavigationRequest(station)?.let { request ->
        Intent(request.action, request.uri.toUri())
    }
}

/**
 * Navigation is deliberately an ordinary Android VIEW request: no target package and no task flags.
 * Keeping this description platform-free also makes that contract testable without a fake Activity.
 */
internal data class StationNavigationRequest(
    val action: String,
    val uri: String,
    val packageName: String? = null,
    val flags: Int = 0
)

internal fun stationNavigationRequest(station: StationListItem): StationNavigationRequest? =
    stationNavigationUri(station)?.let { uri ->
        StationNavigationRequest(action = Intent.ACTION_VIEW, uri = uri)
    }

internal fun stationNavigationUri(station: StationListItem): String? {
    val coordinates = station.coordinatesOrNull() ?: return null
    val query = URLEncoder.encode(
        "${coordinates.latitude},${coordinates.longitude}(${station.name})",
        "UTF-8"
    ).replace("+", "%20")
    return "geo:${coordinates.latitude},${coordinates.longitude}?q=$query"
}
