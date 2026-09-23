package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.bgr3108.kilonom.chargers.ChargerListItem
import com.bgr3108.kilonom.chargers.coordinatesOrNull
import com.bgr3108.kilonom.chargers.chargersForMap
import com.bgr3108.kilonom.stations.StationCoordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.Feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.camera.CameraAnimation
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

@UiComposable
@Composable
fun ChargerMap(
    chargers: List<ChargerListItem>,
    currentLocation: StationCoordinates?,
    onChargerSelected: (ChargerListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapPreparation by produceState<ChargerMapPreparation>(
        initialValue = ChargerMapPreparation.Loading,
        key1 = chargers
    ) {
        value = ChargerMapPreparation.Loading
        value = withContext(Dispatchers.Default) { prepareChargerMap(chargers) }
    }
    when (val preparation = mapPreparation) {
        ChargerMapPreparation.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("Preparando el mapa de cargadores…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChargerMapPreparation.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("No se ha podido cargar el mapa de cargadores.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is ChargerMapPreparation.Ready -> ChargerMapContent(
                preparation = preparation,
                currentLocation = currentLocation,
                onChargerSelected = onChargerSelected,
                modifier = modifier
            )
    }
}

@UiComposable
@Composable
private fun ChargerMapContent(
    preparation: ChargerMapPreparation.Ready,
    currentLocation: StationCoordinates?,
    onChargerSelected: (ChargerListItem) -> Unit,
    modifier: Modifier
) {
    val chargersById by rememberUpdatedState(preparation.chargersById)
    val selectCharger by rememberUpdatedState(onChargerSelected)
    val scope = rememberCoroutineScope()
    var activeMapState by remember { mutableStateOf<org.maplibre.compose.map.MapState?>(null) }
    val locationGeoJson = remember(currentLocation) { currentLocation.toChargerLocationGeoJson() }
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(OPEN_FREE_MAP_STYLE_URL),
        initialCameraPosition = chargerInitialCamera
    ) {
        val chargerSource = rememberGeoJsonSource(
            data = GeoJsonData.JsonString(preparation.geoJson),
            options = GeoJsonOptions(cluster = true, clusterRadius = 54, clusterMaxZoom = 14)
        )
        val locationSource = rememberGeoJsonSource(data = GeoJsonData.JsonString(locationGeoJson))
        val isCluster = Feature.has("cluster")
        CircleLayer("charger-clusters", chargerSource, filter = isCluster, color = const(Color(0xFF00695C)), radius = const(16.dp), strokeColor = const(Color.White), strokeWidth = const(2.dp), hitPadding = 12.dp)
        SymbolLayer(
            id = "charger-cluster-counts", source = chargerSource, filter = isCluster,
            textField = Feature.get("point_count").convertToString(), textFont = const(listOf("Noto Sans Regular")),
            textSize = const(12.sp), textColor = const(Color.White), textAllowOverlap = const(true), textIgnorePlacement = const(true)
        )
        CircleLayer("charger-points", chargerSource, filter = !isCluster, color = const(Color(0xFF26A69A)), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(2.dp), hitPadding = 12.dp)
        CircleLayer("charger-current-location-halo", locationSource, color = const(Color(0xFF80CBC4)), radius = const(14.dp), strokeColor = const(Color.White), strokeWidth = const(2.dp))
        CircleLayer("charger-current-location", locationSource, color = const(Color(0xFF00695C)), radius = const(8.dp), strokeColor = const(Color.White), strokeWidth = const(3.dp))
    }
    LaunchedEffect(mapState) { activeMapState = mapState }
    val onMapClick = rememberUpdatedState<(ClickEvent) -> Unit> { event ->
        scope.launch {
            val map = activeMapState ?: return@launch
            val features = map.queryRenderedFeatures(event.screenOffset, chargerInteractiveLayerIds)
            val cluster = features.firstOrNull { isClusterFeature(it.properties) }
            if (cluster != null) {
                val target = (cluster.geometry as? Point)?.coordinates ?: return@launch
                map.animateCameraPosition(
                    org.maplibre.compose.camera.CameraPosition(target = target, zoom = clusterTargetZoom(map.cameraPosition.zoom)),
                    animation = CameraAnimation.Ease(350.milliseconds)
                )
            } else {
                features.firstNotNullOfOrNull { chargerIdFromFeature(it.properties, it.id) }
                    ?.let(chargersById::get)?.let(selectCharger)
            }
        }
    }
    val interactions = remember {
        MapInteractions { callbacks { click { onEvent { event -> onMapClick.value(event); ClickResult.Consume } } } }
    }
    val loadState = when (val style = mapState.style.loadState) {
        is StyleLoadState.Failed -> stationMapLoadStateForFailure(style.reason)
        StyleLoadState.Ready -> StationMapLoadState.READY
        else -> StationMapLoadState.LOADING
    }
    LaunchedEffect(preparation.chargersById, currentLocation) {
        currentLocation?.takeIf(StationCoordinates::isValid)?.let {
            mapState.animateCameraPosition(
                userLocationCamera(it),
                animation = CameraAnimation.Ease(500.milliseconds)
            )
        } ?: chargerStationsCamera(preparation.chargersById.values.toList())?.let { camera ->
            mapState.animateCameraPosition(
                camera,
                animation = CameraAnimation.Ease(500.milliseconds)
            )
        }
    }
    Box(modifier) {
        MaplibreMap(
            state = mapState, interactions = interactions, modifier = Modifier.fillMaxSize(),
            cameraPadding = PaddingValues(),
            overlay = mapOverlay@{
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaplibreLogo()
                    ExpandingAttributionButton(
                        contentAlignment = Alignment.BottomStart,
                        expandedContent = { attributions, textStyle ->
                            AttributionLinks(attributions = attributions, textStyle = textStyle.copy(fontSize = 11.sp))
                        }
                    )
                }
            }
        )
        stationMapStatusMessage(loadState)?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).padding(16.dp)) }
    }
}

private fun chargerStationsCamera(chargers: List<ChargerListItem>): org.maplibre.compose.camera.CameraPosition? {
    val points = chargers.mapNotNull(ChargerListItem::coordinatesOrNull)
    if (points.isEmpty()) return null
    val latitude = points.map(StationCoordinates::latitude).average()
    val longitude = points.map(StationCoordinates::longitude).average()
    val span = maxOf(
        points.maxOf(StationCoordinates::latitude) - points.minOf(StationCoordinates::latitude),
        points.maxOf(StationCoordinates::longitude) - points.minOf(StationCoordinates::longitude)
    )
    val zoom = when {
        span <= 0.02 -> 13.0
        span <= 0.08 -> 11.0
        span <= 0.30 -> 9.0
        span <= 1.2 -> 7.0
        else -> 5.5
    }
    return org.maplibre.compose.camera.CameraPosition(target = Position(longitude = longitude, latitude = latitude), zoom = zoom)
}

internal sealed interface ChargerMapPreparation {
    data object Loading : ChargerMapPreparation
    data object Failed : ChargerMapPreparation
    data class Ready(
        val geoJson: String,
        val chargersById: Map<String, ChargerListItem>
    ) : ChargerMapPreparation
}

/** Builds minimal installation-only map data away from Compose's main-thread recomposition. */
internal fun prepareChargerMap(
    chargers: List<ChargerListItem>,
    geoJsonBuilder: (List<ChargerListItem>) -> String = List<ChargerListItem>::toChargerGeoJson
): ChargerMapPreparation = try {
    ChargerMapPreparation.Ready(
        geoJson = geoJsonBuilder(chargers),
        chargersById = chargersForMap(chargers).associateBy(ChargerListItem::externalId)
    )
} catch (_: Exception) {
    ChargerMapPreparation.Failed
}

internal fun List<ChargerListItem>.toChargerGeoJson(): String = chargersForMap(this).joinToString(
    prefix = "{\"type\":\"FeatureCollection\",\"features\":[", postfix = "]}"
) { charger ->
    "{\"type\":\"Feature\",\"id\":${charger.externalId.toChargerJsonString()},\"properties\":{\"charger_id\":${charger.externalId.toChargerJsonString()}},\"geometry\":{\"type\":\"Point\",\"coordinates\":[${charger.longitude},${charger.latitude}]}}"
}

internal fun chargerIdFromFeature(properties: JsonObject?, featureId: JsonPrimitive?): String? =
    if (isClusterFeature(properties)) null else properties?.get("charger_id")?.jsonPrimitive?.contentOrNull ?: featureId?.contentOrNull

private fun StationCoordinates?.toChargerLocationGeoJson(): String = if (this?.isValid() == true) {
    "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[$longitude,$latitude]}}]}"
} else "{\"type\":\"FeatureCollection\",\"features\":[]}"

private fun String.toChargerJsonString(): String = buildString {
    append('"')
    this@toChargerJsonString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(character)
        }
    }
    append('"')
}
private val chargerInitialCamera = org.maplibre.compose.camera.CameraPosition(target = Position(longitude = -3.7, latitude = 40.2), zoom = 4.4)
private val chargerInteractiveLayerIds = setOf("charger-clusters", "charger-points")
