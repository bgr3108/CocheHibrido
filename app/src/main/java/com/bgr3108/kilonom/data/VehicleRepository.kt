package com.bgr3108.kilonom.data

import android.database.sqlite.SQLiteException
import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.domain.calculateVehicleCurrentKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

sealed interface VehicleLoadState {
    data object Loading : VehicleLoadState
    data object Empty : VehicleLoadState
    data class Ready(val vehicle: VehicleEntity) : VehicleLoadState
    data class Error(val cause: Exception) : VehicleLoadState
}

internal const val RELEASE_NOTES_VERSION = "1.5.0"

internal fun shouldShowReleaseNotes(
    hasExistingInstallation: Boolean,
    lastSeenReleaseNotesVersion: String?,
    currentReleaseNotesVersion: String
): Boolean = hasExistingInstallation && lastSeenReleaseNotesVersion != currentReleaseNotesVersion

/**
 * Resolves a single Room-backed active vehicle. Consumers must derive all vehicle-scoped data
 * from [activeVehicle] so a vehicle and its entries never originate from different contexts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleRepository(
    val vehicleDataSource: VehicleCatalog,
    private val vehiclePreferences: VehiclePreferencesStore,
    private val vehicleDao: VehicleDao,
    private val fuelEntryDao: FuelEntryDao,
    private val maintenanceDao: MaintenanceDao,
    repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clearPersonalRoomData: suspend () -> Unit = { vehicleDao.deleteAll() }
) {

    data class VehicleSummary(
        val vehicle: VehicleEntity,
        val currentKm: Double,
        val entryCount: Int,
        val maintenanceRecordCount: Int,
        val minimumValidRecordedKm: Double?
    )

    private val _vehicle = MutableStateFlow(Vehicle())
    private val _activeVehicleId = MutableStateFlow<Long?>(null)
    private val _loadState = MutableStateFlow<VehicleLoadState>(VehicleLoadState.Loading)
    private val loadRequests = MutableStateFlow(0L)
    private val observationFailure = MutableStateFlow<Exception?>(null)
    private val _activeVehicle = MutableStateFlow<VehicleEntity?>(null)
    private val _vehicleSummaries = MutableStateFlow<List<VehicleSummary>>(emptyList())
    private val _showReleaseNotes = MutableStateFlow(false)
    private val releaseNotesMutex = Mutex()
    private val vehicleMutex = Mutex()

    val loadState: StateFlow<VehicleLoadState> = _loadState.asStateFlow()
    /** Compatibility views; the application shell consumes the authoritative [loadState]. */
    val isLoading: StateFlow<Boolean> = loadState.map { it is VehicleLoadState.Loading }
        .stateIn(repositoryScope, SharingStarted.Eagerly, true)
    val activeVehicleId: StateFlow<Long?> = _activeVehicleId
    val activeVehicle: StateFlow<VehicleEntity?> = _activeVehicle.asStateFlow()
    /** Compatibility snapshot; active data consumers use [activeVehicle] instead. */
    val vehicle: StateFlow<Vehicle> = _vehicle
    val showReleaseNotes: StateFlow<Boolean> = _showReleaseNotes

    /** All Room snapshots with their own derived kilometre readings and entry counts. */
    val vehicleSummaries: StateFlow<List<VehicleSummary>> = _vehicleSummaries.asStateFlow()

    private fun observeVehicleSummaries() = vehicleDao.observeAllVehicles()
        .flatMapLatest { vehicles ->
            if (vehicles.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(vehicles.map { entity ->
                    combine(
                        fuelEntryDao.observeEntries(entity.id),
                        maintenanceDao.observeOdometerKilometersForVehicle(entity.id),
                        maintenanceDao.observeRecordCountForVehicle(entity.id)
                    ) { entries, maintenanceKilometers, maintenanceRecordCount ->
                        val recordedKilometers = entries.map { it.km } + maintenanceKilometers.map(Long::toDouble)
                        VehicleSummary(
                            vehicle = entity,
                            currentKm = calculateVehicleCurrentKm(
                                initialKm = entity.initialKm,
                                entryKilometers = recordedKilometers
                            ),
                            entryCount = entries.size,
                            maintenanceRecordCount = maintenanceRecordCount,
                            minimumValidRecordedKm = recordedKilometers
                                .filter { it.isFinite() && it >= 0.0 }
                                .minOrNull()
                        )
                    }
                }) { summaries -> summaries.toList() }
            }
        }

    init {
        repositoryScope.launch {
            loadRequests.collectLatest {
                _loadState.value = VehicleLoadState.Loading
                observationFailure.value = null
                try {
                    bootstrapLegacyVehicle()
                    combine(
                        _activeVehicleId.flatMapLatest { id -> id?.let(vehicleDao::observeVehicle) ?: flowOf(null) },
                        observeVehicleSummaries(),
                        observationFailure
                    ) { active, summaries, error ->
                        error?.let { throw it }
                        active to summaries
                    }.collect { (active, summaries) ->
                        // A vehicle switch can invalidate the two Room observations separately.
                        if (active?.id != _activeVehicleId.value) return@collect
                        if (active != null && summaries.none { it.vehicle == active }) return@collect
                        publishLoadedVehicles(active, summaries)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    _loadState.value = VehicleLoadState.Error(error)
                } catch (error: SQLiteException) {
                    _loadState.value = VehicleLoadState.Error(error)
                }
            }
        }
    }

    /** Restarts both bootstrap and Room observations after a recoverable storage failure. */
    fun retryVehicleLoad() {
        if (_loadState.value !is VehicleLoadState.Error) return
        _loadState.value = VehicleLoadState.Loading
        loadRequests.update { it + 1L }
    }

    /** Associated Room reads share the same error/retry lifecycle without emitting false empty data. */
    internal fun <T> observeLoadedVehicle(read: (VehicleEntity?) -> Flow<T>): Flow<T> =
        loadState.flatMapLatest { state ->
            when (state) {
                is VehicleLoadState.Ready -> guardedVehicleRead(state.vehicle, read)
                VehicleLoadState.Empty -> guardedVehicleRead(null, read)
                VehicleLoadState.Loading, is VehicleLoadState.Error -> emptyFlow()
            }
        }

    private fun <T> guardedVehicleRead(vehicle: VehicleEntity?, read: (VehicleEntity?) -> Flow<T>): Flow<T> =
        flow { emitAll(read(vehicle)) }.catch { error ->
            currentCoroutineContext().ensureActive()
            when (error) {
                is IOException -> observationFailure.value = error
                is SQLiteException -> observationFailure.value = error
                else -> throw error
            }
        }

    private fun publishLoadedVehicles(active: VehicleEntity?, summaries: List<VehicleSummary>) {
        _activeVehicle.value = active
        _vehicle.value = active?.toVehicle() ?: Vehicle()
        _vehicleSummaries.value = summaries
        _loadState.value = active?.let { VehicleLoadState.Ready(it) } ?: VehicleLoadState.Empty
    }

    /**
     * Reconciles the legacy DataStore snapshot with Room. Re-running it updates the recovery
     * anchor rather than creating another vehicle, so an interrupted bootstrap stays safe.
     */
    private suspend fun bootstrapLegacyVehicle() = vehicleMutex.withLock {
        // Complete every initial read before importing/enriching legacy data or writing preferences.
        // A failed read leaves the previous published snapshot intact and blocks the application shell.
        val firstVehicle = vehicleDao.getFirstVehicle()
        val summaries = observeVehicleSummaries().first()
        val legacyVehicle = vehiclePreferences.loadVehicle()
        val configuredLegacyVehicle = legacyVehicle.takeIf { it.isConfigured() }
        val storedActiveId = vehiclePreferences.loadActiveVehicleId()
        val storedActiveVehicle = storedActiveId?.let { vehicleDao.getVehicle(it) }
        val existingActive = storedActiveVehicle ?: firstVehicle
        existingActive?.let { vehicleDao.observeVehicle(it.id).first() }
        val hasExistingInstallation = vehiclePreferences.hasStoredAppState() || firstVehicle != null
        val lastSeenVersion = vehiclePreferences.lastSeenReleaseNotesVersion()
        currentCoroutineContext().ensureActive()

        val activeVehicle = when {
            storedActiveVehicle != null -> {
                if (configuredLegacyVehicle != null && storedActiveVehicle.isRecoveryVehicle()) {
                    val enriched = configuredLegacyVehicle.toEntity(
                        id = storedActiveVehicle.id,
                        createdAt = storedActiveVehicle.createdAt
                    )
                    vehicleDao.update(enriched)
                    enriched
                } else {
                    storedActiveVehicle
                }
            }

            firstVehicle != null -> {
                if (configuredLegacyVehicle != null && firstVehicle.isRecoveryVehicle()) {
                    val enriched = configuredLegacyVehicle.toEntity(
                        id = firstVehicle.id,
                        createdAt = firstVehicle.createdAt
                    )
                    vehicleDao.update(enriched)
                    enriched
                } else {
                    firstVehicle
                }
            }

            configuredLegacyVehicle != null -> {
                val candidate = configuredLegacyVehicle.toEntity(createdAt = System.currentTimeMillis())
                candidate.copy(id = vehicleDao.insert(candidate))
            }

            else -> null
        }

        activeVehicle?.let { vehiclePreferences.saveActiveVehicleId(it.id) }
        if (!hasExistingInstallation) vehiclePreferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
        _showReleaseNotes.value = shouldShowReleaseNotes(
            hasExistingInstallation, lastSeenVersion, RELEASE_NOTES_VERSION
        )
        _activeVehicleId.value = activeVehicle?.id
        val loadedSummaries = summaries.map { summary ->
            if (summary.vehicle.id == activeVehicle?.id) {
                summary.copy(vehicle = activeVehicle, currentKm = maxOf(summary.currentKm, activeVehicle.initialKm))
            } else summary
        }
        publishLoadedVehicles(activeVehicle, if (activeVehicle != null && loadedSummaries.none { it.vehicle.id == activeVehicle.id }) {
            loadedSummaries + VehicleSummary(activeVehicle, activeVehicle.initialKm, 0, 0, null)
        } else loadedSummaries)
    }

    /** Saves the legacy snapshot as a safety net and then commits the Room vehicle as the active one. */
    suspend fun saveVehicle(vehicle: Vehicle) = vehicleMutex.withLock {
        vehiclePreferences.saveVehicle(vehicle)

        val activeVehicle = _activeVehicleId.value?.let { vehicleDao.getVehicle(it) }
        val entity = if (activeVehicle != null) {
            val updated = vehicle.toEntity(
                id = activeVehicle.id,
                createdAt = activeVehicle.createdAt
            )
            vehicleDao.update(updated)
            updated
        } else {
            val id = vehicleDao.insert(vehicle.toEntity(createdAt = System.currentTimeMillis()))
            vehicleDao.getVehicle(id) ?: error("No se pudo recuperar el vehículo creado")
        }

        vehiclePreferences.saveActiveVehicleId(entity.id)
        _activeVehicleId.value = entity.id
        _vehicle.value = entity.toVehicle()
    }

    /** Resolves a requested id through the same deterministic fallback used at bootstrap. */
    suspend fun selectActiveVehicle(requestedId: Long?) = vehicleMutex.withLock {
        val resolved = requestedId?.let { id -> vehicleDao.getVehicle(id) } ?: vehicleDao.getFirstVehicle()
        activateVehicle(resolved)
    }

    /** Activation retries must select the saved id, never silently fall back to another vehicle. */
    suspend fun activateCreatedVehicle(vehicleId: Long): Boolean = vehicleMutex.withLock {
        val existing = vehicleDao.getVehicle(vehicleId) ?: return@withLock false
        activateVehicle(existing)
        true
    }

    private suspend fun activateVehicle(resolved: VehicleEntity?) {
        if (resolved != null) {
            vehiclePreferences.saveActiveVehicleId(resolved.id)
        } else {
            vehiclePreferences.clearActiveVehicleId()
        }
        currentCoroutineContext().ensureActive()
        _activeVehicleId.value = resolved?.id
        _vehicle.value = resolved?.toVehicle() ?: Vehicle()
    }

    /** Acknowledges the Room commit separately, before attempting to persist the active selection. */
    suspend fun createVehicle(
        vehicle: Vehicle,
        onStored: (VehicleEntity) -> Unit = {}
    ): VehicleEntity = vehicleMutex.withLock {
        currentCoroutineContext().ensureActive()
        val candidate = vehicle.copy(id = null).toEntity(createdAt = System.currentTimeMillis())
        val entity = withContext(NonCancellable) {
            // Only the local INSERT and its acknowledgement are non-cancellable: retain the real
            // id before cancellation can interrupt activation. Waiting for the mutex is cancellable.
            candidate.copy(id = vehicleDao.insert(candidate)).also(onStored)
        }
        currentCoroutineContext().ensureActive()
        activateVehicle(entity)
        entity
    }

    /**
     * Updates a snapshot only when its initial mileage remains compatible with its history.
     * Vehicles with consumption or maintenance history keep their structural metadata unchanged.
     */
    suspend fun updateVehicle(vehicleId: Long, updatedVehicle: Vehicle): VehicleEntity = vehicleMutex.withLock {
        val current = vehicleDao.getVehicle(vehicleId) ?: error("Vehículo no encontrado")
        val entryKilometers = fuelEntryDao.getKilometersForVehicle(vehicleId)
        val maintenanceKilometers = maintenanceDao.getOdometerKilometersForVehicle(vehicleId)
        val minimumRecordedKm = (entryKilometers + maintenanceKilometers.map(Long::toDouble))
            .filter { it.isFinite() && it >= 0.0 }
            .minOrNull()
        require(updatedVehicle.initialKm.isFinite() && updatedVehicle.initialKm >= 0.0) {
            "El kilometraje inicial no es válido"
        }
        require(minimumRecordedKm == null || updatedVehicle.initialKm <= minimumRecordedKm) {
            "El kilometraje inicial no puede superar el primer kilometraje registrado"
        }
        val hasRecordedActivity = fuelEntryDao.countEntries(vehicleId) > 0 ||
            maintenanceDao.countRecordsForVehicle(vehicleId) > 0 ||
            maintenanceDao.countItemsForVehicle(vehicleId) > 0
        val entity = if (hasRecordedActivity) {
            require(updatedVehicle.type == current.type) {
                "No se puede cambiar la propulsión de un vehículo con datos registrados"
            }
            require(updatedVehicle.category == current.category) {
                "No se puede cambiar el tipo de vehículo con datos registrados"
            }
            current.copy(initialKm = updatedVehicle.initialKm)
        } else {
            updatedVehicle.toEntity(id = current.id, createdAt = current.createdAt)
        }
        vehicleDao.update(entity)
        if (_activeVehicleId.value == vehicleId) _vehicle.value = entity.toVehicle()
        entity
    }

    /** Returns a conservative, read-only suggestion for an old snapshot with no provenance. */
    fun reconcileLegacyVehicle(vehicle: VehicleEntity): LegacyCatalogReconciliation =
        if (vehicle.catalogId == null) vehicleDataSource.reconcileLegacyVehicle(vehicle)
        else LegacyCatalogReconciliation.NoMatch

    /**
     * Persists only a catalog provenance after verifying the exact match again under the mutex.
     * No field of the personal snapshot is reconstructed from the catalog.
     */
    suspend fun linkLegacyVehicleToCatalog(vehicleId: Long, catalogId: String): Boolean = vehicleMutex.withLock {
        val current = vehicleDao.getVehicle(vehicleId) ?: return@withLock false
        val resolution = vehicleDataSource.reconcileLegacyVehicle(current)
        if (resolution !is LegacyCatalogReconciliation.ExactMatch || resolution.catalogId != catalogId) {
            return@withLock false
        }
        val linked = vehicleDao.setCatalogIdIfMissing(vehicleId, catalogId) == 1
        if (linked && _activeVehicleId.value == vehicleId) {
            _vehicle.value = current.copy(catalogId = catalogId).toVehicle()
        }
        linked
    }

    /** Deletes one snapshot; Room cascades its associated entries and maintenance history. */
    suspend fun deleteVehicle(vehicleId: Long) = vehicleMutex.withLock {
        val target = vehicleDao.getVehicle(vehicleId) ?: return@withLock
        val replacement = vehicleDao.observeAllVehicles().first().firstOrNull { it.id != target.id }
        if (replacement == null) {
            // Remove the recovery anchor before Room can become empty, just as for a full reset.
            vehiclePreferences.clearVehicle()
            try {
                currentCoroutineContext().ensureActive()
                vehicleDao.delete(target)
                _activeVehicleId.value = null
                publishLoadedVehicles(null, emptyList())
            } finally {
                // Room may have committed before cancellation was delivered. Re-read storage,
                // without resuming deletion, rather than retaining a deleted active snapshot.
                loadRequests.update { it + 1L }
            }
            return@withLock
        }

        val deletingActive = target.id == _activeVehicleId.value
        vehicleDao.delete(target)

        if (deletingActive) {
            _activeVehicleId.value = replacement.id
            _vehicle.value = replacement.toVehicle()
            vehiclePreferences.saveActiveVehicleId(replacement.id)
        }
    }

    suspend fun clearVehicle() = vehicleMutex.withLock {
        try {
            // DataStore commits first: Room must never be empty with a surviving legacy snapshot.
            vehiclePreferences.clearVehicle()
            currentCoroutineContext().ensureActive()
            clearPersonalRoomData()
            _activeVehicleId.value = null
            publishLoadedVehicles(null, emptyList())
        } finally {
            // Re-read authoritative storage, including cancellation at the transaction boundary.
            // Bootstrap only reads/reconciles here; it never resumes a pending deletion.
            loadRequests.update { it + 1L }
        }
    }

    /**
     * Calculates, rather than persists, the current kilometre reading. Invalid historical values
     * are ignored and the result never falls below the configured initial kilometre reading.
     */
    suspend fun currentKm(vehicleId: Long): Double {
        val entity = vehicleDao.getVehicle(vehicleId) ?: return 0.0
        return calculateVehicleCurrentKm(
            initialKm = entity.initialKm,
            entryKilometers = fuelEntryDao.getKilometersForVehicle(vehicleId) +
                maintenanceDao.getOdometerKilometersForVehicle(vehicleId).map(Long::toDouble)
        )
    }

    suspend fun dismissReleaseNotes() {
        releaseNotesMutex.withLock {
            if (!_showReleaseNotes.value) return

            vehiclePreferences.markReleaseNotesAsSeen(RELEASE_NOTES_VERSION)
            _showReleaseNotes.value = false
        }
    }
}

private fun Vehicle.isConfigured(): Boolean =
    brand.isNotBlank() && model.isNotBlank() && year != null && type != null &&
        initialKm.isFinite() && initialKm >= 0.0

private fun VehicleEntity.isRecoveryVehicle(): Boolean =
    brand.isBlank() && model.isBlank() && year == null && type == null
