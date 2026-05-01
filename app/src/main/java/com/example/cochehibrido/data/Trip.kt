package com.example.cochehibrido.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fecha: Long,

    // 🔥 CAMBIO IMPORTANTE → ahora Double
    val km: Double,

    // Consumo gasolina (L/100km)
    val consumoGasolina: Double,

    // Consumo eléctrico (kWh/100km)
    val consumoElectrico: Double
)