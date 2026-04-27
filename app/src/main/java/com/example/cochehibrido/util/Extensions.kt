package com.example.cochehibrido.util

fun String.toDoubleSafe(): Double {
    return this.replace(",", ".").toDoubleOrNull() ?: 0.0
}

fun Double.toSpanishDecimal(): String {
    return String.format("%.2f", this).replace(".", ",")
}