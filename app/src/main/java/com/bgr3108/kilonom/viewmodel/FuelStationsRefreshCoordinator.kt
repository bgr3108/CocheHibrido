package com.bgr3108.kilonom.viewmodel

import kotlinx.coroutines.sync.Mutex

/** Per-source coordination: cache age for automatic refresh, in-memory cooldown after failure. */
internal class StationsRefreshCoordinator<T>(
    private val metadataProvider: suspend () -> T?,
    private val refresh: suspend () -> Result<*>?,
    private val isStale: (T?) -> Boolean,
    private val elapsedRealtime: () -> Long = android.os.SystemClock::elapsedRealtime
) {
    private var stationEntryHandled = false
    private val mutex = Mutex()
    private var lastAutomaticFailure: Long? = null
    private var refreshInProgress = false
    private var refreshGeneration = 0L

    suspend fun refreshIfNeededOnStationsOpened() {
        if (stationEntryHandled) return
        stationEntryHandled = true
        refreshAutomaticallyIfNeeded()
    }

    suspend fun refreshAutomaticallyIfNeeded() = refreshIfAllowed(manual = false)

    suspend fun refreshManually() = refreshIfAllowed(manual = true)

    /** Cache deletion shares the refresh lock; it never runs during an active replacement. */
    suspend fun clearCache(clear: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        try {
            clear()
            return true
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun refreshIfAllowed(manual: Boolean) {
        val generation = refreshGeneration
        if (!mutex.tryLock()) {
            // Manual actions may wait for a local metadata check, never for an active download.
            if (!manual || refreshInProgress) return
            mutex.lock()
        }
        try {
            if (generation != refreshGeneration) return
            if (!manual) {
                val sinceFailure = lastAutomaticFailure?.let { elapsedRealtime() - it }
                if (sinceFailure != null && sinceFailure in 0 until FAILURE_COOLDOWN_MILLIS) return
                if (!isStale(metadataProvider())) return
            }
            refreshInProgress = true
            refreshGeneration++
            val result = refresh() ?: return
            if (result.isSuccess) lastAutomaticFailure = null
            else if (!manual) lastAutomaticFailure = elapsedRealtime()
        } finally {
            refreshInProgress = false
            mutex.unlock()
        }
    }

    companion object {
        const val FAILURE_COOLDOWN_MILLIS = 10L * 60L * 1000L
    }
}
