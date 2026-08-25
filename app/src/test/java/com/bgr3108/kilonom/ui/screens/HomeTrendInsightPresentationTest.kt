package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.domain.HomeTrendInsight
import com.bgr3108.kilonom.domain.HomeTrendMetric
import com.bgr3108.kilonom.domain.TrendMagnitude
import com.bgr3108.kilonom.domain.TrendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class HomeTrendInsightPresentationTest {

    @Test
    fun downwardPhevEnergyMessage_containsAnUnsignedCompactPercentage() {
        val message = HomeTrendInsight(
            metric = HomeTrendMetric.PHEV_ENERGY_CHARGED,
            status = TrendStatus.DOWN,
            magnitude = TrendMagnitude.SLIGHT,
            percentageChange = -7.0
        ).toHomeTrendDescription(Locale.forLanguageTag("es-ES"))

        assertEquals(
            "La energía cargada por cada 100 km ha bajado un 7 %.",
            message
        )
        assertFalse(message.contains("-7 %"))
        assertFalse(message.contains("+7 %"))
    }
}
