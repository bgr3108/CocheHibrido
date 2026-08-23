package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FuelType {
    GASOLINA,
    ELECTRICO
}

@Entity(
    tableName = "fuel_entries",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId", "fecha"])]
)
data class FuelEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fecha: Long,

    val cantidad: Double,
    val precio: Double,

    val tipo: FuelType,

    val km: Double,

    val fullTank: Boolean = true,

    /** Fraction of the tank estimated after a fuel entry, or null for historical/unknown data. */
    val fuelLevelAfter: Double? = null,

    val vehicleId: Long
)
