package com.bgr3108.kilonom.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bgr3108.kilonom.domain.MaintenanceDueMeasure
import com.bgr3108.kilonom.domain.MaintenanceHomeInsight
import com.bgr3108.kilonom.domain.MaintenanceHomeInsightType
import java.util.Locale
import kotlin.math.abs

internal fun MaintenanceHomeInsight.homeTitle(): String = when (type) {
    MaintenanceHomeInsightType.SINGLE_OVERDUE,
    MaintenanceHomeInsightType.MULTIPLE_OVERDUE -> "Mantenimiento pendiente"

    MaintenanceHomeInsightType.SINGLE_DUE_SOON -> "Próximo mantenimiento"
    MaintenanceHomeInsightType.MULTIPLE_DUE_SOON -> "Próximos mantenimientos"
    else -> "Mantenimiento"
}

internal fun MaintenanceHomeInsight.homeMessage(locale: Locale = Locale.getDefault()): String = when (type) {
    MaintenanceHomeInsightType.NO_ITEMS -> "Aún no has añadido mantenimientos."
    MaintenanceHomeInsightType.NO_REMINDERS -> "No hay próximos avisos configurados."
    MaintenanceHomeInsightType.ALL_UP_TO_DATE -> "Todo al día."
    MaintenanceHomeInsightType.MULTIPLE_OVERDUE -> "Tienes $count elementos pendientes."
    MaintenanceHomeInsightType.MULTIPLE_DUE_NOW -> "Tienes $count elementos que tocan ahora."
    MaintenanceHomeInsightType.MULTIPLE_DUE_SOON -> "Tienes $count avisos próximos."
    MaintenanceHomeInsightType.SINGLE_OVERDUE -> {
        val prefix = requireNotNull(itemName)
        "$prefix: venció hace ${formattedAmount(locale, absolute = true)}."
    }
    MaintenanceHomeInsightType.SINGLE_DUE_NOW -> {
        val suffix = if (dueMeasure == MaintenanceDueMeasure.DATE) "toca hoy" else "toca ahora"
        "${requireNotNull(itemName)} $suffix."
    }
    MaintenanceHomeInsightType.SINGLE_DUE_SOON ->
        "${requireNotNull(itemName)} en ${formattedAmount(locale)}."
}

@Composable
internal fun MaintenanceHomeInsight.homeAccentColor(): Color = when (type) {
    MaintenanceHomeInsightType.SINGLE_OVERDUE,
    MaintenanceHomeInsightType.MULTIPLE_OVERDUE -> MaterialTheme.colorScheme.error

    MaintenanceHomeInsightType.SINGLE_DUE_NOW,
    MaintenanceHomeInsightType.MULTIPLE_DUE_NOW,
    MaintenanceHomeInsightType.SINGLE_DUE_SOON,
    MaintenanceHomeInsightType.MULTIPLE_DUE_SOON -> MaterialTheme.colorScheme.primary

    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
internal fun MaintenanceHomeInsight.homeMessageColor(): Color = when (type) {
    MaintenanceHomeInsightType.SINGLE_OVERDUE,
    MaintenanceHomeInsightType.MULTIPLE_OVERDUE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
}

private fun MaintenanceHomeInsight.formattedAmount(locale: Locale, absolute: Boolean = false): String {
    val value = requireNotNull(amount)
    val displayValue = if (absolute) abs(value) else value
    return when (dueMeasure) {
        MaintenanceDueMeasure.KILOMETERS -> "${displayValue.formatKilometersForHome(locale)} km"
        MaintenanceDueMeasure.DATE -> "$displayValue ${if (displayValue == 1L) "día" else "días"}"
        null -> error("A single maintenance insight needs a due measure")
    }
}

private fun Long.formatKilometersForHome(locale: Locale): String =
    java.text.NumberFormat.getIntegerInstance(locale).format(this)
