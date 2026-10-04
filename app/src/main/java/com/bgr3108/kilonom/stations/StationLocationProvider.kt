package com.bgr3108.kilonom.stations

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine

object StationLocationProvider {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Android does not expose a dedicated permanent-denial result. Once a request has been made,
     * the absence of both rationale prompts means the user must change it in system settings.
     */
    fun isPermissionPermanentlyDenied(context: Context, wasRequested: Boolean): Boolean {
        val activity = context.findActivity() ?: return false
        return wasRequested && !hasPermission(context) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun createAppLocationSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )

    /**
     * Requests one current position only after an explicit user action. The result is intentionally
     * not persisted: it is used solely to sort and display approximate station distances.
     */
    suspend fun requestCurrentLocation(context: Context): StationCoordinates? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val freshLastKnown = lastKnownLocation(context, manager, fineGranted)
        return resolveStationLocation(
            freshLastKnown = freshLastKnown
        ) {
            val providers = locationProviderOrder(
                hasFinePermission = fineGranted,
                networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
                gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            )
            providers.firstNotNullOfOrNull { provider ->
                requestSingleLocation(context, manager, provider)
            }
        }
    }

    private suspend fun requestSingleLocation(
        context: Context,
        manager: LocationManager,
        provider: String
    ): StationCoordinates? = withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT) {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted && !fineGranted) return@withTimeoutOrNull null
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(
                            location?.let { StationCoordinates(it.latitude, it.longitude) }
                                ?.takeIf { it.isValid() }
                        )
                    }
                }
            } catch (_: SecurityException) {
                // The user may revoke a runtime grant after the check above.
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun lastKnownLocation(
        context: Context,
        manager: LocationManager,
        fineGranted: Boolean
    ): StationCoordinates? {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted && !fineGranted) return null
        val nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        // A provider can be disabled now yet still hold a recent, valid last fix. Provider state
        // only limits the subsequent current-location request, not reuse of a fresh reading.
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (fineGranted) add(LocationManager.GPS_PROVIDER)
        }
        return selectFreshLastKnownLocation(
            candidates = providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()?.let { location ->
                    LastKnownStationLocation(
                        coordinates = StationCoordinates(location.latitude, location.longitude),
                        elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                        accuracyMeters = location.accuracy
                    )
                }
            },
            nowElapsedRealtimeNanos = nowElapsedRealtimeNanos
        )
    }

    private val LOCATION_REQUEST_TIMEOUT = 8.seconds
}

/**
 * A nearby search starts at a 10 km radius, so a two-minute fix is recent enough to avoid an
 * unnecessary request while remaining conservative for a user who may have travelled by car.
 */
internal val MAX_LAST_KNOWN_LOCATION_AGE: Duration = 2.minutes

/** A monotonic timestamp avoids reusing a reading incorrectly after a wall-clock change. */
internal fun isLastKnownLocationFresh(
    locationElapsedRealtimeNanos: Long,
    nowElapsedRealtimeNanos: Long,
    maximumAge: Duration = MAX_LAST_KNOWN_LOCATION_AGE
): Boolean {
    if (locationElapsedRealtimeNanos <= 0L || nowElapsedRealtimeNanos <= 0L) return false
    if (nowElapsedRealtimeNanos < locationElapsedRealtimeNanos) return false
    return nowElapsedRealtimeNanos - locationElapsedRealtimeNanos <= maximumAge.inWholeNanoseconds
}

internal data class LastKnownStationLocation(
    val coordinates: StationCoordinates,
    val elapsedRealtimeNanos: Long,
    val accuracyMeters: Float
)

/**
 * Prefer the freshest valid reading. Accuracy only breaks a tie, so an older GPS fix cannot win
 * over a newer network location. Invalid or stale timestamps deliberately return no fallback.
 */
internal fun selectFreshLastKnownLocation(
    candidates: List<LastKnownStationLocation>,
    nowElapsedRealtimeNanos: Long
): StationCoordinates? = candidates
    .asSequence()
    .filter { it.coordinates.isValid() }
    .filter { isLastKnownLocationFresh(it.elapsedRealtimeNanos, nowElapsedRealtimeNanos) }
    .sortedWith(
        compareByDescending<LastKnownStationLocation> { it.elapsedRealtimeNanos }
            .thenBy { it.accuracyMeters.takeIf { accuracy -> accuracy.isFinite() && accuracy >= 0f } ?: Float.POSITIVE_INFINITY }
    )
    .map(LastKnownStationLocation::coordinates)
    .firstOrNull()

/** A stale reading is never a fallback: the current one-shot request decides the final result. */
internal suspend fun resolveStationLocation(
    freshLastKnown: StationCoordinates?,
    requestCurrentLocation: suspend () -> StationCoordinates?
): StationCoordinates? = freshLastKnown ?: requestCurrentLocation()

/** Coarse permission can use network location; GPS is attempted only with fine permission. */
internal fun locationProviderOrder(
    hasFinePermission: Boolean,
    networkEnabled: Boolean,
    gpsEnabled: Boolean
): List<String> = buildList {
    if (networkEnabled) add(LocationManager.NETWORK_PROVIDER)
    if (hasFinePermission && gpsEnabled) add(LocationManager.GPS_PROVIDER)
}

/** Both approximate and precise grants allow the feature to continue after the system dialog. */
internal fun locationPermissionGranted(permissions: Map<String, Boolean>): Boolean =
    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
