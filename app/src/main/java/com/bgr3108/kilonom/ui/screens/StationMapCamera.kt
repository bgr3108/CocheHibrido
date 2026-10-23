package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationSearchScope
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.map.MapState
import kotlin.time.Duration.Companion.milliseconds

/** Filters are not camera scopes; a new location is handled by its own explicit centring effect. */
internal fun stationMapCameraScopeKey(scope: StationSearchScope): String = when (scope) {
    StationSearchScope.None -> "none"
    is StationSearchScope.Nearby -> "nearby"
    is StationSearchScope.ManualZone -> "manual:${scope.province.length}:${scope.province}:" +
        (scope.municipality?.let { "${it.length}:$it" } ?: "-")
}

/** Keep one camera while List hides Map, but never restore a different manual zone's camera. */
@Composable
internal fun rememberStationMapStateHolder(scope: StationSearchScope): SaveableStateHolder {
    val holder = rememberSaveableStateHolder()
    val scopeKey = stationMapCameraScopeKey(scope)
    var previousScope by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(scopeKey) {
        if (scope !is StationSearchScope.None) {
            if (previousScope != scopeKey) previousScope?.let(holder::removeState)
            previousScope = scopeKey
        }
    }
    return holder
}

/** MapLibre saves the camera itself. These small saved flags prevent replaying completed requests. */
@Composable
internal fun rememberStationMapCamera(
    mapState: MapState,
    initialBounds: StationMapBounds?,
    waitForInitialCamera: Boolean,
    currentLocation: StationCoordinates?,
    locationRequestId: Long
): State<Boolean> {
    val waiting = rememberSaveable(waitForInitialCamera) { mutableStateOf(waitForInitialCamera) }
    var centredLocation by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialBounds, waitForInitialCamera) {
        if (waiting.value && initialBounds != null) {
            try {
                mapState.animateCameraPosition(initialBounds.toMapCameraPosition(), CameraAnimation.Ease(500.milliseconds))
            } finally {
                waiting.value = false
            }
        } else if (!waitForInitialCamera) waiting.value = false
    }
    LaunchedEffect(currentLocation, locationRequestId) {
        currentLocation?.takeIf(StationCoordinates::isValid)?.let { location ->
            val request = "${location.latitude}:${location.longitude}:$locationRequestId"
            if (centredLocation != request) {
                centredLocation = request
                mapState.animateCameraPosition(userLocationCamera(location), CameraAnimation.Ease(500.milliseconds))
            }
        }
    }
    return waiting
}
