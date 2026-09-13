package com.bgr3108.kilonom.ui.screens

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class StationMapLoadStateTest {
    @Test
    fun attributionOverlay_isPinnedToTheBottomStartWithSmallMapEdgePadding() {
        assertEquals(Alignment.BottomStart, STATION_MAP_ATTRIBUTION_ALIGNMENT)
        assertEquals(6.dp, STATION_MAP_OVERLAY_EDGE_PADDING)
    }

    @Test
    fun navigationUnavailableMessage_isSafeAndUserFacing() {
        assertEquals(
            "No se ha encontrado una aplicación de navegación compatible.",
            stationNavigationUnavailableMessage()
        )
    }

    @Test
    fun mapNetworkFailure_hasAnOfflineFriendlyMessage() {
        val state = stationMapLoadStateForFailure("SSL connection failed while loading a tile")

        assertEquals(StationMapLoadState.NETWORK_ERROR, state)
        assertEquals("El mapa necesita conexión para cargar nuevas zonas.", stationMapStatusMessage(state))
    }

    @Test
    fun styleFailure_hasAClearNonTechnicalMessage() {
        val state = stationMapLoadStateForFailure("Style parse error")

        assertEquals(StationMapLoadState.ERROR, state)
        assertEquals("No se ha podido cargar el mapa.", stationMapStatusMessage(state))
    }

    @Test
    fun loadingAndReadyStates_doNotOverlayAnError() {
        assertNull(stationMapStatusMessage(StationMapLoadState.LOADING))
        assertNull(stationMapStatusMessage(StationMapLoadState.READY))
    }

    @Test
    fun mapGeoJson_keepsOnlyValidStationCoordinatesAndTheExternalId() {
        val valid = station(externalId = "station-1", latitude = 28.123, longitude = -15.456)
        val invalid = station(externalId = "station-2", latitude = 120.0, longitude = -15.0)

        val geoJson = listOf(valid, invalid).toStationGeoJson()

        assertTrue(geoJson.contains("station-1"))
        assertTrue(geoJson.contains("-15.456,28.123"))
        assertFalse(geoJson.contains("station-2"))
    }

    @Test
    fun mapGeoJson_escapesStationIdsBeforePassingThemToTheMapSource() {
        val geoJson = listOf(station(externalId = "station-\"quoted\"", latitude = 40.4, longitude = -3.7))
            .toStationGeoJson()

        assertTrue(geoJson.contains("station-\\\"quoted\\\""))
    }

    @Test
    fun mapGeoJson_keepsTheStableStationIdAsFeatureIdAndProperty() {
        val geoJson = listOf(station(externalId = "station-1", latitude = 40.4, longitude = -3.7))
            .toStationGeoJson()

        assertTrue(geoJson.contains("\"id\":\"station-1\""))
        assertTrue(geoJson.contains("\"station_id\":\"station-1\""))
    }

    @Test
    fun stationFeatureId_prefersTheExplicitPropertyAndFallsBackToFeatureId() {
        assertEquals(
            "from-property",
            stationIdFromFeatureProperties(
                JsonObject(mapOf("station_id" to JsonPrimitive("from-property"))),
                JsonPrimitive("from-feature-id")
            )
        )
        assertEquals(
            "from-feature-id",
            stationIdFromFeatureProperties(JsonObject(emptyMap()), JsonPrimitive("from-feature-id"))
        )
    }

    @Test
    fun clusterFeature_neverResolvesToAStationSheet() {
        val clusterProperties = JsonObject(
            mapOf(
                "cluster" to JsonPrimitive(true),
                "station_id" to JsonPrimitive("not-a-station-tap")
            )
        )

        assertTrue(isClusterFeature(clusterProperties))
        assertNull(stationIdFromFeatureProperties(clusterProperties, JsonPrimitive("fallback")))
    }

    @Test
    fun clusterPointCount_usesTheRendererProvidedPointCountAsItsLabel() {
        val clusterProperties = JsonObject(
            mapOf("cluster" to JsonPrimitive(true), "point_count" to JsonPrimitive(103))
        )

        assertEquals("103", clusterPointCountLabel(clusterProperties))
        assertNull(clusterPointCountLabel(JsonObject(emptyMap())))
    }

    @Test
    fun clusterTap_usesABoundedZoomIncrement() {
        assertEquals(10.0, clusterTargetZoom(currentZoom = 8.0), 0.0)
        assertEquals(18.0, clusterTargetZoom(currentZoom = 17.5), 0.0)
    }

    @Test
    fun userLocationCamera_centresAtTheOneShotLocationWithNearbyStationZoom() {
        val camera = userLocationCamera(com.bgr3108.kilonom.stations.StationCoordinates(28.1235, -15.4363))

        assertEquals(28.1235, camera.target.latitude, 0.0)
        assertEquals(-15.4363, camera.target.longitude, 0.0)
        assertEquals(13.0, camera.zoom, 0.0)
    }

    @Test
    fun filteringOutTheSelectedStation_closesTheStationSheet() {
        val selected = station(externalId = "station-1", latitude = 40.4, longitude = -3.7)

        assertEquals(selected, selectedStationAfterFiltering(selected, listOf(selected)))
        assertNull(selectedStationAfterFiltering(selected, emptyList()))
    }

    @Test
    fun currentFilteredStationRefreshesTheSelectedSheetWithItsDistance() {
        val selected = station(externalId = "station-1", latitude = 40.4, longitude = -3.7)
        val refreshed = selected.copy(distanceMeters = 850.0)

        assertEquals(refreshed, selectedStationAfterFiltering(selected, listOf(refreshed)))
    }

    @Test
    fun stationSheetPresentation_usesTheFilteredFuelAndOmitsAbsentDetails() {
        val presentation = station(
            externalId = "station-1",
            latitude = 40.4,
            longitude = -3.7
        ).copy(schedule = "", distanceMeters = null, sourceUpdatedAtMillis = null)
            .toDetailsPresentation()

        assertEquals("Estación de prueba", presentation.name)
        assertEquals("Dirección · Municipio · Provincia", presentation.address)
        assertEquals("Gasolina 95: 1,500 €", presentation.price)
        assertNull(presentation.schedule)
        assertNull(presentation.distance)
        assertNull(presentation.updatedAt)
    }

    @Test
    fun stationSheetPresentation_showsApproximateDistanceWhenAvailable() {
        val presentation = station(externalId = "station-1", latitude = 40.4, longitude = -3.7)
            .copy(distanceMeters = 1_800.0)
            .toDetailsPresentation()

        assertEquals("1,8 km", presentation.distance)
    }

    @Test
    fun distanceFormatting_usesMetresBelowOneKilometreAndKilometresAfterwards() {
        assertEquals("850 m", 850.0.toDisplayDistance())
        assertEquals("1,8 km", 1_800.0.toDisplayDistance())
    }

    private fun station(externalId: String, latitude: Double?, longitude: Double?) =
        com.bgr3108.kilonom.stations.StationListItem(
            externalId = externalId,
            name = "Estación de prueba",
            address = "Dirección",
            municipality = "Municipio",
            province = "Provincia",
            latitude = latitude,
            longitude = longitude,
            schedule = null,
            sourceUpdatedAtMillis = null,
            productCode = "gasolina_95_e5",
            price = 1.5,
            productName = "Gasolina 95",
            hasSelectedFuel = true
        )
}
