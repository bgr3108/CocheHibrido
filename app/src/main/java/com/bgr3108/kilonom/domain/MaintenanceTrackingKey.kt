package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition
import java.text.Normalizer
import java.util.Locale

fun createMaintenanceTrackingKey(
    type: MaintenanceType,
    tyrePosition: TyrePosition?,
    customName: String?
): String = when (type) {
    MaintenanceType.TYRES -> "${type.name}:${requireNotNull(tyrePosition).name}"
    MaintenanceType.OTHER -> "${type.name}:${normalizeMaintenanceCustomName(requireNotNull(customName))}"
    else -> type.name
}

fun normalizeMaintenanceCustomName(value: String): String {
    val trimmed = value.trim().replace(Regex("\\s+"), " ")
    require(trimmed.isNotEmpty()) { "El nombre personalizado es obligatorio" }
    return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase(Locale.ROOT)
}
