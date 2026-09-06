package com.bgr3108.kilonom.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveVehicleEntriesTest {

    @Test
    fun changingActiveVehicle_switchesEntriesCapabilitiesAndCurrentKilometers() = runBlocking {
        val vehicleA = vehicle(id = 1, type = VehicleType.GASOLINA, initialKm = 1_000.0)
        val vehicleB = vehicle(id = 2, type = VehicleType.ELECTRICO, initialKm = 8_000.0)
        val entries = InMemoryFuelEntryDao(
            mutableListOf(entry(id = 1, km = 1_250.0))
        )
        val preferences = Preferences(activeVehicleId = 1)
        val repository = VehicleRepository(
            EmptyCatalog,
            preferences,
            InMemoryVehicleDao(listOf(vehicleA, vehicleB)),
            entries,
            InMemoryMaintenanceDao()
        )
        val fuelRepository = FuelRepository(entries)

        repository.isLoading.first { !it }
        assertEquals(1L, fuelRepository.observeActiveVehicleEntries(repository.activeVehicle)
            .first { it.vehicle.id == 1L }.vehicle.id)
        assertEquals(1_250.0, repository.currentKm(1), 0.0)

        repository.selectActiveVehicle(2)
        val activeB = fuelRepository.observeActiveVehicleEntries(repository.activeVehicle)
            .first { it.vehicle.id == 2L }

        assertEquals(emptyList<FuelEntry>(), activeB.entries)
        assertEquals(VehicleType.ELECTRICO, activeB.vehicle.type)
        assertEquals(8_000.0, repository.currentKm(2), 0.0)
    }

    @Test
    fun newEntry_isPersistedForTheResolvedActiveVehicleOnly() = runBlocking {
        val entries = InMemoryFuelEntryDao()
        val repository = repository(fuelEntries = entries)
        repository.isLoading.first { !it }

        repository.selectActiveVehicle(2)
        fuelRepository(entries).addEntryForVehicle(
            entry(id = 0, km = 8_100.0),
            repository.activeVehicleId.value!!
        )

        assertEquals(2L, entries.entries.single().vehicleId)
    }

    @Test
    fun editingOrDeletingAnotherVehiclesEntry_isRejected() = runBlocking {
        val vehicleAEntry = entry(id = 4, km = 1_300.0)
        val entries = InMemoryFuelEntryDao(mutableListOf(vehicleAEntry))
        val repository = repository(fuelEntries = entries)
        repository.isLoading.first { !it }
        repository.selectActiveVehicle(2)
        val fuelRepository = fuelRepository(entries)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { fuelRepository.updateEntryForVehicle(vehicleAEntry, 2) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { fuelRepository.deleteEntryForVehicle(vehicleAEntry, 2) }
        }
        assertEquals(vehicleAEntry, entries.entries.single())
    }

    @Test
    fun missingActiveVehicle_rejectsNewEntries() = runBlocking {
        val entries = InMemoryFuelEntryDao()
        val repository = VehicleRepository(
            EmptyCatalog,
            Preferences(activeVehicleId = null),
            InMemoryVehicleDao(),
            entries,
            InMemoryMaintenanceDao()
        )
        repository.isLoading.first { !it }
        assertEquals(null, repository.activeVehicleId.value)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { fuelRepository(entries).addEntryForVehicle(entry(id = 0, km = 100.0), 0) }
        }
        assertEquals(emptyList<FuelEntry>(), entries.entries)
    }

    @Test
    fun maintenanceRecordsAndOdometerRemainIsolatedWhenTheActiveVehicleChanges() = runBlocking {
        val maintenanceDao = InMemoryMaintenanceDao(
            items = mutableListOf(
                maintenanceItem(id = 1, vehicleId = 1, type = MaintenanceType.OIL_AND_FILTER).copy(
                    intervalKm = 10_000L,
                    reminderLeadKm = 1_500L
                ),
                maintenanceItem(id = 2, vehicleId = 2, type = MaintenanceType.ITV).copy(
                    intervalTimeValue = 12,
                    intervalTimeUnit = MaintenanceTimeUnit.MONTHS,
                    reminderLeadDays = 14L
                )
            ),
            records = mutableListOf(
                MaintenanceRecordEntity(id = 1, itemId = 1, odometerKm = 1_300, createdAt = 1, updatedAt = 1),
                MaintenanceRecordEntity(id = 2, itemId = 2, odometerKm = 8_500, createdAt = 2, updatedAt = 2)
            )
        )
        val repository = VehicleRepository(
            EmptyCatalog,
            Preferences(activeVehicleId = 1),
            InMemoryVehicleDao(
                listOf(
                    vehicle(id = 1, type = VehicleType.GASOLINA, initialKm = 1_000.0),
                    vehicle(id = 2, type = VehicleType.ELECTRICO, initialKm = 8_000.0)
                )
            ),
            InMemoryFuelEntryDao(),
            maintenanceDao
        )
        repository.isLoading.first { !it }

        assertEquals(1_300.0, repository.currentKm(1), 0.0)
        assertEquals(1_500L, maintenanceDao.observeItems(1).first().single().reminderLeadKm)
        assertEquals(1L, maintenanceDao.observeRecordsForVehicle(repository.activeVehicleId.value!!).first().single().id)

        repository.selectActiveVehicle(2)

        assertEquals(8_500.0, repository.currentKm(2), 0.0)
        assertEquals(MaintenanceTimeUnit.MONTHS, maintenanceDao.observeItems(2).first().single().intervalTimeUnit)
        assertEquals(14L, maintenanceDao.observeItems(2).first().single().reminderLeadDays)
        assertEquals(2L, maintenanceDao.observeRecordsForVehicle(repository.activeVehicleId.value!!).first().single().id)
        assertEquals(1_300L, maintenanceDao.getMinimumOdometerKmForVehicle(1))
        assertEquals(8_500L, maintenanceDao.getMaximumOdometerKmForVehicle(2))
    }

    private fun repository(fuelEntries: InMemoryFuelEntryDao): VehicleRepository =
        VehicleRepository(
            EmptyCatalog,
            Preferences(activeVehicleId = 2),
            InMemoryVehicleDao(
                listOf(
                    vehicle(id = 1, type = VehicleType.GASOLINA, initialKm = 1_000.0),
                    vehicle(id = 2, type = VehicleType.ELECTRICO, initialKm = 8_000.0)
                )
            ),
            fuelEntries,
            InMemoryMaintenanceDao()
        )

    private fun fuelRepository(entries: InMemoryFuelEntryDao) = FuelRepository(entries)

    private fun vehicle(id: Long, type: VehicleType, initialKm: Double) = VehicleEntity(
        id = id,
        category = VehicleCategory.COCHE,
        brand = "Marca$id",
        model = "Modelo$id",
        year = 2026,
        type = type,
        fuelTankCapacity = if (type.supportsFuelEntries) 40.0 else 0.0,
        batteryCapacity = if (type.supportsElectricEntries) 60.0 else 0.0,
        initialKm = initialKm,
        createdAt = id
    )

    private fun entry(id: Int, km: Double) = FuelEntry(
        id = id,
        fecha = id.toLong(),
        cantidad = 10.0,
        precio = 10.0,
        tipo = FuelType.GASOLINA,
        km = km,
        vehicleId = 1L
    )

    private fun maintenanceItem(id: Long, vehicleId: Long, type: MaintenanceType) = MaintenanceItemEntity(
        id = id,
        vehicleId = vehicleId,
        type = type,
        trackingKey = type.name,
        createdAt = id,
        updatedAt = id
    )

    private object EmptyCatalog : VehicleCatalog {
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
    }

    private class Preferences(
        var activeVehicleId: Long?
    ) : VehiclePreferencesStore {
        override suspend fun saveVehicle(vehicle: Vehicle) = Unit
        override suspend fun loadVehicle(): Vehicle = Vehicle()
        override suspend fun clearVehicle() = Unit
        override suspend fun loadActiveVehicleId(): Long? = activeVehicleId
        override suspend fun saveActiveVehicleId(vehicleId: Long) {
            activeVehicleId = vehicleId
        }
    }
}
