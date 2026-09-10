package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.database.VehicleDao
import com.bgr3108.kilonom.domain.calculateVehicleCurrentKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val RELEASE_NOTES_VERSION = "1.3.0"

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
    private val maintenanceDao: MaintenanceDao
) {

    data class VehicleSummary(
        val vehicle: VehicleEntity,
        val currentKm: Double,
        val entryCount: Int,
        val maintenanceRecordCount: Int,
        val minimumValidRecordedKm: Double?
    )

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _vehicle = MutableStateFlow(Vehicle())
    private val _activeVehicleId = MutableStateFlow<Long?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _loadError = MutableStateFlow<Throwable?>(null)
    private val _showReleaseNotes = MutableStateFlow(false)
    private val releaseNotesMutex = Mutex()
    private val vehicleMutex = Mutex()

    val isLoading: StateFlow<Boolean> = _isLoading
    val loadError: StateFlow<Throwable?> = _loadError
    val activeVehicleId: StateFlow<Long?> = _activeVehicleId
    val activeVehicle: StateFlow<VehicleEntity?> = _activeVehicleId
        .flatMapLatest { id -> id?.let(vehicleDao::observeVehicle) ?: flowOf(null) }
        .stateIn(repositoryScope, SharingStarted.Eagerly, null)
    /** Compatibility snapshot; active data consumers use [activeVehicle] instead. */
    val vehicle: StateFlow<Vehicle> = _vehicle
    val showReleaseNotes: StateFlow<Boolean> = _showReleaseNotes

    /** All Room snapshots with their own derived kilometre readings and entry counts. */
    val vehicleSummaries = vehicleDao.observeAllVehicles()
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
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    init {
        repositoryScope.launch {
            try {
                bootstrapLegacyVehicle()
            } catch (error: Throwable) {
                _activeVehicleId.value = null
                _vehicle.value = Vehicle()
                _loadError.value = error
            } finally {
                _showReleaseNotes.value = runCatching {
                    !vehiclePreferences.hasSeenReleaseNotes(RELEASE_NOTES_VERSION)
                }.getOrDefault(false)
                _isLoading.value = false
            }
        }
    }

    /**
     * Reconciles the legacy DataStore snapshot with Room. Re-running it updates the recovery
     * anchor rather than creating another vehicle, so an interrupted bootstrap stays safe.
     */
    private suspend fun bootstrapLegacyVehicle() = vehicleMutex.withLock {
        val legacyVehicle = readBootstrapPreferenceOr(Vehicle()) {
            vehiclePreferences.loadVehicle()
        }
        val configuredLegacyVehicle = legacyVehicle.takeIf { it.isConfigured() }
        val storedActiveId = readBootstrapPreferenceOr(null) {
            vehiclePreferences.loadActiveVehicleId()
        }
        val storedActiveVehicle = storedActiveId?.let { vehicleDao.getVehicle(it) }
        val firstVehicle = vehicleDao.getFirstVehicle()

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
                val id = vehicleDao.insert(
                    configuredLegacyVehicle.toEntity(createdAt = System.currentTimeMillis())
                )
                vehicleDao.getVehicle(id) ?: error("No se pudo recuperar el vehículo creado")
            }

            else -> null
        }

        _activeVehicleId.value = activeVehicle?.id
        _vehicle.value = activeVehicle?.toVehicle() ?: Vehicle()
        activeVehicle?.let { vehicle -> saveBootstrapActiveVehicleId(vehicle.id) }
    }

    /**
     * DataStore is only a legacy/bootstrap snapshot. A read failure must not make valid Room
     * vehicle snapshots disappear from the active context.
     */
    private suspend fun <T> readBootstrapPreferenceOr(
        defaultValue: T,
        read: suspend () -> T
    ): T = try {
        read()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error

        recordBootstrapPreferenceFailure(error)
        defaultValue
    }

    private fun recordBootstrapPreferenceFailure(error: Throwable) {
        if (_loadError.value == null) {
            _loadError.value = error
        }
    }

    private suspend fun saveBootstrapActiveVehicleId(vehicleId: Long) {
        try {
            vehiclePreferences.saveActiveVehicleId(vehicleId)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error

            recordBootstrapPreferenceFailure(error)
        }
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
        _activeVehicleId.value = resolved?.id
        _vehicle.value = resolved?.toVehicle() ?: Vehicle()
        if (resolved != null) {
            vehiclePreferences.saveActiveVehicleId(resolved.id)
        } else {
            vehiclePreferences.clearActiveVehicleId()
        }
    }

    /** Creates an independent Room snapshot and makes it the active vehicle. */
    suspend fun createVehicle(vehicle: Vehicle): VehicleEntity = vehicleMutex.withLock {
        val id = vehicleDao.insert(vehicle.copy(id = null).toEntity(createdAt = System.currentTimeMillis()))
        val entity = vehicleDao.getVehicle(id) ?: error("No se pudo recuperar el vehículo creado")
        vehiclePreferences.saveActiveVehicleId(entity.id)
        _activeVehicleId.value = entity.id
        _vehicle.value = entity.toVehicle()
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

    /** Deletes one snapshot; Room cascades its associated entries and maintenance history. */
    suspend fun deleteVehicle(vehicleId: Long) = vehicleMutex.withLock {
        val target = vehicleDao.getVehicle(vehicleId) ?: return@withLock
        val deletingActive = target.id == _activeVehicleId.value
        vehicleDao.delete(target)

        if (deletingActive) {
            val replacement = vehicleDao.getFirstVehicle()
            _activeVehicleId.value = replacement?.id
            _vehicle.value = replacement?.toVehicle() ?: Vehicle()
            if (replacement != null) {
                vehiclePreferences.saveActiveVehicleId(replacement.id)
            } else {
                vehiclePreferences.clearVehicle()
            }
        }
    }

    suspend fun clearVehicle() = vehicleMutex.withLock {
        vehicleDao.deleteAll()
        vehiclePreferences.clearVehicle()
        _activeVehicleId.value = null
        _vehicle.value = Vehicle()
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
