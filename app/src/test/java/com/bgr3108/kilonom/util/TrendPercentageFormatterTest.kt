package com.bgr3108.kilonom.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TrendPercentageFormatterTest {

    private val spanishLocale = Locale.forLanguageTag("es-ES")

    @Test
    fun compactFormat_roundsToNearestWholeNumberWithoutDecimals() {
        assertEquals("7 %", formatTrendPercentageCompact(7.00, spanishLocale))
        assertEquals("7 %", formatTrendPercentageCompact(7.49, spanishLocale))
        assertEquals("8 %", formatTrendPercentageCompact(7.50, spanishLocale))
        assertEquals("12 %", formatTrendPercentageCompact(12.37, spanishLocale))
    }

    @Test
    fun detailedFormat_usesAtMostOneDecimalAndSpanishDecimalSeparator() {
        assertEquals("7 %", formatTrendPercentageDetailed(7.00, spanishLocale))
        assertEquals("7 %", formatTrendPercentageDetailed(7.04, spanishLocale))
        assertEquals("7,3 %", formatTrendPercentageDetailed(7.26, spanishLocale))
        assertEquals("12,4 %", formatTrendPercentageDetailed(12.37, spanishLocale))
        assertEquals("10 %", formatTrendPercentageDetailed(10.00, spanishLocale))
    }

    @Test
    fun formatsUseMagnitudeOnlySoDirectionNeverProducesADuplicateSign() {
        assertEquals("7 %", formatTrendPercentageCompact(-7.00, spanishLocale))
        assertEquals("7,3 %", formatTrendPercentageDetailed(-7.26, spanishLocale))
    }
}
