package com.bgr3108.kilonom.data

import androidx.room.withTransaction
import com.bgr3108.kilonom.database.HybridCarDatabase
import com.bgr3108.kilonom.database.MaintenanceDao
import com.bgr3108.kilonom.domain.availableMaintenanceTypes
import com.bgr3108.kilonom.domain.createMaintenanceTrackingKey
import com.bgr3108.kilonom.domain.MaintenanceNextDue
import com.bgr3108.kilonom.domain.MaintenanceNextDueUpdate
import com.bgr3108.kilonom.domain.resolveNextMaintenanceDueUpdate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Vehicle-scoped persistence boundary for maintenance. It owns structural validation and never
 * trusts a global item or record id without resolving its vehicle parent first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaintenanceRepository(
    private val database: HybridCarDatabase,
    private val maintenanceDao: MaintenanceDao,
    private val vehicleRepository: VehicleRepository
) {
    fun observeActiveItems(): Flow<List<MaintenanceItemEntity>> =
        vehicleRepository.activeVehicle.flatMapLatest { vehicle ->
            vehicle?.let { maintenanceDao.observeItems(it.id) } ?: flowOf(emptyList())
        }

    fun observeActiveRecords(): Flow<List<MaintenanceRecordWithItem>> =
        vehicleRepository.activeVehicle.flatMapLatest { vehicle ->
            vehicle?.let { entity ->
                maintenanceDao.observeRecordsForVehicle(entity.id).map { rows -> rows.map { it.toDomain() } }
            } ?: flowOf(emptyList())
        }

    fun observeRecordsForActiveItem(itemId: Long): Flow<List<MaintenanceRecordEntity>> =
        vehicleRepository.activeVehicle.flatMapLatest { vehicle ->
            vehicle?.let { maintenanceDao.observeRecordsForItem(itemId, it.id) } ?: flowOf(emptyList())
        }

    suspend fun createItemForActiveVehicle(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity? = null
    ): Long = database.withTransaction {
        val vehicle = requireActiveVehicle()
        val normalizedItem = normalizeAndValidateItem(item.copy(vehicleId = vehicle.id, id = 0), vehicle)
        record?.let {
            validateRecord(it.copy(id = 0), vehicle.initialKm)
            validateDueKilometersAfterRecord(normalizedItem, it)
        }
        maintenanceDao.insertItemWithRecord(normalizedItem, record?.copy(id = 0, itemId = 0))
    }

    /** Future form flow: atomically change the item reminder and append one historical record. */
    suspend fun registerRecordForActiveVehicle(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity,
        nextDueUpdate: MaintenanceNextDueUpdate = MaintenanceNextDueUpdate.Manual(
            nextDueKm = item.nextDueKm,
            nextDueDate = item.nextDueDate
        )
    ) = database.withTransaction {
        val vehicle = requireActiveVehicle()
        val existing = maintenanceDao.getItemForVehicle(item.id, vehicle.id)
            ?: error("El mantenimiento no pertenece al vehículo activo")
        val candidateItem = item.copy(
            nextDueKm = null,
            nextDueDate = null,
            vehicleId = vehicle.id,
            id = existing.id,
            createdAt = existing.createdAt,
            trackingKey = ""
        )
        val due = resolveNextDue(candidateItem, record, nextDueUpdate)
        val normalizedItem = normalizeAndValidateItem(
            candidateItem.copy(nextDueKm = due.nextDueKm, nextDueDate = due.nextDueDate),
            vehicle
        )
        validateRecord(record.copy(id = 0, itemId = existing.id), vehicle.initialKm)
        validateDueKilometersAfterRecord(normalizedItem, record)
        maintenanceDao.updateItemWithNewRecord(normalizedItem, record.copy(id = 0, itemId = existing.id))
    }

    suspend fun updateItemForActiveVehicle(item: MaintenanceItemEntity) = database.withTransaction {
        val vehicle = requireActiveVehicle()
        val existing = maintenanceDao.getItemForVehicle(item.id, vehicle.id)
            ?: error("El mantenimiento no pertenece al vehículo activo")
        require(item.type == existing.type && item.tyrePosition == existing.tyrePosition) {
            "No se puede cambiar el tipo ni la posición del seguimiento"
        }
        val normalizedItem = normalizeAndValidateItem(
            item.copy(
                vehicleId = vehicle.id,
                id = existing.id,
                createdAt = existing.createdAt,
                trackingKey = ""
            ),
            vehicle
        )
        maintenanceDao.updateItem(normalizedItem)
    }

    suspend fun deleteItemForActiveVehicle(itemId: Long) = database.withTransaction {
        val vehicle = requireActiveVehicle()
        require(maintenanceDao.deleteItemForVehicle(itemId, vehicle.id) > 0) {
            "El mantenimiento no pertenece al vehículo activo"
        }
    }

    suspend fun updateRecordForActiveVehicle(record: MaintenanceRecordEntity) = database.withTransaction {
        val vehicle = requireActiveVehicle()
        val existing = maintenanceDao.getRecordForVehicle(record.id, vehicle.id)
            ?: error("El registro no pertenece al vehículo activo")
        validateRecord(record.copy(itemId = existing.itemId), vehicle.initialKm)
        maintenanceDao.updateRecord(record.copy(itemId = existing.itemId))
    }

    /** Deletes only history. Item reminders deliberately remain unchanged. */
    suspend fun deleteRecordForActiveVehicle(recordId: Long) = database.withTransaction {
        val vehicle = requireActiveVehicle()
        require(maintenanceDao.deleteRecordForVehicle(recordId, vehicle.id) > 0) {
            "El registro no pertenece al vehículo activo"
        }
    }

    private fun requireActiveVehicle(): VehicleEntity {
        val activeId = vehicleRepository.activeVehicleId.value ?: error("No hay un vehículo activo")
        return vehicleRepository.activeVehicle.value?.takeIf { it.id == activeId }
            ?: error("No hay un vehículo activo")
    }

    private fun normalizeAndValidateItem(
        item: MaintenanceItemEntity,
        vehicle: VehicleEntity
    ): MaintenanceItemEntity {
        require(item.vehicleId > 0) { "Se requiere un vehículo válido" }
        require(item.type in availableMaintenanceTypes(vehicle.category, vehicle.type)) {
            "Este mantenimiento no es compatible con el vehículo"
        }
        require(item.nextDueKm == null || item.nextDueKm >= 0) {
            "El próximo kilometraje no es válido"
        }
        require(item.nextDueDate == null || item.nextDueDate >= 0) {
            "La próxima fecha no es válida"
        }
        require(item.intervalKm == null || item.intervalKm > 0L) {
            "El intervalo de kilometraje debe ser positivo"
        }
        require((item.intervalTimeValue == null) == (item.intervalTimeUnit == null)) {
            "El intervalo temporal requiere valor y unidad"
        }
        require(item.intervalTimeValue == null || item.intervalTimeValue > 0) {
            "El intervalo temporal debe ser positivo"
        }
        require(item.reminderLeadKm >= 0L) {
            "El aviso previo por kilometraje no es válido"
        }
        require(item.reminderLeadDays >= 0L) {
            "El aviso previo por fecha no es válido"
        }
        when (item.type) {
            MaintenanceType.TYRES -> require(item.tyrePosition != null) {
                "La posición de los neumáticos es obligatoria"
            }

            else -> require(item.tyrePosition == null) {
                "La posición de neumáticos solo corresponde a neumáticos"
            }
        }
        val normalizedCustomName = when (item.type) {
            MaintenanceType.OTHER -> item.customName?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("El nombre personalizado es obligatorio")

            else -> {
                require(item.customName == null) { "El nombre personalizado solo corresponde a Otro" }
                null
            }
        }
        val trackingKey = createMaintenanceTrackingKey(
            type = item.type,
            tyrePosition = item.tyrePosition,
            customName = normalizedCustomName
        )
        require(item.trackingKey.isBlank() || item.trackingKey == trackingKey) {
            "La clave de seguimiento no coincide con el mantenimiento"
        }
        return item.copy(customName = normalizedCustomName, trackingKey = trackingKey)
    }

    private fun validateRecord(record: MaintenanceRecordEntity, initialKm: Double) {
        val km = record.odometerKm
        require(km == null || km >= 0L) { "El kilometraje no es válido" }
        require(km == null || km >= initialKm) {
            "El kilometraje no puede ser inferior al kilometraje inicial"
        }
        require(record.cost == null || (record.cost.isFinite() && record.cost >= 0.0)) {
            "El coste no es válido"
        }
    }

    private fun validateDueKilometersAfterRecord(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity
    ) {
        require(
            item.nextDueKm == null || record.odometerKm == null || item.nextDueKm >= record.odometerKm
        ) {
            "El próximo kilometraje no puede ser anterior al mantenimiento realizado"
        }
    }

    private fun resolveNextDue(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity,
        update: MaintenanceNextDueUpdate
    ): MaintenanceNextDue = resolveNextMaintenanceDueUpdate(
        update = update,
        performedDate = record.performedDate,
        performedKm = record.odometerKm,
        intervalKm = item.intervalKm,
        intervalTimeValue = item.intervalTimeValue,
        intervalTimeUnit = item.intervalTimeUnit
    )
}
