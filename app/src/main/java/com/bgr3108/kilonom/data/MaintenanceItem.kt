package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MaintenanceType {
    OIL_AND_FILTER,
    BRAKES,
    TYRES,
    BATTERY_12V,
    GENERAL_SERVICE,
    CHAIN_AND_DRIVETRAIN,
    ITV,
    INSURANCE,
    CIRCULATION_TAX,
    OTHER
}

enum class TyrePosition {
    ALL,
    FRONT,
    REAR
}

@Entity(
    tableName = "maintenance_items",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["vehicleId", "trackingKey"], unique = true)
    ]
)
data class MaintenanceItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val type: MaintenanceType,
    val tyrePosition: TyrePosition? = null,
    val customName: String? = null,
    val trackingKey: String,
    val nextDueKm: Long? = null,
    val nextDueDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
