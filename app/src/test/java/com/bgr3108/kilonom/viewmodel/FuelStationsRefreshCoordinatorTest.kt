package com.bgr3108.kilonom.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.IOException
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationRepository

class FuelStationsRefreshCoordinatorTest {
    @Test
    fun cacheClearCannotOverlapAnActiveRefresh() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val coordinator = coordinator(refresh = { started.complete(Unit); finish.await(); Result.success(1) })
        val job = launch { coordinator.refreshManually() }
        started.await()
        var deletions = 0
        assertFalse(coordinator.clearCache { deletions++ })
        assertEquals(0, deletions)
        finish.complete(Unit)
        job.join()
        assertTrue(coordinator.clearCache { deletions++ })
        assertEquals(1, deletions)
    }

    @Test
    fun failedAndCancelledCacheClearReleaseTheGuardForRetry() = runBlocking {
        val coordinator = coordinator(refresh = { Result.success(1) })
        try {
            coordinator.clearCache { throw IOException() }
            throw AssertionError("Expected IOException")
        } catch (_: IOException) { }
        try {
            coordinator.clearCache { throw CancellationException() }
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(coordinator.clearCache { })
    }

    @Test
    fun failedMetadataReadDoesNotDownloadAndAllowsManualRetry() = runBlocking {
        var calls = 0
        val coordinator = StationsRefreshCoordinator<Long>(
            metadataProvider = { throw IOException() },
            refresh = { calls++; Result.success(1) },
            isStale = { true },
            elapsedRealtime = { 0L }
        )
        try {
            coordinator.refreshAutomaticallyIfNeeded()
            throw AssertionError("Expected IOException")
        } catch (_: IOException) { }
        assertEquals(0, calls)
        coordinator.refreshManually()
        assertEquals(1, calls)
    }
    @Test
    fun creationWithoutOpeningStations_neverStartsARefresh() {
        var calls = 0
        coordinator(refresh = { calls++; Result.success(1) })
        assertEquals(0, calls)
    }

    @Test
    fun entryChecksReevaluateCacheAgeAndRefreshAgainAtExactlyTwentyFourHours() = runBlocking {
        var now = 1L
        var metadata: StationCacheMetadataEntity? = null
        var calls = 0
        val coordinator = StationsRefreshCoordinator(
            metadataProvider = { metadata },
            refresh = {
                calls++
                metadata = StationCacheMetadataEntity(downloadedAtMillis = now, sourceUpdatedAtMillis = null, sourceUrl = "fixture")
                Result.success(1)
            },
            isStale = { StationRepository.isStale(it, now) },
            elapsedRealtime = { now }
        )
        coordinator.refreshAutomaticallyIfNeeded()
        coordinator.refreshAutomaticallyIfNeeded()
        now += StationRepository.CACHE_STALE_AFTER_MILLIS - 1
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(1, calls)
        now++
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(2, calls)
    }

    @Test
    fun freshCacheSkipsAutomaticButAllowsManualRefresh() = runBlocking {
        var calls = 0
        val coordinator = coordinator(stale = false, refresh = { calls++; Result.success(1) })
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(0, calls)
        coordinator.refreshManually()
        assertEquals(1, calls)
    }

    @Test
    fun automaticFailureDefersRetryUntilExactlyTenMinutes() = runBlocking {
        var now = 100L
        var calls = 0
        val coordinator = coordinator(clock = { now }, refresh = { calls++; Result.failure<Int>(IllegalStateException()) })
        coordinator.refreshAutomaticallyIfNeeded()
        now += StationsRefreshCoordinator.FAILURE_COOLDOWN_MILLIS - 1
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(1, calls)
        now++
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(2, calls)
    }

    @Test
    fun manualRefreshIgnoresAutomaticFailureCooldown() = runBlocking {
        var calls = 0
        val coordinator = coordinator(refresh = { calls++; Result.failure<Int>(IllegalStateException()) })
        coordinator.refreshAutomaticallyIfNeeded()
        coordinator.refreshManually()
        assertEquals(2, calls)
    }

    @Test
    fun fuelAndChargersHaveIndependentCooldowns() = runBlocking {
        var fuelCalls = 0
        var chargerCalls = 0
        val fuel = coordinator(refresh = { fuelCalls++; Result.failure<Int>(IllegalStateException()) })
        val chargers = coordinator(refresh = { chargerCalls++; Result.failure<Int>(IllegalStateException()) })
        fuel.refreshAutomaticallyIfNeeded()
        chargers.refreshAutomaticallyIfNeeded()
        fuel.refreshAutomaticallyIfNeeded()
        chargers.refreshAutomaticallyIfNeeded()
        assertEquals(1, fuelCalls)
        assertEquals(1, chargerCalls)
    }

    @Test
    fun activeRefreshDropsConcurrentAutomaticAndManualRequests() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = coordinator(refresh = {
            calls++
            entered.complete(Unit)
            complete.await()
            Result.success(1)
        })
        val job = launch { coordinator.refreshAutomaticallyIfNeeded() }
        entered.await()
        coordinator.refreshManually()
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(1, calls)
        complete.complete(Unit)
        job.join()
    }

    @Test
    fun cancellationReleasesGuardWithoutStartingFailureCooldown() = runBlocking {
        var calls = 0
        val coordinator = coordinator(refresh = {
            calls++
            if (calls == 1) throw CancellationException()
            Result.success(1)
        })
        try {
            coordinator.refreshAutomaticallyIfNeeded()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation is propagated and must not record an automatic failure.
        }
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(2, calls)
    }

    @Test
    fun manualActionDuringFreshMetadataCheckStillRefreshes() = runBlocking {
        val readingMetadata = CompletableDeferred<Unit>()
        val finishReading = CompletableDeferred<Unit>()
        var calls = 0
        val coordinator = StationsRefreshCoordinator<Long>(
            metadataProvider = { readingMetadata.complete(Unit); finishReading.await(); 1L },
            refresh = { calls++; Result.success(1) },
            isStale = { false },
            elapsedRealtime = { 0L }
        )
        val automatic = launch { coordinator.refreshAutomaticallyIfNeeded() }
        readingMetadata.await()
        val manual = launch { coordinator.refreshManually() }
        finishReading.complete(Unit)
        automatic.join()
        manual.join()
        assertEquals(1, calls)
    }

    @Test
    fun cancellationDuringMetadataReadAllowsTheNextEntryWithoutStartingCooldown() = runBlocking {
        val reading = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        var shouldBlock = true
        var calls = 0
        val coordinator = StationsRefreshCoordinator<Long>(
            metadataProvider = { if (shouldBlock) { reading.complete(Unit); blocked.await() }; null },
            refresh = { calls++; Result.success(1) },
            isStale = { true },
            elapsedRealtime = { 0L }
        )
        val firstEntry = launch { coordinator.refreshAutomaticallyIfNeeded() }
        reading.await()
        firstEntry.cancelAndJoin()
        assertTrue(firstEntry.isCancelled)
        assertEquals(0, calls)
        shouldBlock = false
        coordinator.refreshAutomaticallyIfNeeded()
        assertEquals(1, calls)
    }

    private fun coordinator(
        stale: Boolean = true,
        clock: () -> Long = { 0L },
        refresh: suspend () -> Result<Int>
    ) = StationsRefreshCoordinator<Long>(
        metadataProvider = { null },
        refresh = refresh,
        isStale = { stale },
        elapsedRealtime = clock
    )
}
