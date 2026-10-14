package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** Exercises the production tab dispatcher in real Compose, without opening the user's app UI. */
@RunWith(AndroidJUnit4::class)
class StatisticsTabSubscriptionsTest {
    @Test
    fun onlyVisibleTabCollects_switchCancelsHiddenCollectors_returnReadsLatestValue() = runBlocking {
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                onFrame(System.nanoTime())
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + clock)
        val recomposer = Recomposer(scope.coroutineContext)
        val composition = Composition(NoOpApplier(), recomposer)
        val selected = mutableStateOf(StatisticsTab.GLOBAL)
        val global = Probe()
        val periods = Probe()
        val trends = Probe()
        try {
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            withContext(Dispatchers.Main.immediate) {
                composition.setContent {
                    StatisticsTabContent(
                        selected.value,
                        global = { CollectProbe(global) },
                        periods = { CollectProbe(periods) },
                        trends = { CollectProbe(trends) }
                    )
                }
            }
            await { global.values.subscriptionCount.first { it == 1 } }
            assertEquals(0, periods.values.subscriptionCount.value)
            assertEquals(0, trends.values.subscriptionCount.value)

            withContext(Dispatchers.Main.immediate) {
                selected.value = StatisticsTab.PERIODS
                Snapshot.sendApplyNotifications()
            }
            await { periods.values.subscriptionCount.first { it == 1 } }
            await { global.values.subscriptionCount.first { it == 0 } }
            assertEquals(0, trends.values.subscriptionCount.value)
            global.values.value = 42

            withContext(Dispatchers.Main.immediate) {
                selected.value = StatisticsTab.TRENDS
                Snapshot.sendApplyNotifications()
            }
            await { trends.values.subscriptionCount.first { it == 1 } }
            await { periods.values.subscriptionCount.first { it == 0 } }
            assertEquals(0, global.values.subscriptionCount.value)

            withContext(Dispatchers.Main.immediate) {
                selected.value = StatisticsTab.GLOBAL
                Snapshot.sendApplyNotifications()
            }
            await { global.observed.first { it == 42 } }
            await { trends.values.subscriptionCount.first { it == 0 } }
            assertEquals(1, global.values.subscriptionCount.value)
            assertEquals(0, periods.values.subscriptionCount.value)
        } finally {
            withContext(Dispatchers.Main.immediate) {
                composition.dispose()
                recomposer.close()
            }
            requireNotNull(scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    @Composable
    private fun CollectProbe(probe: Probe) {
        LaunchedEffect(probe) {
            probe.values.collect { probe.observed.value = it }
        }
    }

    private class Probe {
        val values = MutableStateFlow(0)
        val observed = MutableStateFlow(0)
    }

    private suspend fun <T> await(block: suspend () -> T): T =
        withTimeout(10.seconds) { block() }

    private class NoOpApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
