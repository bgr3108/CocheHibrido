package com.example.cochehibrido.util

import java.util.Locale

private val decimalNumberPattern = Regex("[-+]?\\d+(?:[,.]\\d+)?")

fun String.toFiniteDoubleOrNull(): Double? {
    val normalized = trim()

    if (!decimalNumberPattern.matches(normalized)) return null

    return normalized
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
}

fun Double.toSpanishDecimal(): String {
    return String.format(Locale.getDefault(), "%.2f", this).replace(".", ",")
}
