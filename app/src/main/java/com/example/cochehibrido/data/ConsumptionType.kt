package com.example.cochehibrido.data

enum class ConsumptionType {
    GASOLINA,
    ELECTRICO;

    fun displayName(): String {
        return when (this) {
            GASOLINA -> "Gasolina"
            ELECTRICO -> "Electrico"
        }
    }

    fun quantityLabel(): String {
        return when (this) {
            GASOLINA -> "Litros"
            ELECTRICO -> "kWh"
        }
    }
}
