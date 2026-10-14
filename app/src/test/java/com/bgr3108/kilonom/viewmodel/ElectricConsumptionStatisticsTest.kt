package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.domain.ElectricConsumptionSegment
import com.bgr3108.kilonom.domain.calculateAverageElectricConsumptionFromSegments
import com.bgr3108.kilonom.domain.calculateBestElectricConsumptionFromSegments
import com.bgr3108.kilonom.domain.calculateElectricSegmentCountFromSegments
import com.bgr3108.kilonom.domain.calculateElectricSegments
import com.bgr3108.kilonom.domain.calculateWorstElectricConsumptionFromSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class ElectricConsumptionStatisticsTest {
    @Test
    fun noConsumers_noSourceSubscriptionOrSegmentConstruction() = runBlocking {
        fixture { f ->
            f.rows.value = HISTORY
            assertEquals(0, f.sourceStarts.get())
            assertEquals(0, f.builds.get())
            assertTrue(f.segments.value.isEmpty())
        }
    }

    @Test
    fun simultaneousMeanExtremesCountAndChart_buildOnce_andShareOneSource() = runBlocking {
        fixture { f ->
            f.rows.value = HISTORY
            val jobs = listOf(
                f.scope.launch { f.mean.collect {} },
                f.scope.launch { f.best.collect {} },
                f.scope.launch { f.worst.collect {} },
                f.scope.launch { f.count.collect {} },
                f.scope.launch { f.chart.collect {} }
            )
            assertEquals(10.0, f.mean.first { it == 10.0 }, 0.0)
            assertEquals(10.0, f.best.first { it == 10.0 }, 0.0)
            assertEquals(10.0, f.worst.first { it == 10.0 }, 0.0)
            assertEquals(2, f.count.first { it == 2 })
            assertEquals(listOf(10.0, 10.0), f.chart.first { it.size == 2 })
            assertEquals(1, f.builds.get())
            assertEquals(1, f.sourceStarts.get())
            assertEquals(1, f.rows.subscriptionCount.value)
            jobs.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun resultsMatchPreviousWeightedFormula_includingInvalidAndExtremeHistories() {
        val cases = listOf(
            emptyList(),
            HISTORY.take(1),
            HISTORY,
            HISTORY + entry(4, 400.0, 40.0),
            HISTORY + entry(4, Double.NaN, 20.0) + entry(5, 500.0, -1.0),
            listOf(entry(1, 0.0, 10.0), entry(2, 1.0, Double.MAX_VALUE)),
            listOf(entry(1, 0.0, 10.0), entry(2, 100.0, 10.0), entry(3, 100.0, 20.0))
        )
        cases.forEach { entries ->
            val segments = calculateElectricSegments(entries)
            assertEquals(previousMean(segments), calculateAverageElectricConsumptionFromSegments(segments), 0.0)
            assertEquals(segments.minOfOrNull { it.consumption } ?: 0.0, calculateBestElectricConsumptionFromSegments(segments), 0.0)
            assertEquals(segments.maxOfOrNull { it.consumption } ?: 0.0, calculateWorstElectricConsumptionFromSegments(segments), 0.0)
            assertEquals(segments.size, calculateElectricSegmentCountFromSegments(segments))
        }
    }

    @Test
    fun addEditDelete_updatesSharedResultsOncePerHistory() = runBlocking {
        fixture { f ->
            val observer = f.scope.launch { f.mean.collect {} }
            f.rows.value = HISTORY
            f.mean.first { it == 10.0 }
            f.rows.value = HISTORY + entry(4, 300.0, 40.0)
            f.mean.first { it == 20.0 }
            f.rows.value = HISTORY + entry(4, 300.0, 20.0)
            f.mean.first { it == 40.0 / 3.0 }
            f.rows.value = HISTORY
            assertEquals(10.0, f.mean.first { it == 10.0 }, 0.0)
            assertEquals(4, f.builds.get())
            observer.cancelAndJoin()
        }
    }

    @Test
    fun vehicleSwitch_cancelsOldHistory_andDoesNotMixResults() = runBlocking {
        fixture { f ->
            f.rows.value = HISTORY
            val observer = f.scope.launch { f.mean.collect {} }
            f.mean.first { it == 10.0 }
            f.otherRows.value = listOf(entry(1, 0.0, 10.0, 2), entry(2, 100.0, 30.0, 2))
            f.vehicleId.value = 2
            assertEquals(30.0, f.mean.first { it == 30.0 }, 0.0)
            assertEquals(0, f.rows.subscriptionCount.value)
            assertEquals(1, f.sourceCancellations.get())
            observer.cancelAndJoin()
        }
    }

    @Test
    fun phevFuelRows_doNotChangeElectricSegmentsOrDistanceSemantics() {
        val mixed = HISTORY + entry(4, 50.0, 30.0).copy(tipo = FuelType.GASOLINA)
        assertEquals(calculateElectricSegments(HISTORY), calculateElectricSegments(mixed))
        assertEquals(10.0, calculateAverageElectricConsumptionFromSegments(calculateElectricSegments(mixed)), 0.0)
    }

    @Test
    fun observersLeave_workStopsAfterGrace_returnUsesNewHistory() = runBlocking {
        fixture { f ->
            f.rows.value = HISTORY
            val observer = f.scope.launch { f.mean.collect {} }
            f.mean.first { it == 10.0 }
            observer.cancelAndJoin()
            f.rows.subscriptionCount.first { it == 0 }
            val builds = f.builds.get()
            f.rows.value = listOf(entry(1, 0.0, 10.0), entry(2, 100.0, 50.0))
            assertEquals(builds, f.builds.get())
            assertEquals(50.0, f.mean.first { it == 50.0 }, 0.0)
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try {
            withTimeout(15.seconds) { block(f) }
        } finally {
            requireNotNull(f.scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rows = MutableStateFlow<List<FuelEntry>>(emptyList())
        val otherRows = MutableStateFlow<List<FuelEntry>>(emptyList())
        val vehicleId = MutableStateFlow(1L)
        val sourceStarts = AtomicInteger()
        val sourceCancellations = AtomicInteger()
        val builds = AtomicInteger()
        private val entries = vehicleId.flatMapLatest { id ->
            flow {
                sourceStarts.incrementAndGet()
                try {
                    (if (id == 1L) rows else otherRows).collect { emit(it) }
                } finally {
                    sourceCancellations.incrementAndGet()
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
        val segments = entries.subscribedEntryStatistic(scope, emptyList()) {
            if (it.isNotEmpty()) builds.incrementAndGet()
            calculateElectricSegments(it)
        }
        val mean = segments.map(::calculateAverageElectricConsumptionFromSegments)
            .stateIn(scope, SharingStarted.WhileSubscribed(), 0.0)
        val best = segments.map(::calculateBestElectricConsumptionFromSegments)
            .stateIn(scope, SharingStarted.WhileSubscribed(), 0.0)
        val worst = segments.map(::calculateWorstElectricConsumptionFromSegments)
            .stateIn(scope, SharingStarted.WhileSubscribed(), 0.0)
        val count = segments.map(::calculateElectricSegmentCountFromSegments)
            .stateIn(scope, SharingStarted.WhileSubscribed(), 0)
        val chart = segments.map { list -> list.map { it.consumption } }
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
    }

    /** Frozen reference of the previous weighted-average accumulation, not the new wrapper. */
    private fun previousMean(segments: List<ElectricConsumptionSegment>): Double {
        var energy = 0.0
        var distance = 0.0
        segments.forEach {
            val nextEnergy = energy + it.energyUsed
            val nextDistance = distance + it.distance
            if (nextEnergy.isFinite() && nextDistance.isFinite()) {
                energy = nextEnergy
                distance = nextDistance
            }
        }
        return if (distance > 0.0) ((energy / distance) * 100.0).takeIf { it.isFinite() } ?: 0.0 else 0.0
    }

    companion object {
        private fun entry(id: Int, km: Double, amount: Double, vehicleId: Long = 1) =
            FuelEntry(id, id.toLong(), amount, amount, FuelType.ELECTRICO, km, vehicleId = vehicleId)

        private val HISTORY = listOf(entry(1, 0.0, 10.0), entry(2, 100.0, 10.0), entry(3, 200.0, 10.0))
    }
}
