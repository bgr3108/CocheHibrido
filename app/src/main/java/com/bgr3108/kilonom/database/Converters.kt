package com.bgr3108.kilonom.database

import androidx.room.TypeConverter
import com.bgr3108.kilonom.data.ConsumptionType

class Converters {
    @TypeConverter
    fun toConsumptionType(value: String): ConsumptionType = ConsumptionType.valueOf(value)

    @TypeConverter
    fun fromConsumptionType(type: ConsumptionType): String = type.name
}
