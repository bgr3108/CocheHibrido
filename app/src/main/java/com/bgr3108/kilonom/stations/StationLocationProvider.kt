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
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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
        lastKnownLocation(context, manager)?.let { return it }
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val providers = locationProviderOrder(
            hasFinePermission = fineGranted,
            networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER),
            gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        )
        return providers.firstNotNullOfOrNull { provider ->
            requestSingleLocation(context, manager, provider)
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

    private fun lastKnownLocation(context: Context, manager: LocationManager): StationCoordinates? {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!coarseGranted && !fineGranted) return null
        return runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { location -> location.time }
                ?.let { StationCoordinates(it.latitude, it.longitude) }
                ?.takeIf { it.isValid() }
        }.getOrNull()
    }

    private val LOCATION_REQUEST_TIMEOUT = 8.seconds
}

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
