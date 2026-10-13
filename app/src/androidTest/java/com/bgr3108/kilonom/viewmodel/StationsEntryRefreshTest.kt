package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.chargers.ChargerCacheDatabase
import com.bgr3108.kilonom.chargers.ChargerRepository
import com.bgr3108.kilonom.stations.MitecoStationsParser
import com.bgr3108.kilonom.stations.StationCacheDao
import com.bgr3108.kilonom.stations.StationCacheDatabase
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class StationsEntryRefreshTest {
    @Test
    fun reentryAfterCooldownRetriesInTheSameViewModelButRepeatedOpenNotificationsDoNot() = runBlocking {
        withFixture { f ->
            onMain { f.vm.onStationsOpened() }
            f.awaitError()
            onMain { f.vm.onStationsOpened() }
            assertEquals(1, f.calls.value)
            f.monotonicNow = StationsRefreshCoordinator.FAILURE_COOLDOWN_MILLIS - 1
            onMain { f.vm.onStationsClosed(); f.vm.onStationsOpened() }
            assertEquals(1, f.calls.value)
            f.monotonicNow++
            // Remaining on the same screen is not a new entry, even after the cooldown expires.
            onMain { f.vm.onStationsOpened() }
            assertEquals(1, f.calls.value)
            onMain { f.vm.onStationsClosed(); f.vm.onStationsOpened() }
            await { f.calls.first { it == 2 } }
            f.awaitError()
            assertEquals(0, f.chargerCalls)
            assertFalse(await { f.vm.uiState.first { !it.isRefreshing } }.isRefreshing)
        }
    }

    @Test
    fun recentCacheSkipsEveryEntryAndManualRefreshStillWorksDuringFailureCooldown() = runBlocking {
        withFixture { f ->
            f.metadata = f.metadata.copy(downloadedAtMillis = System.currentTimeMillis())
            onMain { f.vm.onStationsOpened(); f.vm.onStationsClosed(); f.vm.onStationsOpened() }
            assertEquals(0, f.calls.value)
            f.metadata = f.metadata.copy(downloadedAtMillis = 1)
            onMain { f.vm.onStationsClosed(); f.vm.onStationsOpened() }
            f.awaitError()
            f.succeed = true
            onMain { f.vm.refresh() }
            await { f.vm.uiState.first { it.refreshSucceeded && !it.isRefreshing } }
            assertEquals(2, f.calls.value)
            assertEquals(0, f.chargerCalls)
        }
    }

    @Test
    fun reentryAndManualRequestsCannotOverlapAnAutomaticDownload() = runBlocking {
        withFixture { f ->
            f.succeed = true
            val gate = CountDownLatch(1)
            f.downloadGate = gate
            onMain { f.vm.onStationsOpened() }
            await { f.calls.first { it == 1 } }
            f.monotonicNow = StationsRefreshCoordinator.FAILURE_COOLDOWN_MILLIS
            onMain {
                f.vm.onStationsClosed(); f.vm.onStationsOpened()
                f.vm.onStationsOpened(); f.vm.refresh()
            }
            assertEquals(1, f.calls.value)
            gate.countDown()
            await { f.vm.uiState.first { it.refreshSucceeded && !it.isRefreshing } }
            assertEquals(1, f.calls.value)
        }
    }

    private suspend fun withFixture(test: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { test(f) }
        finally {
            f.downloadGate?.countDown()
            onMain { f.store.clear() }
            f.vm.viewModelScope.coroutineContext[Job]?.join()
            f.stations.close(); f.chargers.close()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stations = Room.inMemoryDatabaseBuilder(context, StationCacheDatabase::class.java).build()
        val chargers = Room.inMemoryDatabaseBuilder(context, ChargerCacheDatabase::class.java).build()
        val store = ViewModelStore()
        val calls = MutableStateFlow(0)
        var chargerCalls = 0
        var monotonicNow = 0L
        var metadata = StationCacheMetadataEntity(downloadedAtMillis = 1, sourceUpdatedAtMillis = null, sourceUrl = "fixture")
        @Volatile var succeed = false
        @Volatile var downloadGate: CountDownLatch? = null
        private val dao = object : StationCacheDao by stations.stationCacheDao() {
            override suspend fun getMetadata() = metadata
        }
        val vm = onMain {
            StationsViewModel(
                StationRepository(stations, dao) {
                    calls.update { it + 1 }
                    // download() is synchronous and the repository executes it on Dispatchers.IO.
                    downloadGate?.let { check(it.await(5, TimeUnit.SECONDS)) { "Fixture download was not released" } }
                    if (!succeed) throw IOException("fixture offline")
                    MitecoStationsParser.parse("""{"ListaEESSPrecio":[{"IDEESS":"fixture","Rótulo":"Estación","Provincia":"MADRID","Municipio":"Madrid","Precio Gasolina 95 E5":"1,50"}]}""")
                },
                ChargerRepository(chargers, chargers.chargerCacheDao()) { chargerCalls++; error("Unexpected charger download") },
                elapsedRealtime = { monotonicNow }
            ).also { store.put("stations", it) }
        }
        suspend fun awaitError() = await { vm.uiState.first { it.refreshError != null && !it.isRefreshing } }
    }

    private companion object {
        suspend fun <T> await(block: suspend () -> T): T = withTimeout(5.seconds) { block() }
        fun <T> onMain(action: () -> T): T {
            val result = AtomicReference<T>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result.set(action()) }
            return result.get()
        }
    }
}
