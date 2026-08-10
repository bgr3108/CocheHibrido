package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "car")
data class Car(
    @PrimaryKey val id: Int = 1,
    val marca: String,
    val modelo: String,
    val matricula: String,
    val kmActuales: Int
)
