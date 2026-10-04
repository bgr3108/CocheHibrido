package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class StationLocationFreshnessTest {
    private val now = 1_000.minutes.inWholeNanoseconds

    @Test
    fun recentLocation_isReused() {
        assertTrue(isLastKnownLocationFresh(now - 1.minutes.inWholeNanoseconds, now))
        assertEquals(
            StationCoordinates(28.123, -15.432),
            selectFreshLastKnownLocation(
                listOf(candidate(28.123, -15.432, now - 1.minutes.inWholeNanoseconds)),
                now
            )
        )
    }

    @Test
    fun locationAtFreshnessBoundary_isReused() {
        assertTrue(isLastKnownLocationFresh(now - MAX_LAST_KNOWN_LOCATION_AGE.inWholeNanoseconds, now))
    }

    @Test
    fun locationJustPastFreshnessBoundary_isNotReused() {
        assertFalse(isLastKnownLocationFresh(now - MAX_LAST_KNOWN_LOCATION_AGE.inWholeNanoseconds - 1L, now))
    }

    @Test
    fun staleLocation_isNotReturnedSoTheProviderCanRequestANewFix() {
        assertNull(
            selectFreshLastKnownLocation(
                listOf(candidate(28.123, -15.432, now - 3.minutes.inWholeNanoseconds)),
                now
            )
        )
    }

    @Test
    fun locationsSeveralHoursOrDaysOld_areNotReused() {
        assertFalse(isLastKnownLocationFresh(now - 6.hours.inWholeNanoseconds, now))
        assertFalse(isLastKnownLocationFresh(now - 2.days.inWholeNanoseconds, now))
    }

    @Test
    fun invalidOrInconsistentMonotonicTimestamp_isNeverReused() {
        assertFalse(isLastKnownLocationFresh(0L, now))
        assertFalse(isLastKnownLocationFresh(now, now - 1L))
    }

    @Test
    fun freshestCandidateWinsAndAccuracyBreaksEqualTimestampTie() {
        val fresh = candidate(28.100, -15.400, now - 30.secondsAsNanos(), accuracy = 100f)
        val olderButMoreAccurate = candidate(28.200, -15.500, now - 1.minutes.inWholeNanoseconds, accuracy = 5f)
        val equallyFreshAndMoreAccurate = candidate(28.300, -15.600, now - 30.secondsAsNanos(), accuracy = 10f)

        assertEquals(
            equallyFreshAndMoreAccurate.coordinates,
            selectFreshLastKnownLocation(listOf(fresh, olderButMoreAccurate, equallyFreshAndMoreAccurate), now)
        )
    }

    @Test
    fun noLastKnownLocation_leavesTheProviderToRequestANewFix() {
        assertNull(selectFreshLastKnownLocation(emptyList(), nowElapsedRealtimeNanos = now))
    }

    @Test
    fun freshLastKnownLocation_isReusedWithoutRequestingAnotherFix() = runBlocking {
        var currentFixRequested = false
        val fresh = StationCoordinates(28.123, -15.432)

        val result = resolveStationLocation(fresh) {
            currentFixRequested = true
            null
        }

        assertEquals(fresh, result)
        assertFalse(currentFixRequested)
    }

    @Test
    fun staleLocation_requestsANewFixAndDoesNotFallBackWhenThatRequestFails() = runBlocking {
        val stale = selectFreshLastKnownLocation(
            listOf(candidate(28.123, -15.432, now - 3.minutes.inWholeNanoseconds)),
            now
        )
        var currentFixRequested = false

        val result = resolveStationLocation(stale) {
            currentFixRequested = true
            null
        }

        assertTrue(currentFixRequested)
        assertNull(result)
    }

    private fun candidate(
        latitude: Double,
        longitude: Double,
        elapsedRealtimeNanos: Long,
        accuracy: Float = 20f
    ) = LastKnownStationLocation(
        coordinates = StationCoordinates(latitude, longitude),
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        accuracyMeters = accuracy
    )

    private fun Int.secondsAsNanos(): Long = this.toLong() * 1_000_000_000L
}
