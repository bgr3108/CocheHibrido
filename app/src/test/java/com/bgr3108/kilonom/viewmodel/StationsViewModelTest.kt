package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.stations.StationsViewMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StationsViewModelTest {
    @Test
    fun chargerPipeline_isInactiveWhileFuelStationsAreActive() {
        assertFalse(chargerPipelineIsActive(StationsContentType.FUEL))
    }

    @Test
    fun chargerPipeline_isActivatedOnlyForChargers() {
        assertTrue(chargerPipelineIsActive(StationsContentType.CHARGERS))
    }

    @Test
    fun onlyTheVisibleContentTypeLoadsNearbyResults() {
        val nearby = StationSearchScope.Nearby(StationCoordinates(28.1, -15.4))

        assertTrue(shouldLoadFuelResults(StationsContentType.FUEL, nearby))
        assertFalse(shouldLoadChargerResults(StationsContentType.FUEL, nearby))
        assertFalse(shouldLoadFuelResults(StationsContentType.CHARGERS, nearby))
        assertTrue(shouldLoadChargerResults(StationsContentType.CHARGERS, nearby))
    }

    @Test
    fun mapQuery_requiresAnActiveScopeMapModeAndViewport() {
        val nearby = StationSearchScope.Nearby(StationCoordinates(28.1, -15.4))
        val viewport = StationMapBounds(28.0, -15.5, 28.2, -15.3)

        assertFalse(shouldLoadFuelMapResults(StationsContentType.FUEL, nearby, StationsViewMode.MAP, null))
        assertFalse(shouldLoadFuelMapResults(StationsContentType.FUEL, StationSearchScope.None, StationsViewMode.MAP, viewport))
        assertFalse(shouldLoadFuelMapResults(StationsContentType.FUEL, nearby, StationsViewMode.LIST, viewport))
        assertTrue(shouldLoadFuelMapResults(StationsContentType.FUEL, nearby, StationsViewMode.MAP, viewport))
        assertTrue(shouldLoadChargerMapResults(StationsContentType.CHARGERS, nearby, StationsViewMode.MAP, viewport))
    }

    @Test
    fun cancelledFuelAndChargerRefreshesAlwaysRestoreTheirLoadingFlags() = runBlocking {
        val fuelRefreshing = MutableStateFlow(false)
        val fuelError = MutableStateFlow<String?>("old fuel error")
        val chargerRefreshing = MutableStateFlow(false)
        val chargerError = MutableStateFlow<String?>("old charger error")

        assertCancellation { runRefreshWithUiState(fuelRefreshing, fuelError) { throw CancellationException() } }
        assertCancellation { runRefreshWithUiState(chargerRefreshing, chargerError) { throw CancellationException() } }

        assertFalse(fuelRefreshing.value)
        assertFalse(chargerRefreshing.value)
        assertTrue(fuelError.value == null)
        assertTrue(chargerError.value == null)
    }

    @Test
    fun refreshFeedback_reportsSuccessAndFailureWhileRestoringState() = runBlocking {
        val refreshing = MutableStateFlow(false)
        val error = MutableStateFlow<String?>(null)
        val succeeded = MutableStateFlow(false)
        runRefreshWithUiState(refreshing, error, succeeded) { Result.success(1) }
        assertTrue(succeeded.value)
        assertFalse(refreshing.value)
        runRefreshWithUiState(refreshing, error, succeeded) { Result.failure<Int>(java.io.IOException()) }
        assertFalse(succeeded.value)
        assertFalse(refreshing.value)
        assertEquals("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.", error.value)
    }

    @Test
    fun errorMessagesNeverExposeTechnicalExceptions() {
        assertEquals("El servidor no está disponible. Inténtalo de nuevo más tarde.", stationRefreshErrorMessage(IllegalStateException("RIPREE respondió con HTTP 503")))
        assertEquals("Los datos recibidos no son válidos. Inténtalo de nuevo más tarde.", stationRefreshErrorMessage(IllegalStateException("CSV RIPREE truncado")))
        assertEquals("No se pudieron actualizar los datos. Inténtalo de nuevo.", stationRefreshErrorMessage(Exception("internal details")))
    }

    private suspend fun assertCancellation(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("Expected CancellationException")
        } catch (_: CancellationException) {
            // Cancellation is intentionally propagated after the loading state is cleared.
        }
    }
}
