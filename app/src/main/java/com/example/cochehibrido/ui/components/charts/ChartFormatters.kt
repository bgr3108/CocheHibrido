package com.example.cochehibrido.ui.components.charts

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun formatKilometers(value: Double): String {
    val absValue = abs(value)

    return when {
        absValue >= 1_000_000 ->
            String.format(Locale.getDefault(), "%.1fM", value / 1_000_000)
                .replace(".0", "")

        absValue >= 1_000 ->
            String.format(Locale.getDefault(), "%.1fk", value / 1_000)
                .replace(".0", "")

        else ->
            NumberFormat.getIntegerInstance().format(value.toInt())
    }
}

fun formatConsumption(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value)

fun formatCurrency(value: Double): String =
    String.format(Locale.getDefault(), "%.2f €", value)

fun formatPercentage(value: Double): String =
    String.format(Locale.getDefault(), "%.0f%%", value)

fun formatInteger(value: Double): String =
    NumberFormat.getIntegerInstance().format(value.toInt())