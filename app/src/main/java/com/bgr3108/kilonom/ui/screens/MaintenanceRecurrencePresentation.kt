package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceTimeUnit

/** Compact, user-facing recurrence copy shared by maintenance detail and future list surfaces. */
internal fun MaintenanceItemEntity.recurrenceSummary(): String? {
    val kilometrePart = intervalKm?.let { "Cada ${it.formatKilometers()} km" }
    val timePart = if (intervalTimeValue != null && intervalTimeUnit != null) {
        "$intervalTimeValue ${intervalTimeUnit.quantityLabel(intervalTimeValue)}"
    } else {
        null
    }
    return when {
        kilometrePart != null && timePart != null -> "$kilometrePart o $timePart"
        kilometrePart != null -> kilometrePart
        timePart != null -> "Cada $timePart"
        else -> null
    }
}

internal fun MaintenanceItemEntity.reminderLeadSummaries(): List<String> = buildList {
    if (intervalKm != null || nextDueKm != null) {
        add("Aviso: ${reminderLeadKm.formatKilometers()} km antes")
    }
    if (intervalTimeValue != null || nextDueDate != null) {
        add("Aviso: $reminderLeadDays ${if (reminderLeadDays == 1L) "día" else "días"} antes")
    }
}

internal fun MaintenanceTimeUnit.quantityLabel(value: Int): String = when (this) {
    MaintenanceTimeUnit.DAYS -> if (value == 1) "día" else "días"
    MaintenanceTimeUnit.MONTHS -> if (value == 1) "mes" else "meses"
    MaintenanceTimeUnit.YEARS -> if (value == 1) "año" else "años"
}
