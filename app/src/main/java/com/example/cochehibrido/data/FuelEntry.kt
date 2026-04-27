package com.example.cochehibrido.data

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

    val fecha: String,

    // Cantidad:
    // gasolina → litros
    // eléctrico → kWh
    val cantidad: Double,

    // Precio total (€)
    val precio: Double,

    // Tipo de energía
    val tipo: FuelType,

    // 🔥 NUEVO → KM del coche en ese momento (odómetro)
    val km: Double
)