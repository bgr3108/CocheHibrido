package com.bgr3108.kilonom.util

import java.util.Locale
import kotlin.math.floor

private val decimalNumberPattern = Regex("[-+]?\\d+(?:[,.]\\d+)?")

fun String.toFiniteDoubleOrNull(): Double? {
    val normalized = trim()

    if (!decimalNumberPattern.matches(normalized)) return null

    return normalized
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }
}

/**
 * Kilonom almacena el kilometraje histórico como [Double], pero los nuevos
 * valores de kilometraje siempre deben ser enteros no negativos.
 */
fun String.toKilometersOrNull(): Double? {
    val normalized = trim()

    if (normalized.isEmpty() || !normalized.all(Char::isDigit)) return null

    return normalized.toLongOrNull()?.toDouble()
}

fun Double.toKilometersInput(): String {
    return toWholeKilometersOrNull()?.toString().orEmpty()
}

fun Double.toKilometersDisplay(): String {
    return toWholeKilometersOrNull()?.toString() ?: "—"
}

private fun Double.toWholeKilometersOrNull(): Long? {
    if (!isFinite() || this < 0.0 || this > Long.MAX_VALUE.toDouble()) return null
    if (this != floor(this)) return null

    return toLong()
}

fun Double.toSpanishDecimal(): String {
    return String.format(Locale.getDefault(), "%.2f", this).replace(".", ",")
}
