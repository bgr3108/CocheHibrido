package com.bgr3108.kilonom.ui.screens

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.chargers.ChargerListItem
import com.bgr3108.kilonom.viewmodel.ScopedMapBounds
import com.bgr3108.kilonom.testing.OperationTestActivity
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Production MapLibre surfaces, real pointer gestures and Activity saved state. No repositories. */
@RunWith(AndroidJUnit4::class)
class MapCameraRestorationTest {
    @Test fun stationCameraSurvivesActivityRecreationAfterPanAndZoom() = recreation(charger = false)
    @Test fun chargerCameraSurvivesActivityRecreationAfterPanAndZoom() = recreation(charger = true)
    @Test fun nearbyStationCameraSurvivesRecreation() = recreation(charger = false, nearby = true)
    @Test fun nearbyChargerCameraSurvivesRecreation() = recreation(charger = true, nearby = true)
    @Test fun stationProvinceAndMunicipalityChangesStillCentreWithDelayedBounds() = changeScope(charger = false)
    @Test fun chargerProvinceAndMunicipalityChangesStillCentreWithDelayedBounds() = changeScope(charger = true)
    @Test fun stationListMapFiltersAndDataPreserveUserCamera() = listAndData(charger = false)
    @Test fun chargerListMapFiltersAndDataPreserveUserCamera() = listAndData(charger = true)
    @Test fun explicitStationLocationRequestRecentresEvenAtTheSameCoordinates() = location(charger = false)
    @Test fun explicitChargerLocationRequestRecentresEvenAtTheSameCoordinates() = location(charger = true)

    private fun recreation(charger: Boolean, nearby: Boolean = false) = runBlocking {
        fixture(charger, nearby) { f, scenario ->
            f.awaitInitialCamera()
            panAndZoom(f, scenario)
            val chosen = f.camera()
            val before = f.lastViewport.get()
            f.lastViewport.set(null)
            f.phase = "restoration"
            scenario.recreate()
            f.awaitIdle()
            assertCamera(chosen, f.camera())
            assertEquals(before!!.south, f.lastViewport.get()!!.south, 0.001)
            assertEquals(before.west, f.lastViewport.get()!!.west, 0.001)
        }
    }

    private fun changeScope(charger: Boolean) = runBlocking {
        fixture(charger) { f, scenario ->
            f.awaitInitialCamera()
            panAndZoom(f, scenario)
            val province = StationSearchScope.ManualZone("SEVILLA", null)
            val municipality = province.copy(municipality = "SEVILLA")
            withContext(Dispatchers.Main.immediate) { f.scope = province; f.lastViewport.set(null) }
            // The preceding province's cached bounds must not consume the new initial-camera request.
            f.awaitCamera { abs(it[4] - 4.4) < 0.01 }
            scenario.recreate() // Restore while still waiting for the new scope's aggregate query.
            withContext(Dispatchers.Main.immediate) { f.frame = ScopedMapBounds(province, SEVILLE) }
            f.awaitTarget(SEVILLE)
            withContext(Dispatchers.Main.immediate) {
                f.scope = municipality
                f.frame = ScopedMapBounds(municipality, MUNICIPALITY)
                f.lastViewport.set(null)
            }
            f.awaitTarget(MUNICIPALITY)
        }
    }

    private fun listAndData(charger: Boolean) = runBlocking {
        fixture(charger) { f, scenario ->
            f.awaitInitialCamera()
            panAndZoom(f, scenario)
            val chosen = f.camera()
            withContext(Dispatchers.Main.immediate) { f.visible = false }
            withTimeout(5.seconds) { while (f.renderedMap.get()) delay(16.milliseconds) }
            // Frame arrives from a filter/cache change while Map is not composed.
            withContext(Dispatchers.Main.immediate) {
                f.frame = ScopedMapBounds(f.scope, SEVILLE)
                f.dataRevision++
            }
            f.lastViewport.set(null)
            withContext(Dispatchers.Main.immediate) { f.visible = true }
            f.awaitIdle()
            assertCamera(chosen, f.camera())
            val callbacks = f.viewportCalls.get()
            withContext(Dispatchers.Main.immediate) { f.dataRevision++ }
            delay(1200.milliseconds)
            assertCamera(chosen, f.camera())
            assertEquals("Data changes must not generate a camera/viewport loop", callbacks, f.viewportCalls.get())
        }
    }

    private fun location(charger: Boolean) = runBlocking {
        fixture(charger, nearby = true) { f, scenario ->
            f.awaitInitialCamera()
            panAndZoom(f, scenario)
            f.lastViewport.set(null)
            withContext(Dispatchers.Main.immediate) { f.requestId++ }
            f.awaitInitialCamera()
            val next = StationCoordinates(40.45, -3.65)
            f.lastViewport.set(null)
            withContext(Dispatchers.Main.immediate) { f.scope = StationSearchScope.Nearby(next); f.requestId++ }
            f.awaitInitialCamera()
        }
    }

    private suspend fun fixture(
        charger: Boolean,
        nearby: Boolean = false,
        test: suspend (Fixture, ActivityScenario<OperationTestActivity>) -> Unit
    ) {
        val f = Fixture(charger).apply { if (nearby) scope = StationSearchScope.Nearby(ORIGIN) }
        OperationTestActivity.content = { f.Content() }
        try {
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                test(f, scenario)
            }
        } finally { OperationTestActivity.content = null }
    }

    private class Fixture(val charger: Boolean) {
        var phase = "initial"
        var scope by mutableStateOf<StationSearchScope>(StationSearchScope.ManualZone("MADRID", null))
        var frame by mutableStateOf(ScopedMapBounds(scope, BOUNDS))
        var visible by mutableStateOf(true)
        var dataRevision by mutableIntStateOf(0)
        var requestId by mutableLongStateOf(0L)
        private val registry = AtomicReference<SaveableStateRegistry>()
        val lastViewport = AtomicReference<StationMapBounds>()
        val viewportCalls = AtomicInteger()
        val renderedMap = AtomicBoolean()
        private var lastChangedAt = 0L
        @Composable fun Content() {
            val saved = LocalSaveableStateRegistry.current
            val holder = rememberStationMapStateHolder(scope)
            val visibleNow = visible
            SideEffect { registry.set(saved); renderedMap.set(visibleNow) }
            MaterialTheme {
                if (visible) holder.SaveableStateProvider(stationMapCameraScopeKey(scope)) {
                    val location = (scope as? StationSearchScope.Nearby)?.origin
                    if (charger) ChargerMap(
                        if (dataRevision == 0) emptyList() else listOf(CHARGER.copy(maxPowerKw = dataRevision.toDouble())),
                        location, frame.forScope(scope), scope is StationSearchScope.ManualZone, ::onViewport, {}, Modifier.fillMaxSize(), requestId)
                    else StationMap(
                        if (dataRevision == 0) emptyList() else listOf(STATION.copy(price = dataRevision.toDouble())),
                        location, frame.forScope(scope), scope is StationSearchScope.ManualZone, ::onViewport, {}, Modifier.fillMaxSize(), requestId)
                } else Text("Lista aislada")
            }
        }
        private fun onViewport(bounds: StationMapBounds) {
            lastViewport.set(bounds)
            viewportCalls.incrementAndGet()
            lastChangedAt = SystemClock.elapsedRealtime()
        }
        suspend fun camera(): List<Double> = withContext(Dispatchers.Main.immediate) {
            cameras(registry.get()?.performSave()).single()
        }
        suspend fun awaitCamera(predicate: (List<Double>) -> Boolean) {
            try {
                withTimeout(25.seconds) {
                    while (true) {
                        val value = withContext(Dispatchers.Main.immediate) { cameras(registry.get()?.performSave()).singleOrNull() }
                        if (value != null && predicate(value)) break
                        delay(32.milliseconds)
                    }
                }
            } catch (error: TimeoutCancellationException) {
                throw AssertionError("Camera timeout during $phase: ${camera()}, viewport=${lastViewport.get()}", error)
            }
        }
        suspend fun awaitInitialCamera() {
            val target = (scope as? StationSearchScope.Nearby)?.origin?.let(::userLocationCamera)
                ?: checkNotNull(frame.forScope(scope)).toMapCameraPosition()
            awaitCamera { abs(it[1] - target.target.longitude) < 0.001 && abs(it[2] - target.target.latitude) < 0.001 && abs(it[4] - target.zoom) < 0.01 }
            awaitIdle()
        }
        suspend fun awaitTarget(bounds: StationMapBounds) {
            val target = bounds.toMapCameraPosition()
            awaitCamera { abs(it[1] - target.target.longitude) < 0.001 && abs(it[2] - target.target.latitude) < 0.001 && abs(it[4] - target.zoom) < 0.01 }
            awaitIdle()
        }
        suspend fun awaitIdle() {
            try {
                withTimeout(25.seconds) {
                    // Includes an interval longer than Kilonom's initial 500 ms camera animation.
                    while (!withContext(Dispatchers.Main.immediate) {
                        lastViewport.get() != null && SystemClock.elapsedRealtime() - lastChangedAt > 900L
                    }) delay(32.milliseconds)
                }
            } catch (error: TimeoutCancellationException) {
                throw AssertionError("Viewport timeout during $phase: ${camera()}, viewport=${lastViewport.get()}", error)
            }
        }
    }

    private companion object {
        val BOUNDS = StationMapBounds(south = 40.3, west = -3.9, north = 40.5, east = -3.6)
        val SEVILLE = StationMapBounds(37.3, -6.1, 37.5, -5.8)
        val MUNICIPALITY = StationMapBounds(37.38, -6.0, 37.40, -5.98)
        val ORIGIN = StationCoordinates(40.4, -3.7)
        val STATION = StationListItem("fixture", "Fixture", "", "Madrid", "MADRID", 40.4, -3.7, null, null,
            "gasolina_95_e5", 1.5, "Gasolina", true)
        val CHARGER = ChargerListItem("fixture", "Fixture", "Fixture", "", "Madrid", "MADRID", null, 40.4, -3.7, null, listOf("CCS"), 150.0)
        suspend fun panAndZoom(f: Fixture, scenario: ActivityScenario<OperationTestActivity>) {
            val initial = f.camera()
            f.phase = "pan"
            f.lastViewport.set(null)
            gesture(scenario, zoom = false)
            f.awaitCamera { abs(it[1] - initial[1]) > 0.001 || abs(it[2] - initial[2]) > 0.001 }
            f.awaitIdle()
            f.phase = "zoom"
            f.lastViewport.set(null)
            gesture(scenario, zoom = true)
            f.awaitCamera { it[4] > initial[4] + 0.3 }
            f.awaitIdle()
        }
        fun cameras(value: Any?): List<List<Double>> = when (value) {
            is Map<*, *> -> value.values.flatMap(::cameras)
            is List<*> -> if (value.size == 5 && value.all { it is Double }) {
                listOf(value.map { it as Double })
            } else value.flatMap(::cameras)
            else -> emptyList()
        }
        fun assertCamera(expected: List<Double>, actual: List<Double>) {
            assertEquals(5, actual.size)
            expected.zip(actual).forEachIndexed { index, (a, b) -> assertEquals("camera component $index", a, b, 0.0001) }
        }
        suspend fun gesture(scenario: ActivityScenario<OperationTestActivity>, zoom: Boolean) {
            var centerX = 0f
            var centerY = 0f
            scenario.onActivity { centerX = it.window.decorView.width / 2f; centerY = it.window.decorView.height / 2f }
            val down = SystemClock.uptimeMillis()
            fun touch(action: Int, x: Float, spread: Float = 0f) {
                val count = if (spread > 0) 2 else 1
                val properties = Array(count) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
                val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
                    this.x = x + if (index == 0) -spread else spread
                    y = centerY; pressure = 1f; size = 1f
                } }
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, if (zoom) centerX - 90f else centerX)
            if (zoom) touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), centerX, 90f)
            repeat(20) { index ->
                touch(MotionEvent.ACTION_MOVE, if (zoom) centerX else centerX + (index + 1) * 12f,
                    if (zoom) 90f + (index + 1) * 6f else 0f)
                delay(20.milliseconds)
            }
            if (zoom) touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), centerX, 210f)
            touch(MotionEvent.ACTION_UP, if (zoom) centerX - 210f else centerX + 240f)
        }
    }
}
