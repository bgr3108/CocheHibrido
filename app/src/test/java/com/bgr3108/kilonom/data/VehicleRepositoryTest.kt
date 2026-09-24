package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.bgr3108.kilonom.domain.calculateTravelledKilometers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VehicleRepositoryTest {

    @Test
    fun validStoredType_isParsed() {
        assertEquals(VehicleType.GASOLINA, vehicleTypeOrNull("GASOLINA"))
    }

    @Test
    fun legacyHevStoredType_isNormalizedToHybrid() {
        assertEquals(VehicleType.HIBRIDO, vehicleTypeOrNull("HEV"))
    }

    @Test
    fun unknownStoredType_isTreatedAsNotConfigured() {
        assertNull(vehicleTypeOrNull("UNKNOWN_TYPE"))
    }

    @Test
    fun absentStoredCategory_isTreatedAsCarForExistingInstallations() {
        assertEquals(VehicleCategory.COCHE, vehicleCategoryOrDefault(null))
    }

    @Test
    fun newInstallation_hasNoActiveVehicle() = runBlocking {
        val repository = repository(preferences = FakeVehiclePreferences())

        repository.isLoading.first { !it }

        assertEquals(Vehicle(), repository.vehicle.value)
        assertNull(repository.activeVehicleId.value)
    }

    @Test
    fun configuredLegacyVehicleWithoutEntries_isImportedAndActivated() = runBlocking {
        val preferences = FakeVehiclePreferences(vehicle = configuredVehicle())
        val vehicleDao = InMemoryVehicleDao()
        val repository = repository(preferences, vehicleDao)

        repository.isLoading.first { !it }

        assertEquals(1, vehicleDao.vehicles.size)
        assertEquals("SEAT", repository.vehicle.value.brand)
        assertEquals(vehicleDao.vehicles.single().id, repository.activeVehicleId.value)
        assertEquals(vehicleDao.vehicles.single().id, preferences.activeVehicleId)
    }

    @Test
    fun repeatedBootstrap_doesNotDuplicateLegacyVehicle() = runBlocking {
        val preferences = FakeVehiclePreferences(vehicle = configuredVehicle())
        val vehicleDao = InMemoryVehicleDao()

        repository(preferences, vehicleDao).isLoading.first { !it }
        repository(preferences, vehicleDao).isLoading.first { !it }

        assertEquals(1, vehicleDao.vehicles.size)
    }

    @Test
    fun absentActiveVehicleId_usesTheOldestVehicleDeterministically() = runBlocking {
        val first = entity(id = 2, createdAt = 1)
        val later = entity(id = 1, createdAt = 2)
        val preferences = FakeVehiclePreferences()
        val repository = repository(preferences, InMemoryVehicleDao(listOf(later, first)))

        repository.isLoading.first { !it }

        assertEquals(first.id, repository.activeVehicleId.value)
        assertEquals(first.id, preferences.activeVehicleId)
    }

    @Test
    fun invalidActiveVehicleId_usesTheOldestVehicle() = runBlocking {
        val first = entity(id = 1, createdAt = 1)
        val preferences = FakeVehiclePreferences(activeVehicleId = 999)
        val repository = repository(preferences, InMemoryVehicleDao(listOf(first)))

        repository.isLoading.first { !it }

        assertEquals(first.id, repository.activeVehicleId.value)
        assertEquals(first.id, preferences.activeVehicleId)
    }

    @Test
    fun incompleteLegacyData_keepsTheRecoveryVehicleActive() = runBlocking {
        val recoveryVehicle = entity(id = 1, configured = false)
        val repository = repository(
            preferences = FakeVehiclePreferences(),
            vehicleDao = InMemoryVehicleDao(listOf(recoveryVehicle))
        )

        repository.isLoading.first { !it }

        assertEquals(recoveryVehicle.id, repository.activeVehicleId.value)
        assertTrue(repository.vehicle.value.type == null)
    }

    @Test
    fun configuredLegacyData_enrichesTheRecoveryVehicleWithoutCreatingADuplicate() = runBlocking {
        val recoveryVehicle = entity(id = 1, configured = false)
        val preferences = FakeVehiclePreferences(vehicle = configuredVehicle())
        val vehicleDao = InMemoryVehicleDao(listOf(recoveryVehicle))
        val repository = repository(preferences, vehicleDao)

        repository.isLoading.first { !it }

        assertEquals(1, vehicleDao.vehicles.size)
        assertEquals(1L, repository.activeVehicleId.value)
        assertEquals("SEAT", vehicleDao.vehicles.single().brand)
    }

    @Test
    fun successfulSave_updatesRoomAndActiveVehicleAfterPreferencesAreWritten() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val vehicleDao = InMemoryVehicleDao()
        val repository = repository(preferences, vehicleDao)

        repository.isLoading.first { !it }
        repository.saveVehicle(configuredVehicle())

        assertEquals(1, vehicleDao.vehicles.size)
        assertEquals(vehicleDao.vehicles.single().id, repository.activeVehicleId.value)
        assertEquals("SEAT", repository.vehicle.value.brand)
        assertEquals(configuredVehicle().initialKm, repository.vehicle.value.initialKm, 0.0)
    }

    @Test
    fun failedSave_keepsThePreviouslyLoadedVehicle() = runBlocking {
        val previous = configuredVehicle()
        val error = IllegalStateException("DataStore unavailable")
        val repository = repository(
            preferences = FakeVehiclePreferences(vehicle = previous, saveError = error),
            vehicleDao = InMemoryVehicleDao()
        )

        repository.isLoading.first { !it }

        val result = runCatching { repository.saveVehicle(configuredVehicle(type = VehicleType.ELECTRICO)) }

        assertTrue(result.isFailure)
        assertEquals(previous.type, repository.vehicle.value.type)
    }

    @Test
    fun currentKm_usesTheLargestValidEntryButNeverMovesBelowInitialKm() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 50_000.0)
        val fuelEntries = InMemoryFuelEntryDao(
            mutableListOf(
                entry(km = 49_000.0),
                entry(km = 50_360.0),
                entry(km = Double.NaN)
            )
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle)),
            fuelEntryDao = fuelEntries
        )

        repository.isLoading.first { !it }

        assertEquals(50_360.0, repository.currentKm(1), 0.0)
    }

    @Test
    fun currentKm_withoutEntries_usesInitialKm() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 50_000.0)
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle))
        )

        repository.isLoading.first { !it }

        assertEquals(50_000.0, repository.currentKm(1), 0.0)
    }

    @Test
    fun currentKm_includesMaintenanceOdometerButNeverDueIntervalOrReminderKilometers() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 20_000.0)
        val item = maintenanceItem()
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(
                item.copy(
                    nextDueKm = 50_000,
                    intervalKm = 30_000,
                    reminderLeadKm = 2_000
                )
            ),
            records = mutableListOf(maintenanceRecord(odometerKm = 27_000))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle)),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 25_000.0))),
            maintenanceDao = maintenanceDao
        )

        repository.isLoading.first { !it }

        assertEquals(27_000.0, repository.currentKm(1), 0.0)
        assertEquals(
            5_000.0,
            calculateTravelledKilometers(
                listOf(entry(km = 25_000.0)),
                initialKilometers = 20_000.0
            ),
            0.0
        )
    }

    @Test
    fun currentKm_ignoresMaintenanceRecordsWithoutAnOdometer() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 20_000.0)
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(maintenanceItem().copy(nextDueKm = 50_000)),
            records = mutableListOf(maintenanceRecord(odometerKm = null))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle)),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 25_000.0))),
            maintenanceDao = maintenanceDao
        )

        repository.isLoading.first { !it }

        assertEquals(25_000.0, repository.currentKm(1), 0.0)
    }

    @Test
    fun currentKm_returnsToNextRealReadingWhenMaximumMaintenanceRecordIsDeleted() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 20_000.0)
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(maintenanceItem()),
            records = mutableListOf(maintenanceRecord(odometerKm = 27_000))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle)),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 25_000.0))),
            maintenanceDao = maintenanceDao
        )
        repository.isLoading.first { !it }

        maintenanceDao.deleteRecordForVehicle(1, 1)

        assertEquals(25_000.0, repository.currentKm(1), 0.0)
    }

    @Test
    fun currentKm_recalculatesWhenEditingRemovesTheMaximumMaintenanceOdometer() = runBlocking {
        val vehicle = entity(id = 1, initialKm = 20_000.0)
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(maintenanceItem()),
            records = mutableListOf(maintenanceRecord(odometerKm = 27_000))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(vehicle)),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 25_000.0))),
            maintenanceDao = maintenanceDao
        )
        repository.isLoading.first { !it }

        maintenanceDao.updateRecord(maintenanceRecord(odometerKm = null))

        assertEquals(25_000.0, repository.currentKm(1), 0.0)
    }

    @Test
    fun loadFailure_isExposedAndEndsLoading() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val repository = repository(preferences = FakeVehiclePreferences(loadError = error))

        repository.isLoading.first { !it }

        assertSame(error, repository.loadError.value)
        assertFalse(repository.isLoading.value)
    }

    @Test
    fun dataStoreSnapshotFailure_recoversTheResolvableActiveRoomVehicle() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val active = entity(id = 2, createdAt = 2)
        val repository = repository(
            preferences = FakeVehiclePreferences(loadError = error, activeVehicleId = active.id),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, createdAt = 1), active))
        )

        repository.isLoading.first { !it }

        assertEquals(active.id, repository.activeVehicleId.value)
    }

    @Test
    fun dataStoreSnapshotFailureAndInvalidActiveId_usesTheOldestRoomVehicle() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val first = entity(id = 2, createdAt = 1)
        val repository = repository(
            preferences = FakeVehiclePreferences(loadError = error, activeVehicleId = 999),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, createdAt = 2), first))
        )

        repository.isLoading.first { !it }

        assertEquals(first.id, repository.activeVehicleId.value)
    }

    @Test
    fun completeDataStoreFailure_recoversTheOldestConfiguredRoomVehicle() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val first = entity(id = 2, createdAt = 1)
        val repository = repository(
            preferences = FakeVehiclePreferences(
                loadError = error,
                activeVehicleIdLoadError = error,
                activeVehicleIdSaveError = error
            ),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, createdAt = 2), first))
        )

        repository.isLoading.first { !it }

        assertEquals(first.id, repository.activeVehicleId.value)
        assertEquals(first.type, repository.vehicle.value.type)
    }

    @Test
    fun dataStoreFailureWithNoRoomVehicle_keepsTheInstallationUnconfigured() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val repository = repository(
            preferences = FakeVehiclePreferences(
                loadError = error,
                activeVehicleIdLoadError = error
            )
        )

        repository.isLoading.first { !it }

        assertNull(repository.activeVehicleId.value)
    }

    @Test
    fun dataStoreFailureWithRecoveryVehicle_preservesItAndItsEntriesForSafeSetup() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val fuelEntries = InMemoryFuelEntryDao(mutableListOf(entry(km = 1_250.0)))
        val vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, configured = false)))
        val repository = repository(
            preferences = FakeVehiclePreferences(
                loadError = error,
                activeVehicleIdLoadError = error,
                activeVehicleIdSaveError = error
            ),
            vehicleDao = vehicleDao,
            fuelEntryDao = fuelEntries
        )

        repository.isLoading.first { !it }

        assertEquals(1, vehicleDao.vehicles.size)
        assertEquals(1, fuelEntries.entries.size)
        assertNull(repository.vehicle.value.type)
    }

    @Test
    fun freshInstallation_baselinesCurrentReleaseNotesWithoutShowingThem() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val repository = repository(preferences)

        repository.isLoading.first { !it }

        assertFalse(repository.showReleaseNotes.value)
        assertEquals(RELEASE_NOTES_VERSION, preferences.releaseNotesVersion)
    }

    @Test
    fun existingOnePointThreeUser_showsCurrentReleaseNotesOnce() = runBlocking {
        val preferences = FakeVehiclePreferences(
            hasStoredAppState = true,
            releaseNotesVersion = "1.3.0"
        )
        val repository = repository(preferences)

        repository.isLoading.first { !it }

        assertTrue(repository.showReleaseNotes.value)

        repository.dismissReleaseNotes()

        assertFalse(repository.showReleaseNotes.value)
        assertEquals(RELEASE_NOTES_VERSION, preferences.releaseNotesVersion)
    }

    @Test
    fun dismissedReleaseNotes_areNotShownAgainForTheSameVersion() = runBlocking {
        val preferences = FakeVehiclePreferences(hasStoredAppState = true)
        val firstRepository = repository(preferences)
        firstRepository.isLoading.first { !it }
        firstRepository.dismissReleaseNotes()

        val recreatedRepository = repository(preferences)
        recreatedRepository.isLoading.first { !it }

        assertFalse(recreatedRepository.showReleaseNotes.value)
    }

    @Test
    fun existingInstallation_withoutReleaseNotesMarker_stillShowsTheCurrentNotes() = runBlocking {
        val repository = repository(FakeVehiclePreferences(hasStoredAppState = true))

        repository.isLoading.first { !it }

        assertTrue(repository.showReleaseNotes.value)
    }

    @Test
    fun futureReleaseNotes_areShownForAnExistingInstallation() {
        assertTrue(
            shouldShowReleaseNotes(
                hasExistingInstallation = true,
                lastSeenReleaseNotesVersion = "1.4.0",
                currentReleaseNotesVersion = "1.5.0"
            )
        )
    }

    @Test
    fun cleanReinstallation_baselinesCurrentReleaseNotesWithoutShowingThem() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val repository = repository(preferences)

        repository.isLoading.first { !it }

        assertFalse(repository.showReleaseNotes.value)
        assertEquals(RELEASE_NOTES_VERSION, preferences.releaseNotesVersion)
    }

    @Test
    fun createVehicle_makesTheNewSnapshotActiveAndKeepsItsInitialKilometers() = runBlocking {
        val repository = repository(FakeVehiclePreferences())
        repository.isLoading.first { !it }

        repository.createVehicle(configuredVehicle(initialKm = 12_000.0))

        assertEquals(1L, repository.activeVehicleId.value)
        assertEquals(
            12_000.0,
            repository.vehicleSummaries.first { it.size == 1 }.single().currentKm,
            0.0
        )
    }

    @Test
    fun updateVehicleWithoutEntries_canReplaceTheCatalogSnapshot() = runBlocking {
        val stored = entity(id = 1, initialKm = 1_000.0)
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(stored))
        )
        repository.isLoading.first { !it }

        repository.updateVehicle(1, configuredVehicle(type = VehicleType.ELECTRICO, initialKm = 2_000.0))

        assertEquals(VehicleType.ELECTRICO, repository.vehicle.value.type)
    }

    @Test
    fun updateVehicleWithEntries_rejectsAPropulsionChange() = runBlocking {
        val stored = entity(id = 1, initialKm = 1_000.0)
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(stored)),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 1_250.0)))
        )
        repository.isLoading.first { !it }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateVehicle(1, configuredVehicle(type = VehicleType.ELECTRICO, initialKm = 1_000.0)) }
        }
        Unit
    }

    @Test
    fun updateVehicle_rejectsInitialKilometersAboveTheFirstValidEntry() = runBlocking {
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, initialKm = 1_000.0))),
            fuelEntryDao = InMemoryFuelEntryDao(mutableListOf(entry(km = 1_250.0)))
        )
        repository.isLoading.first { !it }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateVehicle(1, configuredVehicle(initialKm = 1_251.0)) }
        }
        Unit
    }

    @Test
    fun updateVehicle_rejectsInitialKilometersAboveTheFirstMaintenanceOdometer() = runBlocking {
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(maintenanceItem()),
            records = mutableListOf(maintenanceRecord(odometerKm = 1_250))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, initialKm = 1_000.0))),
            maintenanceDao = maintenanceDao
        )
        repository.isLoading.first { !it }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateVehicle(1, configuredVehicle(initialKm = 1_251.0)) }
        }
        Unit
    }

    @Test
    fun updateVehicleWithMaintenance_rejectsStructuralChanges() = runBlocking {
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(maintenanceItem()),
            records = mutableListOf(maintenanceRecord(odometerKm = 1_250))
        )
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, initialKm = 1_000.0))),
            maintenanceDao = maintenanceDao
        )
        repository.isLoading.first { !it }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.updateVehicle(1, configuredVehicle(type = VehicleType.ELECTRICO, initialKm = 1_000.0)) }
        }
        Unit
    }

    @Test
    fun deleteActiveVehicle_selectsTheOldestRemainingVehicle() = runBlocking {
        val first = entity(id = 1, createdAt = 1)
        val second = entity(id = 2, createdAt = 2)
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 2),
            vehicleDao = InMemoryVehicleDao(listOf(first, second))
        )
        repository.isLoading.first { !it }

        repository.deleteVehicle(2)

        assertEquals(1L, repository.activeVehicleId.value)
    }

    @Test
    fun deleteInactiveVehicle_keepsTheCurrentActiveVehicle() = runBlocking {
        val repository = repository(
            preferences = FakeVehiclePreferences(activeVehicleId = 1),
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, createdAt = 1), entity(id = 2, createdAt = 2)))
        )
        repository.isLoading.first { !it }

        repository.deleteVehicle(2)

        assertEquals(1L, repository.activeVehicleId.value)
    }

    @Test
    fun deleteLastVehicle_clearsTheActiveVehicleId() = runBlocking {
        val preferences = FakeVehiclePreferences(activeVehicleId = 1)
        val repository = repository(
            preferences = preferences,
            vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1)))
        )
        repository.isLoading.first { !it }

        repository.deleteVehicle(1)

        assertNull(repository.activeVehicleId.value)
    }

    @Test
    fun selectedVehicle_isRestoredAfterRepositoryRecreation() = runBlocking {
        val preferences = FakeVehiclePreferences(activeVehicleId = 1)
        val vehicleDao = InMemoryVehicleDao(listOf(entity(id = 1, createdAt = 1), entity(id = 2, createdAt = 2)))
        val firstRepository = repository(preferences, vehicleDao)
        firstRepository.isLoading.first { !it }
        firstRepository.selectActiveVehicle(2)

        val recreatedRepository = repository(preferences, vehicleDao)
        recreatedRepository.isLoading.first { !it }

        assertEquals(2L, recreatedRepository.activeVehicleId.value)
    }

    private fun repository(
        preferences: FakeVehiclePreferences,
        vehicleDao: InMemoryVehicleDao = InMemoryVehicleDao(),
        fuelEntryDao: InMemoryFuelEntryDao = InMemoryFuelEntryDao(),
        maintenanceDao: InMemoryMaintenanceDao = InMemoryMaintenanceDao()
    ) = VehicleRepository(EmptyVehicleCatalog, preferences, vehicleDao, fuelEntryDao, maintenanceDao)

    private fun configuredVehicle(
        type: VehicleType = VehicleType.HIBRIDO_ENCHUFABLE,
        initialKm: Double = 50_000.0
    ) = Vehicle(
        brand = "SEAT",
        model = "León e-HYBRID",
        year = 2026,
        category = VehicleCategory.COCHE,
        type = type,
        fuelTankCapacity = 40.0,
        batteryCapacity = 19.7,
        initialKm = initialKm
    )

    private fun entity(
        id: Long,
        createdAt: Long = 1,
        initialKm: Double = 0.0,
        configured: Boolean = true
    ) = VehicleEntity(
        id = id,
        category = VehicleCategory.COCHE,
        brand = if (configured) "SEAT" else "",
        model = if (configured) "León" else "",
        year = if (configured) 2026 else null,
        type = if (configured) VehicleType.GASOLINA else null,
        fuelTankCapacity = 40.0,
        batteryCapacity = 0.0,
        initialKm = initialKm,
        createdAt = createdAt
    )

    private fun entry(km: Double) = FuelEntry(
        fecha = 1L,
        cantidad = 10.0,
        precio = 10.0,
        tipo = FuelType.GASOLINA,
        km = km,
        vehicleId = 1L
    )

    private fun maintenanceItem() = MaintenanceItemEntity(
        id = 1,
        vehicleId = 1,
        type = MaintenanceType.OIL_AND_FILTER,
        trackingKey = "OIL_AND_FILTER",
        createdAt = 0,
        updatedAt = 0
    )

    private fun maintenanceRecord(odometerKm: Long?) = MaintenanceRecordEntity(
        id = 1,
        itemId = 1,
        odometerKm = odometerKm,
        createdAt = 0,
        updatedAt = 0
    )

    private object EmptyVehicleCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
    }

    private class FakeVehiclePreferences(
        private val vehicle: Vehicle = Vehicle(),
        private val loadError: Exception? = null,
        private val saveError: Exception? = null,
        var activeVehicleId: Long? = null,
        private val activeVehicleIdLoadError: Exception? = null,
        private val activeVehicleIdSaveError: Exception? = null,
        private val hasStoredAppState: Boolean = false,
        var releaseNotesVersion: String? = null
    ) : VehiclePreferencesStore {
        override suspend fun saveVehicle(vehicle: Vehicle) {
            saveError?.let { throw it }
        }

        override suspend fun loadVehicle(): Vehicle {
            loadError?.let { throw it }
            return vehicle
        }

        override suspend fun clearVehicle() {
            activeVehicleId = null
        }

        override suspend fun loadActiveVehicleId(): Long? {
            activeVehicleIdLoadError?.let { throw it }
            return activeVehicleId
        }

        override suspend fun saveActiveVehicleId(vehicleId: Long) {
            activeVehicleIdSaveError?.let { throw it }
            activeVehicleId = vehicleId
        }

        override suspend fun hasStoredAppState(): Boolean =
            hasStoredAppState || releaseNotesVersion != null || activeVehicleId != null || vehicle != Vehicle()

        override suspend fun lastSeenReleaseNotesVersion(): String? = releaseNotesVersion

        override suspend fun markReleaseNotesAsSeen(versionName: String) {
            releaseNotesVersion = versionName
        }
    }
}
