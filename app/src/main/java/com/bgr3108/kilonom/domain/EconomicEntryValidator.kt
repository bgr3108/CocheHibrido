package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType

fun isValidEconomicEntry(entry: FuelEntry): Boolean =
    entry.cantidad.isFinite() &&
            entry.cantidad > 0.0 &&
            entry.precio.isFinite() &&
            entry.precio >= 0.0

fun validEconomicEntries(
    entries: List<FuelEntry>,
    type: FuelType? = null
): List<FuelEntry> = entries.filter { entry ->
    isValidEconomicEntry(entry) && (type == null || entry.tipo == type)
}

fun sumValidEconomicValues(
    entries: List<FuelEntry>,
    type: FuelType? = null,
    value: (FuelEntry) -> Double
): Double = validEconomicEntries(entries, type).fold(0.0) { total, entry ->
    val entryValue = value(entry)
    if (!entryValue.isFinite()) {
        total
    } else {
        (total + entryValue).takeIf { it.isFinite() } ?: total
    }
}
