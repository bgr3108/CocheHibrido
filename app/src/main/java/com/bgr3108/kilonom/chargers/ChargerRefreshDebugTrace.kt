package com.bgr3108.kilonom.chargers

import android.os.SystemClock
import android.util.Log
import com.bgr3108.kilonom.BuildConfig

/** DEBUG-only timing for one RIPREE refresh. It deliberately has no effect on release behavior. */
class ChargerRefreshDebugTrace private constructor() {
    private val startedAt = SystemClock.elapsedRealtime()
    private var phase = "start"
    private var responseAt: Long? = null
    private var parseStartedAt: Long? = null
    private var summaryStartedAt: Long? = null
    private var roomStartedAt: Long? = null
    var lastDownloadedBytes: Long? = null
        private set

    fun httpStarted() {
        phase = "http-start"
        Log.d(TAG, "http-start")
    }

    fun responseAvailable(
        statusCode: Int,
        contentLength: Long,
        contentType: String?,
        contentEncoding: String?,
        transferEncoding: String?
    ) {
        responseAt = now()
        phase = "http-response"
        Log.d(
            TAG,
            "response status=$statusCode requestMs=${elapsed(startedAt)} contentLength=$contentLength " +
                "contentType=${contentType ?: "none"} contentEncoding=${contentEncoding ?: "none"} " +
                "transferEncoding=${transferEncoding ?: "none"}"
        )
    }

    fun parseStarted() {
        parseStartedAt = now()
        phase = "parse-map-deduplicate"
    }

    fun parseCompleted(rows: Int, discardedRows: Int, installations: Int, connectors: Int) {
        Log.d(
            TAG,
            "parse rows=$rows discarded=$discardedRows installations=$installations connectors=$connectors " +
                "parseMs=${elapsed(parseStartedAt)}"
        )
    }

    fun streamConsumed(bytes: Long, inputReadMs: Long) {
        lastDownloadedBytes = bytes
        val parseMs = elapsed(parseStartedAt)
        Log.d(
            TAG,
            "body bytes=$bytes streamReadMs=$inputReadMs parseCpuEstimateMs=${(parseMs - inputReadMs).coerceAtLeast(0)} " +
                "responseToEndMs=${elapsed(responseAt)} totalDownloadAndParseMs=${elapsed(startedAt)}"
        )
    }

    fun summaryStarted() {
        summaryStartedAt = now()
        phase = "installation-summary"
    }

    fun summaryCompleted(installations: Int, connectors: Int) {
        Log.d(
            TAG,
            "summary installations=$installations connectors=$connectors summaryMs=${elapsed(summaryStartedAt)}"
        )
    }

    fun roomStarted() {
        roomStartedAt = now()
        phase = "room-replace"
    }

    fun roomStep(name: String, startedAt: Long, details: String = "") {
        Log.d(TAG, "room $name ms=${elapsed(startedAt)}$details")
    }

    fun roomCompleted() {
        Log.d(TAG, "room totalMs=${elapsed(roomStartedAt)}")
    }

    fun completed(installations: Int, connectors: Int, bytes: Long?) {
        Log.d(
            TAG,
            "completed totalMs=${elapsed(startedAt)} installations=$installations connectors=$connectors " +
                "bytes=${bytes ?: "unknown"}"
        )
    }

    fun failed(error: Throwable) {
        Log.d(TAG, "failed phase=$phase totalMs=${elapsed(startedAt)} error=${error.javaClass.simpleName}")
    }

    fun now(): Long = SystemClock.elapsedRealtime()

    private fun elapsed(from: Long?): Long = from?.let { now() - it } ?: 0L

    companion object {
        const val TAG = "KilonomChargerRefresh"

        fun create(): ChargerRefreshDebugTrace? = if (BuildConfig.DEBUG) ChargerRefreshDebugTrace() else null
    }
}
