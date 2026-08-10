package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FuelType {
    GASOLINA,
    ELECTRICO
}

@Entity(tableName = "fuel_entries")
data class FuelEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fecha: Long,

    val cantidad: Double,
    val precio: Double,

    val tipo: FuelType,

    val km: Double,

    val fullTank: Boolean = true
)
