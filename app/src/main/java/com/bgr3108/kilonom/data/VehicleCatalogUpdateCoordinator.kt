package com.bgr3108.kilonom.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.catalogUpdateDataStore by preferencesDataStore(
    name = "catalog_update_preferences"
)

internal const val AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

internal interface VehicleCatalogUpdateTimestampStore {
    suspend fun lastCatalogCheck(): Long?
    suspend fun saveLastCatalogCheck(epochMillis: Long)
}

internal class VehicleCatalogUpdatePreferences(
    private val context: Context
) : VehicleCatalogUpdateTimestampStore {
    private object Keys {
        val LAST_CATALOG_CHECK = longPreferencesKey("last_catalog_check")
    }

    override suspend fun lastCatalogCheck(): Long? =
        context.catalogUpdateDataStore.data.first()[Keys.LAST_CATALOG_CHECK]

    override suspend fun saveLastCatalogCheck(epochMillis: Long) {
        context.catalogUpdateDataStore.edit { preferences ->
            preferences[Keys.LAST_CATALOG_CHECK] = epochMillis
        }
    }
}

internal sealed interface VehicleCatalogAutomaticCheckResult {
    data object NotDue : VehicleCatalogAutomaticCheckResult
    data class Completed(val updateResult: VehicleCatalogUpdateResult) : VehicleCatalogAutomaticCheckResult
    data object Failed : VehicleCatalogAutomaticCheckResult
}

/** Coordinates when catalog checks run without changing the updater's network/install duties. */
internal class VehicleCatalogUpdateCoordinator(
    private val updateChecker: VehicleCatalogUpdateChecker,
    private val timestampStore: VehicleCatalogUpdateTimestampStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : VehicleCatalogUpdateChecker {
    private val checkMutex = Mutex()
    private var lastCompletedCheck: Long? = null

    override fun activeCatalogVersion(): Int = updateChecker.activeCatalogVersion()

    /** Explicit user checks always run, regardless of the automatic-check interval. */
    override suspend fun checkForUpdate(): VehicleCatalogUpdateResult = checkMutex.withLock {
        completeCheckAndRemember()
    }

    /** Performs a silent check only when the persisted 24-hour interval has elapsed. */
    suspend fun checkAutomaticallyIfDue(): VehicleCatalogAutomaticCheckResult = checkMutex.withLock {
        val now = currentTimeMillis()
        if (lastCompletedCheck?.let { !isAutomaticCatalogCheckDue(it, now) } == true) {
            return@withLock VehicleCatalogAutomaticCheckResult.NotDue
        }
        val lastCheck = try {
            timestampStore.lastCatalogCheck()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            return@withLock VehicleCatalogAutomaticCheckResult.Failed
        }
        if (!isAutomaticCatalogCheckDue(lastCheck, now)) {
            return@withLock VehicleCatalogAutomaticCheckResult.NotDue
        }

        try {
            VehicleCatalogAutomaticCheckResult.Completed(completeCheckAndRemember())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            VehicleCatalogAutomaticCheckResult.Failed
        }
    }

    private suspend fun completeCheckAndRemember(): VehicleCatalogUpdateResult {
        val result = updateChecker.checkForUpdate()
        currentCoroutineContext().ensureActive()
        val completedAt = currentTimeMillis()
        try {
            timestampStore.saveLastCatalogCheck(completedAt)
        } catch (_: IOException) {
            // Auxiliary metadata must not replace an already completed update result.
            // Keep the interval in memory; a later process can safely try again.
        }
        currentCoroutineContext().ensureActive()
        lastCompletedCheck = completedAt
        return result
    }
}

internal fun isAutomaticCatalogCheckDue(lastCheck: Long?, now: Long): Boolean =
    lastCheck == null ||
        lastCheck < 0L ||
        now < 0L ||
        lastCheck > now ||
        now - lastCheck >= AUTOMATIC_CATALOG_CHECK_INTERVAL_MILLIS
