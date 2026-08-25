package com.bgr3108.kilonom.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/** Presentation-only formatting for trend deltas. Direction is conveyed by surrounding text or arrows. */
fun formatTrendPercentageCompact(
    percentageChange: Double,
    locale: Locale = Locale.getDefault()
): String = String.format(
    locale,
    "%.0f %%",
    roundedAbsolutePercentage(percentageChange, scale = 0).toDouble()
)

fun formatTrendPercentageDetailed(
    percentageChange: Double,
    locale: Locale = Locale.getDefault()
): String {
    val rounded = roundedAbsolutePercentage(percentageChange, scale = 1)
    val scale = if (rounded.stripTrailingZeros().scale() <= 0) 0 else 1
    return String.format(locale, "%.${scale}f %%", rounded.toDouble())
}

private fun roundedAbsolutePercentage(percentageChange: Double, scale: Int): BigDecimal {
    require(percentageChange.isFinite()) { "A trend percentage must be finite." }
    return BigDecimal.valueOf(abs(percentageChange)).setScale(scale, RoundingMode.HALF_UP)
}
