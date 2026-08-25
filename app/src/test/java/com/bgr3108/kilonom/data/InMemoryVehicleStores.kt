package com.bgr3108.kilonom.data

import com.bgr3108.kilonom.database.FuelEntryDao
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.database.MaintenanceRecordWithItemRow
import com.bgr3108.kilonom.database.VehicleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class InMemoryVehicleDao(
    initialVehicles: List<VehicleEntity> = emptyList(),
    private val onVehicleDeleted: ((Long) -> Unit)? = null
) : VehicleDao {
    val vehicles = initialVehicles.toMutableList()
    private val vehiclesFlow = MutableStateFlow(vehicles.toList())
    private var nextId = (vehicles.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeAllVehicles(): Flow<List<VehicleEntity>> =
        vehiclesFlow.map { it.sortedWith(compareBy<VehicleEntity> { vehicle -> vehicle.createdAt }.thenBy { vehicle -> vehicle.id }) }

    override fun observeVehicle(id: Long): Flow<VehicleEntity?> =
        vehiclesFlow.map { vehicles -> vehicles.find { it.id == id } }

    override suspend fun getVehicle(id: Long): VehicleEntity? = vehicles.find { it.id == id }

    override suspend fun getFirstVehicle(): VehicleEntity? =
        vehicles.minWithOrNull(compareBy<VehicleEntity> { it.createdAt }.thenBy { it.id })

    override suspend fun insert(vehicle: VehicleEntity): Long {
        val saved = vehicle.copy(id = if (vehicle.id == 0L) nextId++ else vehicle.id)
        vehicles += saved
        vehiclesFlow.value = vehicles.toList()
        return saved.id
    }

    override suspend fun update(vehicle: VehicleEntity) {
        val index = vehicles.indexOfFirst { it.id == vehicle.id }
        check(index >= 0)
        vehicles[index] = vehicle
        vehiclesFlow.value = vehicles.toList()
    }

    override suspend fun delete(vehicle: VehicleEntity) {
        vehicles.removeAll { it.id == vehicle.id }
        onVehicleDeleted?.invoke(vehicle.id)
        vehiclesFlow.value = vehicles.toList()
    }

    override suspend fun deleteAll() {
        vehicles.map { it.id }.forEach { onVehicleDeleted?.invoke(it) }
        vehicles.clear()
        vehiclesFlow.value = emptyList()
    }
}

internal class InMemoryFuelEntryDao(
    val entries: MutableList<FuelEntry> = mutableListOf()
) : FuelEntryDao {
    private val entriesFlow = MutableStateFlow(entries.toList())

    override fun observeEntries(vehicleId: Long): Flow<List<FuelEntry>> =
        entriesFlow.map { entries ->
            entries.filter { it.vehicleId == vehicleId }.sortedByDescending { it.fecha }
        }

    override suspend fun getEntryForVehicle(entryId: Int, vehicleId: Long): FuelEntry? =
        entries.find { it.id == entryId && it.vehicleId == vehicleId }

    override suspend fun getKilometersForVehicle(vehicleId: Long): List<Double> =
        entries.filter { it.vehicleId == vehicleId }.map { it.km }

    override suspend fun countEntries(vehicleId: Long): Int =
        entries.count { it.vehicleId == vehicleId }

    override suspend fun insertEntry(entry: FuelEntry) {
        entries.removeAll { it.id == entry.id && entry.id != 0 }
        entries += entry
        entriesFlow.value = entries.toList()
    }

    override suspend fun updateEntry(entry: FuelEntry): Int {
        val index = entries.indexOfFirst { it.id == entry.id && it.vehicleId == entry.vehicleId }
        if (index < 0) return 0
        entries[index] = entry
        entriesFlow.value = entries.toList()
        return 1
    }

    override suspend fun deleteEntryForVehicle(entryId: Int, vehicleId: Long): Int {
        val removed = entries.removeAll { it.id == entryId && it.vehicleId == vehicleId }
        if (removed) entriesFlow.value = entries.toList()
        return if (removed) 1 else 0
    }

    override suspend fun deleteAll() {
        entries.clear()
        entriesFlow.value = emptyList()
    }
}

internal class InMemoryMaintenanceDao(
    val items: MutableList<MaintenanceItemEntity> = mutableListOf(),
    val records: MutableList<MaintenanceRecordEntity> = mutableListOf()
) : MaintenanceDao {
    private val itemsFlow = MutableStateFlow(items.toList())
    private val recordsFlow = MutableStateFlow(records.toList())
    private var nextItemId = (items.maxOfOrNull { it.id } ?: 0L) + 1L
    private var nextRecordId = (records.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeItems(vehicleId: Long): Flow<List<MaintenanceItemEntity>> =
        itemsFlow.map { values -> values.filter { it.vehicleId == vehicleId } }

    override suspend fun getItemForVehicle(itemId: Long, vehicleId: Long): MaintenanceItemEntity? =
        items.find { it.id == itemId && it.vehicleId == vehicleId }

    override fun observeRecordsForItem(itemId: Long, vehicleId: Long): Flow<List<MaintenanceRecordEntity>> =
        recordsFlow.map { values ->
            values.filter { it.itemId == itemId && rowFor(it, vehicleId) != null }
                .sortedWith(compareByDescending<MaintenanceRecordEntity> { it.performedDate }.thenByDescending { it.createdAt })
        }

    override fun observeRecordsForVehicle(vehicleId: Long): Flow<List<MaintenanceRecordWithItemRow>> =
        recordsFlow.map { values -> values.mapNotNull { rowFor(it, vehicleId) } }

    override suspend fun getRecordForVehicle(recordId: Long, vehicleId: Long): MaintenanceRecordWithItemRow? =
        records.find { it.id == recordId }?.let { rowFor(it, vehicleId) }

    override fun observeOdometerKilometersForVehicle(vehicleId: Long): Flow<List<Long>> =
        recordsFlow.map { values -> values.mapNotNull { record ->
            record.odometerKm?.takeIf { rowFor(record, vehicleId) != null }
        } }

    override suspend fun getOdometerKilometersForVehicle(vehicleId: Long): List<Long> =
        records.mapNotNull { record ->
            record.odometerKm?.takeIf { rowFor(record, vehicleId) != null }
        }

    override suspend fun getMaximumOdometerKmForVehicle(vehicleId: Long): Long? =
        getOdometerKilometersForVehicle(vehicleId).maxOrNull()

    override suspend fun getMinimumOdometerKmForVehicle(vehicleId: Long): Long? =
        getOdometerKilometersForVehicle(vehicleId).minOrNull()

    override suspend fun countRecordsForVehicle(vehicleId: Long): Int =
        records.count { rowFor(it, vehicleId) != null }

    override fun observeRecordCountForVehicle(vehicleId: Long): Flow<Int> =
        recordsFlow.map { values -> values.count { rowFor(it, vehicleId) != null } }

    override suspend fun insertItem(item: MaintenanceItemEntity): Long {
        check(items.none { it.vehicleId == item.vehicleId && it.trackingKey == item.trackingKey })
        val saved = item.copy(id = if (item.id == 0L) nextItemId++ else item.id)
        items += saved
        itemsFlow.value = items.toList()
        return saved.id
    }

    override suspend fun updateItem(item: MaintenanceItemEntity): Int {
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) return 0
        items[index] = item
        itemsFlow.value = items.toList()
        return 1
    }

    override suspend fun deleteItemForVehicle(itemId: Long, vehicleId: Long): Int {
        val deleted = items.removeAll { it.id == itemId && it.vehicleId == vehicleId }
        if (deleted) {
            records.removeAll { it.itemId == itemId }
            itemsFlow.value = items.toList()
            recordsFlow.value = records.toList()
        }
        return if (deleted) 1 else 0
    }

    override suspend fun insertRecord(record: MaintenanceRecordEntity): Long {
        val saved = record.copy(id = if (record.id == 0L) nextRecordId++ else record.id)
        records += saved
        recordsFlow.value = records.toList()
        return saved.id
    }

    override suspend fun updateRecord(record: MaintenanceRecordEntity): Int {
        val index = records.indexOfFirst { it.id == record.id }
        if (index < 0) return 0
        records[index] = record
        recordsFlow.value = records.toList()
        return 1
    }

    override suspend fun deleteRecordForVehicle(recordId: Long, vehicleId: Long): Int {
        val deleted = records.removeAll { record -> record.id == recordId && rowFor(record, vehicleId) != null }
        if (deleted) recordsFlow.value = records.toList()
        return if (deleted) 1 else 0
    }

    fun cascadeVehicleDelete(vehicleId: Long) {
        val itemIds = items.filter { it.vehicleId == vehicleId }.map { it.id }.toSet()
        if (itemIds.isEmpty()) return
        items.removeAll { it.id in itemIds }
        records.removeAll { it.itemId in itemIds }
        itemsFlow.value = items.toList()
        recordsFlow.value = records.toList()
    }

    private fun rowFor(record: MaintenanceRecordEntity, vehicleId: Long): MaintenanceRecordWithItemRow? {
        val item = items.find { it.id == record.itemId && it.vehicleId == vehicleId } ?: return null
        return MaintenanceRecordWithItemRow(
            id = record.id,
            itemId = record.itemId,
            performedDate = record.performedDate,
            odometerKm = record.odometerKm,
            cost = record.cost,
            notes = record.notes,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            item_id = item.id,
            item_vehicleId = item.vehicleId,
            item_type = item.type,
            item_tyrePosition = item.tyrePosition,
            item_customName = item.customName,
            item_trackingKey = item.trackingKey,
            item_nextDueKm = item.nextDueKm,
            item_nextDueDate = item.nextDueDate,
            item_createdAt = item.createdAt,
            item_updatedAt = item.updatedAt
        )
    }
}
