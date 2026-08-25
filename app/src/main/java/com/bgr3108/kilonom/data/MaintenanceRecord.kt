package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "maintenance_records",
    foreignKeys = [
        ForeignKey(
            entity = MaintenanceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["itemId", "performedDate"])]
)
data class MaintenanceRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val performedDate: Long? = null,
    val odometerKm: Long? = null,
    val cost: Double? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** A vehicle-scoped historical record, returned by the DAO through a safe item join. */
data class MaintenanceRecordWithItem(
    val record: MaintenanceRecordEntity,
    val item: MaintenanceItemEntity
)
