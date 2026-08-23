package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun loadFailure_isExposedAndEndsLoading() = runBlocking {
        val error = IllegalStateException("DataStore unavailable")
        val repository = repository(preferences = FakeVehiclePreferences(loadError = error))

        repository.isLoading.first { !it }

        assertSame(error, repository.loadError.value)
        assertFalse(repository.isLoading.value)
    }

    @Test
    fun unseenReleaseNotes_areShownAndPersistedWhenDismissed() = runBlocking {
        val preferences = FakeVehiclePreferences()
        val repository = repository(preferences)

        repository.isLoading.first { !it }
        repository.dismissReleaseNotes()

        assertFalse(repository.showReleaseNotes.value)
        assertEquals(RELEASE_NOTES_VERSION, preferences.releaseNotesVersion)
    }

    private fun repository(
        preferences: FakeVehiclePreferences,
        vehicleDao: InMemoryVehicleDao = InMemoryVehicleDao(),
        fuelEntryDao: InMemoryFuelEntryDao = InMemoryFuelEntryDao()
    ) = VehicleRepository(EmptyVehicleCatalog, preferences, vehicleDao, fuelEntryDao)

    private fun configuredVehicle(type: VehicleType = VehicleType.HIBRIDO_ENCHUFABLE) = Vehicle(
        brand = "SEAT",
        model = "León e-HYBRID",
        year = 2026,
        category = VehicleCategory.COCHE,
        type = type,
        fuelTankCapacity = 40.0,
        batteryCapacity = 19.7,
        initialKm = 50_000.0
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

    private object EmptyVehicleCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
    }

    private class FakeVehiclePreferences(
        private val vehicle: Vehicle = Vehicle(),
        private val loadError: Exception? = null,
        private val saveError: Exception? = null,
        var activeVehicleId: Long? = null,
        var releaseNotesVersion: String? = null
    ) : VehiclePreferencesStore {
        override suspend fun saveVehicle(vehicle: Vehicle) {
            saveError?.let { throw it }
        }

        override suspend fun loadVehicle(): Vehicle {
            loadError?.let { throw it }
            return vehicle
        }

        override suspend fun clearVehicle() = Unit

        override suspend fun loadActiveVehicleId(): Long? = activeVehicleId

        override suspend fun saveActiveVehicleId(vehicleId: Long) {
            activeVehicleId = vehicleId
        }

        override suspend fun hasSeenReleaseNotes(versionName: String): Boolean =
            releaseNotesVersion == versionName

        override suspend fun markReleaseNotesAsSeen(versionName: String) {
            releaseNotesVersion = versionName
        }
    }
}
