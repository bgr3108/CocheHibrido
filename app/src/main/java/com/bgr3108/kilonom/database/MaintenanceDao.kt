package com.bgr3108.kilonom.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.data.MaintenanceRecordWithItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId ORDER BY createdAt ASC, id ASC")
    fun observeItems(vehicleId: Long): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT * FROM maintenance_items WHERE id = :itemId AND vehicleId = :vehicleId LIMIT 1")
    suspend fun getItemForVehicle(itemId: Long, vehicleId: Long): MaintenanceItemEntity?

    @Query(
        """
        SELECT records.* FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE records.itemId = :itemId AND items.vehicleId = :vehicleId
        ORDER BY records.performedDate DESC, records.createdAt DESC
        """
    )
    fun observeRecordsForItem(itemId: Long, vehicleId: Long): Flow<List<MaintenanceRecordEntity>>

    @Query(
        """
        SELECT records.*,
               items.id AS item_id, items.vehicleId AS item_vehicleId, items.type AS item_type,
               items.tyrePosition AS item_tyrePosition, items.customName AS item_customName,
               items.trackingKey AS item_trackingKey, items.nextDueKm AS item_nextDueKm,
               items.nextDueDate AS item_nextDueDate, items.createdAt AS item_createdAt,
               items.updatedAt AS item_updatedAt
        FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId
        ORDER BY records.performedDate DESC, records.createdAt DESC
        """
    )
    fun observeRecordsForVehicle(vehicleId: Long): Flow<List<MaintenanceRecordWithItemRow>>

    @Query(
        """
        SELECT records.*,
               items.id AS item_id, items.vehicleId AS item_vehicleId, items.type AS item_type,
               items.tyrePosition AS item_tyrePosition, items.customName AS item_customName,
               items.trackingKey AS item_trackingKey, items.nextDueKm AS item_nextDueKm,
               items.nextDueDate AS item_nextDueDate, items.createdAt AS item_createdAt,
               items.updatedAt AS item_updatedAt
        FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE records.id = :recordId AND items.vehicleId = :vehicleId
        LIMIT 1
        """
    )
    suspend fun getRecordForVehicle(recordId: Long, vehicleId: Long): MaintenanceRecordWithItemRow?

    @Query(
        """
        SELECT odometerKm FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId AND odometerKm IS NOT NULL AND odometerKm >= 0
        """
    )
    fun observeOdometerKilometersForVehicle(vehicleId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT odometerKm FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId AND odometerKm IS NOT NULL AND odometerKm >= 0
        """
    )
    suspend fun getOdometerKilometersForVehicle(vehicleId: Long): List<Long>

    @Query(
        """
        SELECT MAX(records.odometerKm) FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId AND records.odometerKm IS NOT NULL AND records.odometerKm >= 0
        """
    )
    suspend fun getMaximumOdometerKmForVehicle(vehicleId: Long): Long?

    @Query(
        """
        SELECT MIN(records.odometerKm) FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId AND records.odometerKm IS NOT NULL AND records.odometerKm >= 0
        """
    )
    suspend fun getMinimumOdometerKmForVehicle(vehicleId: Long): Long?

    @Query(
        """
        SELECT COUNT(*) FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId
        """
    )
    suspend fun countRecordsForVehicle(vehicleId: Long): Int

    @Query("SELECT COUNT(*) FROM maintenance_items WHERE vehicleId = :vehicleId")
    suspend fun countItemsForVehicle(vehicleId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM maintenance_records AS records
        INNER JOIN maintenance_items AS items ON items.id = records.itemId
        WHERE items.vehicleId = :vehicleId
        """
    )
    fun observeRecordCountForVehicle(vehicleId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: MaintenanceItemEntity): Long

    @Update
    suspend fun updateItem(item: MaintenanceItemEntity): Int

    @Query("DELETE FROM maintenance_items WHERE id = :itemId AND vehicleId = :vehicleId")
    suspend fun deleteItemForVehicle(itemId: Long, vehicleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: MaintenanceRecordEntity): Long

    @Update
    suspend fun updateRecord(record: MaintenanceRecordEntity): Int

    @Query(
        """
        DELETE FROM maintenance_records
        WHERE id = :recordId
          AND itemId IN (SELECT id FROM maintenance_items WHERE vehicleId = :vehicleId)
        """
    )
    suspend fun deleteRecordForVehicle(recordId: Long, vehicleId: Long): Int

    @Transaction
    suspend fun insertItemWithRecord(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity?
    ): Long {
        val itemId = insertItem(item)
        record?.let { insertRecord(it.copy(itemId = itemId)) }
        return itemId
    }

    @Transaction
    suspend fun updateItemWithNewRecord(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordEntity
    ) {
        updateItem(item)
        insertRecord(record.copy(itemId = item.id))
    }
}

/** Room projection kept internal to the database package; the repository exposes domain values. */
data class MaintenanceRecordWithItemRow(
    val id: Long,
    val itemId: Long,
    val performedDate: Long?,
    val odometerKm: Long?,
    val cost: Double?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val item_id: Long,
    val item_vehicleId: Long,
    val item_type: com.bgr3108.kilonom.data.MaintenanceType,
    val item_tyrePosition: com.bgr3108.kilonom.data.TyrePosition?,
    val item_customName: String?,
    val item_trackingKey: String,
    val item_nextDueKm: Long?,
    val item_nextDueDate: Long?,
    val item_createdAt: Long,
    val item_updatedAt: Long
) {
    fun toDomain(): MaintenanceRecordWithItem = MaintenanceRecordWithItem(
        record = MaintenanceRecordEntity(id, itemId, performedDate, odometerKm, cost, notes, createdAt, updatedAt),
        item = MaintenanceItemEntity(
            id = item_id,
            vehicleId = item_vehicleId,
            type = item_type,
            tyrePosition = item_tyrePosition,
            customName = item_customName,
            trackingKey = item_trackingKey,
            nextDueKm = item_nextDueKm,
            nextDueDate = item_nextDueDate,
            createdAt = item_createdAt,
            updatedAt = item_updatedAt
        )
    )
}
