package com.bgr3108.kilonom.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import kotlinx.coroutines.CancellationException

class VehicleCatalogUpdateCoordinatorTest {

    @Test
    fun completedUpdateSurvivesTimestampWriteFailureAndIsThrottledInMemory() = runBlocking {
        val checker = FakeChecker(VehicleCatalogUpdateResult.Updated(2))
        val timestamps = FakeTimestampStore(NOW - AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS)
        timestamps.writeError = IOException("disk unavailable")
        val coordinator = coordinator(checker, timestamps)
        assertEquals(VehicleCatalogUpdateResult.Updated(2), coordinator.checkForUpdate())
        assertEquals(NOW - AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS, timestamps.lastCheck)
        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(1, checker.checkCalls.get())
        timestamps.writeError = null
        assertEquals(VehicleCatalogUpdateResult.Updated(2), coordinator.checkForUpdate())
        assertEquals(NOW, timestamps.lastCheck)
        assertEquals(2, checker.checkCalls.get())
    }

    @Test
    fun automaticReadFailureDoesNotCheckOrOverwriteMetadataAndManualStillWorks() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore(NOW)
        timestamps.readError = IOException("read unavailable")
        val coordinator = coordinator(checker, timestamps)
        assertEquals(VehicleCatalogAutomaticCheckResult.Failed, coordinator.checkAutomaticallyIfDue())
        assertEquals(0, checker.checkCalls.get())
        assertEquals(NOW, timestamps.lastCheck)
        assertEquals(VehicleCatalogUpdateResult.UpToDate(1), coordinator.checkForUpdate())
        assertEquals(1, checker.checkCalls.get())
    }

    @Test
    fun automaticCompletedResultSurvivesAuxiliaryWriteFailure() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore()
        timestamps.writeError = IOException()
        val coordinator = coordinator(checker, timestamps)
        assertEquals(VehicleCatalogAutomaticCheckResult.Completed(VehicleCatalogUpdateResult.UpToDate(1)), coordinator.checkAutomaticallyIfDue())
        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(null, timestamps.lastCheck)
        assertEquals(1, checker.checkCalls.get())
    }

    @Test
    fun cancellationWhileSavingTimestampPropagatesAndDoesNotRecordCompletion() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore()
        timestamps.writeError = CancellationException()
        val coordinator = coordinator(checker, timestamps)
        try {
            coordinator.checkForUpdate()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(null, timestamps.lastCheck)
        timestamps.writeError = null
        assertTrue(coordinator.checkAutomaticallyIfDue() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(2, checker.checkCalls.get())
    }

    @Test(expected = IllegalStateException::class)
    fun programmingErrorsInTimestampStoreAreNotSwallowed() = runBlocking {
        val timestamps = FakeTimestampStore()
        timestamps.readError = IllegalStateException("programming error")
        coordinator(FakeChecker(), timestamps).checkAutomaticallyIfDue()
        Unit
    }

    @Test
    fun noTimestamp_runsTheFirstAutomaticCheck() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore()
        val coordinator = coordinator(checker, timestamps)

        assertEquals(
            VehicleCatalogAutomaticCheckResult.Completed(VehicleCatalogUpdateResult.UpToDate(1)),
            coordinator.checkAutomaticallyIfDue()
        )
        assertEquals(1, checker.checkCalls.get())
        assertEquals(NOW, timestamps.lastCheck)
    }

    @Test
    fun checksLessThanTwentyFourHoursOld_areNotDue() = runBlocking {
        val checker = FakeChecker()
        val coordinator = coordinator(
            checker = checker,
            timestamps = FakeTimestampStore(NOW - 1L * 60L * 60L * 1000L)
        )

        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(0, checker.checkCalls.get())
    }

    @Test
    fun checkAtTwentyThreeHoursFiftyNineMinutes_isNotDue() = runBlocking {
        val checker = FakeChecker()
        val coordinator = coordinator(
            checker = checker,
            timestamps = FakeTimestampStore(NOW - AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS + 60_000L)
        )

        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(0, checker.checkCalls.get())
    }

    @Test
    fun checkAtExactlyTwentyFourHours_isDue() = runBlocking {
        val checker = FakeChecker()
        val coordinator = coordinator(
            checker = checker,
            timestamps = FakeTimestampStore(NOW - AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS)
        )

        assertTrue(coordinator.checkAutomaticallyIfDue() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(1, checker.checkCalls.get())
    }

    @Test
    fun checkOlderThanTwentyFourHours_isDue() = runBlocking {
        val checker = FakeChecker()
        val coordinator = coordinator(
            checker = checker,
            timestamps = FakeTimestampStore(NOW - AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS - 1L)
        )

        assertTrue(coordinator.checkAutomaticallyIfDue() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(1, checker.checkCalls.get())
    }

    @Test
    fun futureTimestamp_isInvalidAndIsReplacedAfterTheCheck() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore(NOW + 1L)
        val coordinator = coordinator(checker, timestamps)

        assertTrue(coordinator.checkAutomaticallyIfDue() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(1, checker.checkCalls.get())
        assertEquals(NOW, timestamps.lastCheck)
    }

    @Test
    fun negativeTimestamp_isInvalidAndAllowsAnAutomaticCheck() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore(-1L)
        val coordinator = coordinator(checker, timestamps)

        assertTrue(coordinator.checkAutomaticallyIfDue() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(1, checker.checkCalls.get())
        assertEquals(NOW, timestamps.lastCheck)
    }

    @Test
    fun automaticErrorStillRecordsTheCompletedAttemptAndThrottlesTheNextOne() = runBlocking {
        val checker = FakeChecker(result = VehicleCatalogUpdateResult.ManifestDownloadFailed)
        val timestamps = FakeTimestampStore()
        val coordinator = coordinator(checker, timestamps)

        assertEquals(
            VehicleCatalogAutomaticCheckResult.Completed(VehicleCatalogUpdateResult.ManifestDownloadFailed),
            coordinator.checkAutomaticallyIfDue()
        )
        assertEquals(NOW, timestamps.lastCheck)
        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
        assertEquals(1, checker.checkCalls.get())
    }

    @Test
    fun manualCheck_ignoresTheThrottleAndRecordsItsCompletion() = runBlocking {
        val checker = FakeChecker()
        val timestamps = FakeTimestampStore(NOW)
        val coordinator = coordinator(checker, timestamps)

        assertEquals(VehicleCatalogUpdateResult.UpToDate(1), coordinator.checkForUpdate())
        assertEquals(1, checker.checkCalls.get())
        assertEquals(NOW, timestamps.lastCheck)
        assertEquals(VehicleCatalogAutomaticCheckResult.NotDue, coordinator.checkAutomaticallyIfDue())
    }

    @Test
    fun concurrentAutomaticChecks_produceOnlyOneRemoteCheck() = runBlocking {
        val checker = BlockingChecker()
        val coordinator = coordinator(checker, FakeTimestampStore())

        val first = async(Dispatchers.Default) { coordinator.checkAutomaticallyIfDue() }
        assertTrue(checker.started.await(2, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) { coordinator.checkAutomaticallyIfDue() }
        checker.release.countDown()

        val results = awaitAll(first, second)
        assertEquals(1, checker.checkCalls.get())
        assertEquals(1, results.count { it is VehicleCatalogAutomaticCheckResult.Completed })
        assertEquals(1, results.count { it is VehicleCatalogAutomaticCheckResult.NotDue })
    }

    @Test
    fun concurrentAutomaticAndManualChecks_areSerializedSafely() = runBlocking {
        val checker = BlockingChecker()
        val coordinator = coordinator(checker, FakeTimestampStore())

        val automatic = async(Dispatchers.Default) { coordinator.checkAutomaticallyIfDue() }
        assertTrue(checker.started.await(2, TimeUnit.SECONDS))
        val manual = async(Dispatchers.Default) { coordinator.checkForUpdate() }
        checker.release.countDown()

        assertTrue(automatic.await() is VehicleCatalogAutomaticCheckResult.Completed)
        assertEquals(VehicleCatalogUpdateResult.UpToDate(1), manual.await())
        assertEquals(2, checker.checkCalls.get())
    }

    @Test
    fun cancelledAutomaticCheck_doesNotRecordAnIncompleteAttempt() = runBlocking {
        val checker = BlockingChecker()
        val timestamps = FakeTimestampStore()
        val coordinator = coordinator(checker, timestamps)

        val automatic = async(Dispatchers.Default) { coordinator.checkAutomaticallyIfDue() }
        assertTrue(checker.started.await(2, TimeUnit.SECONDS))
        automatic.cancel()
        checker.release.countDown()
        automatic.cancelAndJoin()

        assertEquals(null, timestamps.lastCheck)
    }

    private fun coordinator(
        checker: VehicleCatalogUpdateChecker,
        timestamps: FakeTimestampStore
    ): VehicleCatalogUpdateCoordinator = VehicleCatalogUpdateCoordinator(
        updateChecker = checker,
        timestampStore = timestamps,
        currentTimeMillis = { NOW }
    )

    private class FakeTimestampStore(
        var lastCheck: Long? = null
    ) : VehicleCatalogUpdateTimestampStore {
        var readError: Exception? = null
        var writeError: Exception? = null
        override suspend fun lastCatalogCheck(): Long? {
            readError?.let { throw it }
            return lastCheck
        }

        override suspend fun saveLastCatalogCheck(epochMillis: Long) {
            writeError?.let { throw it }
            lastCheck = epochMillis
        }
    }

    private open class FakeChecker(
        private val result: VehicleCatalogUpdateResult = VehicleCatalogUpdateResult.UpToDate(1)
    ) : VehicleCatalogUpdateChecker {
        val checkCalls = AtomicInteger(0)

        override fun activeCatalogVersion(): Int = 1

        override suspend fun checkForUpdate(): VehicleCatalogUpdateResult {
            checkCalls.incrementAndGet()
            return result
        }
    }

    private class BlockingChecker : FakeChecker() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        override suspend fun checkForUpdate(): VehicleCatalogUpdateResult {
            checkCalls.incrementAndGet()
            started.countDown()
            check(withContext(Dispatchers.IO) { release.await(2, TimeUnit.SECONDS) }) {
                "Timed out waiting to release update check"
            }
            return VehicleCatalogUpdateResult.UpToDate(1)
        }
    }

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
