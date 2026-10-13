package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.domain.calculateAverageFuelPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class HomeMonthlyStatisticsTest {
    @Test
    fun noConsumers_doesNotSubscribeOrCalculate() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry())
            assertEquals(0, f.sourceStarts.get())
            assertEquals(0, f.priceCalculations.get())
            assertTrue(f.prices.value.isEmpty())
        }
    }

    @Test
    fun homeAverageConsumer_doesNotActivateMonthlyWork() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry())
            val average = f.entries.subscribedEntryStatistic(f.scope, 0.0, ::calculateAverageFuelPrice)
            assertEquals(2.0, average.first { it != 0.0 }, 0.0)
            assertEquals(1, f.sourceStarts.get())
            assertEquals(0, f.priceCalculations.get())
        }
    }

    @Test
    fun consumer_receivesSameMonthlyPricesAndCosts() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry(), entry(id = 2, amount = 20.0, cost = 20.0))
            assertEquals(40.0 / 30.0, f.prices.first { it.isNotEmpty() }.single().averagePrice, 0.0)
            assertEquals(40.0, f.costs.first { it.isNotEmpty() }.single().totalCost, 0.0)
            val electric = entry(type = FuelType.ELECTRICO)
            assertEquals(2.0, calculateMonthlyPrices(listOf(electric)).single().averagePrice, 0.0)
            assertEquals(20.0, calculateMonthlyCosts(listOf(electric)).single().totalCost, 0.0)
        }
    }

    @Test
    fun leavingStopsAfterFiveSeconds_andReturningReadsCurrentData() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry())
            val average = f.entries.subscribedEntryStatistic(f.scope, 0.0, ::calculateAverageFuelPrice)
            val homeObserver = f.scope.launch { average.collect {} }
            val chart = f.prices.map { prices -> prices.map { it.averagePrice } }
                .stateIn(f.scope, SharingStarted.WhileSubscribed(), emptyList())
            val observer = f.scope.launch { chart.collect {} }
            f.prices.first { it.isNotEmpty() }
            val leftAt = System.nanoTime()
            observer.cancelAndJoin()
            f.rows.value = listOf(entry(cost = 30.0))
            f.prices.first { it.singleOrNull()?.averagePrice == 3.0 }
            f.monthlySubscribers.first { it == 0 }
            assertTrue(System.nanoTime() - leftAt >= 4_800_000_000L)
            val calculations = f.priceCalculations.get()
            f.rows.value = listOf(entry(cost = 40.0))
            average.first { it == 4.0 }
            assertEquals(calculations, f.priceCalculations.get())
            assertEquals(3.0, f.prices.value.single().averagePrice, 0.0)
            assertEquals(4.0, f.prices.first { it.singleOrNull()?.averagePrice == 4.0 }.single().averagePrice, 0.0)
            homeObserver.cancelAndJoin()
        }
    }

    @Test
    fun multipleConsumers_shareCalculationAndOneSourceSubscription() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry())
            val first = f.scope.launch { f.prices.collect {} }
            f.prices.first { it.isNotEmpty() }
            val calculations = f.priceCalculations.get()
            val second = f.scope.launch { f.prices.collect {} }
            f.costs.first { it.isNotEmpty() }
            assertEquals(calculations, f.priceCalculations.get())
            assertEquals(1, f.sourceStarts.get())
            assertEquals(1, f.rows.subscriptionCount.value)
            first.cancelAndJoin()
            second.cancelAndJoin()
        }
    }

    @Test
    fun vehicleSnapshotChange_replacesRatherThanCombinesMonthlyResults() = runBlocking {
        fixture { f ->
            f.rows.value = listOf(entry(vehicleId = 1))
            f.prices.first { it.isNotEmpty() }
            f.secondVehicleRows.value = listOf(entry(vehicleId = 2, cost = 50.0))
            f.selectedVehicle.value = 2
            assertEquals(5.0, f.prices.first { it.singleOrNull()?.averagePrice == 5.0 }.single().averagePrice, 0.0)
            assertEquals(1, f.sourceCancellations.get())
            assertEquals(0, f.rows.subscriptionCount.value)
            f.secondVehicleRows.value = emptyList()
            assertTrue(f.prices.first { it.isEmpty() }.isEmpty())
        }
    }

    @Test
    fun addEditDelete_recalculatesObservedStatistics() = runBlocking {
        fixture { f ->
            val observer = f.scope.launch { f.costs.collect {} }
            f.rows.value = listOf(entry())
            f.costs.first { it.singleOrNull()?.totalCost == 20.0 }
            f.rows.value = listOf(entry(), entry(id = 2))
            f.costs.first { it.singleOrNull()?.totalCost == 40.0 }
            f.rows.value = listOf(entry(cost = 30.0), entry(id = 2))
            f.costs.first { it.singleOrNull()?.totalCost == 50.0 }
            f.rows.value = listOf(entry(id = 2))
            assertEquals(20.0, f.costs.first { it.singleOrNull()?.totalCost == 20.0 }.single().totalCost, 0.0)
            observer.cancelAndJoin()
        }
    }

    @Test
    fun emptyAndInvalidEntries_preserveInitialValuesAndEconomicRules() = runBlocking {
        fixture { f ->
            assertTrue(f.prices.value.isEmpty())
            assertTrue(f.costs.value.isEmpty())
            f.rows.value = listOf(entry(), entry(id = 2, amount = -1.0))
            assertEquals(2.0, f.prices.first { it.isNotEmpty() }.single().averagePrice, 0.0)
            assertEquals(20.0, f.costs.first { it.isNotEmpty() }.single().totalCost, 0.0)
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try {
            withTimeout(15.seconds) { block(f) }
        } finally {
            f.scope.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val rows = MutableStateFlow<List<FuelEntry>>(emptyList())
        val secondVehicleRows = MutableStateFlow<List<FuelEntry>>(emptyList())
        val selectedVehicle = MutableStateFlow(1L)
        val sourceStarts = AtomicInteger()
        val sourceCancellations = AtomicInteger()
        val priceCalculations = AtomicInteger()
        val monthlySubscribers = MutableStateFlow(0)
        // Mirrors HomeViewModel's single shared activeContext, with no extra grace in the fixture.
        val entries = selectedVehicle.flatMapLatest { vehicleId ->
            flow {
                sourceStarts.incrementAndGet()
                try {
                    val vehicleRows = if (vehicleId == 1L) rows else secondVehicleRows
                    vehicleRows.collect { emit(it) }
                } finally {
                    sourceCancellations.incrementAndGet()
                }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())
        private val monthlyEntries = entries
            .onStart { monthlySubscribers.update { it + 1 } }
            .onCompletion { monthlySubscribers.update { it - 1 } }
        val prices = monthlyEntries.subscribedEntryStatistic(scope, emptyList()) {
            priceCalculations.incrementAndGet()
            calculateMonthlyPrices(it.filter { row -> row.tipo == FuelType.GASOLINA })
        }
        val costs = monthlyEntries.subscribedEntryStatistic(scope, emptyList()) {
            calculateMonthlyCosts(it.filter { row -> row.tipo == FuelType.GASOLINA })
        }
    }

    private fun entry(
        id: Int = 1,
        amount: Double = 10.0,
        cost: Double = 20.0,
        type: FuelType = FuelType.GASOLINA,
        vehicleId: Long = 1
    ) = FuelEntry(
        id = id,
        fecha = LocalDate.of(2026, 1, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        cantidad = amount,
        precio = cost,
        tipo = type,
        km = 100.0,
        vehicleId = vehicleId
    )
}
