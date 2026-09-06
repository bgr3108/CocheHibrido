package com.bgr3108.kilonom.data

import androidx.room.ColumnInfo
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

/** Calendar-preserving unit for a recurring maintenance interval. */
enum class MaintenanceTimeUnit {
    DAYS,
    MONTHS,
    YEARS
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
    val updatedAt: Long,
    val intervalKm: Long? = null,
    val intervalTimeValue: Int? = null,
    val intervalTimeUnit: MaintenanceTimeUnit? = null,
    @ColumnInfo(defaultValue = "1000")
    val reminderLeadKm: Long = DEFAULT_REMINDER_LEAD_KM,
    @ColumnInfo(defaultValue = "30")
    val reminderLeadDays: Long = DEFAULT_REMINDER_LEAD_DAYS
)

const val DEFAULT_REMINDER_LEAD_KM = 1_000L
const val DEFAULT_REMINDER_LEAD_DAYS = 30L
