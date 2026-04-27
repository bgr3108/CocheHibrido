package com.example.cochehibrido.database

import androidx.room.TypeConverter
import com.example.cochehibrido.data.ConsumptionType

class Converters {
    @TypeConverter
    fun toConsumptionType(value: String): ConsumptionType = ConsumptionType.valueOf(value)

    @TypeConverter
    fun fromConsumptionType(type: ConsumptionType): String = type.name
}
