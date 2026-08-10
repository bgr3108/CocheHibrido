package com.bgr3108.kilonom.data

enum class VehicleType {
    GASOLINA,
    DIESEL,
    HIBRIDO,
    HIBRIDO_ENCHUFABLE,
    ELECTRICO
}

val VehicleType?.supportsFuelEntries: Boolean
    get() = when (this) {
        VehicleType.GASOLINA,
        VehicleType.DIESEL,
        VehicleType.HIBRIDO,
        VehicleType.HIBRIDO_ENCHUFABLE,
        null -> true

        VehicleType.ELECTRICO -> false
    }

val VehicleType?.supportsElectricEntries: Boolean
    get() = when (this) {
        VehicleType.HIBRIDO_ENCHUFABLE,
        VehicleType.ELECTRICO -> true

        VehicleType.GASOLINA,
        VehicleType.DIESEL,
        VehicleType.HIBRIDO,
        null -> false
    }
