package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.stationsForMap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.StyleLoadState
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

internal const val OPEN_FREE_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

internal enum class StationMapLoadState {
    LOADING,
    READY,
    NETWORK_ERROR,
    ERROR
}

internal fun stationMapLoadStateForFailure(error: String?): StationMapLoadState {
    val value = error.orEmpty().lowercase()
    return if (listOf("network", "internet", "connection", "timeout", "http", "ssl", "tile").any(value::contains)) {
        StationMapLoadState.NETWORK_ERROR
    } else {
        StationMapLoadState.ERROR
    }
}

internal fun stationMapStatusMessage(state: StationMapLoadState): String? = when (state) {
    StationMapLoadState.LOADING,
    StationMapLoadState.READY -> null
    StationMapLoadState.NETWORK_ERROR -> "El mapa necesita conexión para cargar nuevas zonas."
    StationMapLoadState.ERROR -> "No se ha podido cargar el mapa."
}

@UiComposable
@Composable
fun StationMap(
    stations: List<StationListItem>,
    currentLocation: StationCoordinates?,
    onStationSelected: (StationListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val stationsById by rememberUpdatedState(stations.associateBy(StationListItem::externalId))
    val selectStation by rememberUpdatedState(onStationSelected)
    val scope = rememberCoroutineScope()
    var activeMapState by remember { mutableStateOf<org.maplibre.compose.map.MapState?>(null) }
    val stationsGeoJson = remember(stations) { stations.toStationGeoJson() }
    val locationGeoJson = remember(currentLocation) { currentLocation.toLocationGeoJson() }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(OPEN_FREE_MAP_STYLE_URL),
        initialCameraPosition = StationMapDefaults.initialCamera
    ) {
        val stationSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(stationsGeoJson),
            options = GeoJsonOptions(cluster = true, clusterRadius = 54, clusterMaxZoom = 14)
        )
        val userLocationSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(locationGeoJson)
        )
        val isCluster = Feature.has("cluster")

        CircleLayer(
            id = "station-clusters",
            source = stationSource,
            filter = isCluster,
            color = const(Color(0xFF1565C0)),
            radius = const(16.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp
        )
        SymbolLayer(
            id = "station-cluster-counts",
            source = stationSource,
            filter = isCluster,
            textField = Feature.get(CLUSTER_POINT_COUNT_PROPERTY).convertToString(),
            // OpenFreeMap's Liberty style ships Noto Sans. MapLibre's generic default
            // font stack is not hosted by OpenFreeMap, so use the style's actual font.
            textFont = const(listOf("Noto Sans Regular")),
            textSize = const(12.sp),
            textColor = const(Color.White),
            textAllowOverlap = const(true),
            textIgnorePlacement = const(true)
        )
        CircleLayer(
            id = "station-points",
            source = stationSource,
            filter = !isCluster,
            color = const(Color(0xFF42A5F5)),
            radius = const(7.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp),
            hitPadding = 12.dp
        )
        CircleLayer(
            id = "current-location-halo",
            source = userLocationSource,
            color = const(Color(0xFF80CBC4)),
            radius = const(14.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )
        CircleLayer(
            id = "current-location",
            source = userLocationSource,
            color = const(Color(0xFF00695C)),
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(3.dp)
        )
    }
    LaunchedEffect(mapState) {
        activeMapState = mapState
    }
    val onMapClick = rememberUpdatedState<(ClickEvent) -> Unit> { event ->
        scope.launch {
            val map = activeMapState ?: return@launch
            val features = map.queryRenderedFeatures(
                event.screenOffset,
                STATION_INTERACTIVE_LAYER_IDS
            )
            val cluster = features.firstOrNull { feature ->
                isClusterFeature(feature.properties) && clusterPointCountLabel(feature.properties) != null
            }
            if (cluster != null) {
                val target = (cluster.geometry as? Point)?.coordinates ?: return@launch
                map.animateCameraPosition(
                    org.maplibre.compose.camera.CameraPosition(
                        target = target,
                        // MapLibre Compose provides the rendered cluster feature and its
                        // coordinates here. A bounded increment reliably reveals the next
                        // clustering level without coupling the screen to a source handle.
                        zoom = clusterTargetZoom(map.cameraPosition.zoom)
                    ),
                    duration = CLUSTER_ZOOM_ANIMATION_DURATION
                )
            } else {
                features.firstNotNullOfOrNull { feature ->
                    stationIdFromFeatureProperties(feature.properties, feature.id)
                }?.let(stationsById::get)?.let(selectStation)
            }
        }
    }
    val mapInteractions = remember {
        MapInteractions {
            callbacks {
                click {
                    onEvent { event ->
                        onMapClick.value(event)
                        ClickResult.Consume
                    }
                }
            }
        }
    }
    val loadState = when (val styleState = mapState.style.loadState) {
        is StyleLoadState.Failed -> stationMapLoadStateForFailure(styleState.reason)
        StyleLoadState.Ready -> StationMapLoadState.READY
        else -> StationMapLoadState.LOADING
    }
    LaunchedEffect(currentLocation) {
        currentLocation?.takeIf(StationCoordinates::isValid)?.let { location ->
            mapState.animateCameraPosition(
                userLocationCamera(location),
                duration = USER_LOCATION_ANIMATION_DURATION
            )
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            state = mapState,
            interactions = mapInteractions,
            modifier = Modifier.fillMaxSize(),
            // The Scaffold already keeps this map above the banner and system bars. Avoid
            // applying safeDrawing again inside MapLibre's overlay coordinate space.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Replacing the default overlay keeps the required logo and source
            // attribution inside the map, clear of the location action at the other edge.
            overlay = mapOverlay@{
                val mapOverlayScope = this
                Row(
                    modifier = Modifier
                        .align(STATION_MAP_ATTRIBUTION_ALIGNMENT)
                        .padding(STATION_MAP_OVERLAY_EDGE_PADDING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaplibreLogo()
                    mapOverlayScope.ExpandingAttributionButton(
                        contentAlignment = STATION_MAP_ATTRIBUTION_ALIGNMENT,
                        expandedContent = { attributions, textStyle ->
                            AttributionLinks(
                                attributions = attributions,
                                textStyle = textStyle.copy(fontSize = 11.sp)
                            )
                        }
                    )
                }
            }
        )
        stationMapStatusMessage(loadState)?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

/** Keeps station data local while serialising only valid point coordinates for the map source. */
internal fun List<StationListItem>.toStationGeoJson(): String = stationsForMap(this)
    .joinToString(prefix = "{\"type\":\"FeatureCollection\",\"features\":[", postfix = "]}") { station ->
        val latitude = checkNotNull(station.latitude)
        val longitude = checkNotNull(station.longitude)
        "{\"type\":\"Feature\",\"id\":${station.externalId.jsonString()}," +
            "\"properties\":{\"$STATION_ID_PROPERTY\":${station.externalId.jsonString()}}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}}"
    }

/** Resolves a stable MITECO identifier from a rendered map feature, never from its list index. */
internal fun stationIdFromFeatureProperties(properties: JsonObject?, featureId: JsonPrimitive?): String? =
    if (isClusterFeature(properties)) null
    else properties?.get(STATION_ID_PROPERTY)?.jsonPrimitive?.contentOrNull ?: featureId?.contentOrNull

/** Cluster features expand the map; they deliberately never resolve to a station sheet. */
internal fun isClusterFeature(properties: JsonObject?): Boolean =
    properties?.get(CLUSTER_PROPERTY)?.jsonPrimitive?.contentOrNull == "true"

/** The renderer supplies point_count for clustered GeoJSON features. */
internal fun clusterPointCountLabel(properties: JsonObject?): String? =
    properties?.get(CLUSTER_POINT_COUNT_PROPERTY)?.jsonPrimitive?.contentOrNull
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?.toString()

/** Expands a cluster by one bounded level while preserving the exact tapped position. */
internal fun clusterTargetZoom(currentZoom: Double): Double =
    (currentZoom + CLUSTER_ZOOM_INCREMENT).coerceAtMost(MAX_CLUSTER_ZOOM)

/** The map recentres only after a one-shot location result is available. */
internal fun userLocationCamera(location: StationCoordinates) =
    org.maplibre.compose.camera.CameraPosition(
        target = Position(longitude = location.longitude, latitude = location.latitude),
        zoom = USER_LOCATION_ZOOM
    )

private fun StationCoordinates?.toLocationGeoJson(): String = when {
    this?.isValid() == true ->
        "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}}]}"
    else -> EMPTY_FEATURE_COLLECTION
}

private fun String.jsonString(): String = buildString(length + 2) {
    append('"')
    for (character in this@jsonString) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

private object StationMapDefaults {
    val initialCamera = org.maplibre.compose.camera.CameraPosition(
        target = Position(longitude = -3.7, latitude = 40.2),
        zoom = 4.4
    )
}

private const val STATION_ID_PROPERTY = "station_id"
private const val CLUSTER_PROPERTY = "cluster"
private const val CLUSTER_POINT_COUNT_PROPERTY = "point_count"
private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val STATION_CLUSTERS_LAYER_ID = "station-clusters"
private const val STATION_POINTS_LAYER_ID = "station-points"
private val STATION_INTERACTIVE_LAYER_IDS = setOf(STATION_CLUSTERS_LAYER_ID, STATION_POINTS_LAYER_ID)
private const val CLUSTER_ZOOM_INCREMENT = 2.0
private const val MAX_CLUSTER_ZOOM = 18.0
private val CLUSTER_ZOOM_ANIMATION_DURATION = 350.milliseconds
private const val USER_LOCATION_ZOOM = 13.0
private val USER_LOCATION_ANIMATION_DURATION = 500.milliseconds

/** Official MapLibre controls stay within the map, clear of the location action at bottom end. */
internal val STATION_MAP_ATTRIBUTION_ALIGNMENT = Alignment.BottomStart
internal val STATION_MAP_OVERLAY_EDGE_PADDING = 6.dp
