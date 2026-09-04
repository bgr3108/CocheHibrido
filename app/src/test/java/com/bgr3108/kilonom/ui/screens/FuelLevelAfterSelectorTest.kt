package com.bgr3108.kilonom.ui.screens

import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.fuelLevelAfterSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelLevelAfterSelectorTest {

    @Test
    fun normalWidthAndFontScaleKeepAllNineLevelsInTheFixedLayout() {
        assertFalse(shouldUseScrollableFuelLevelSelector(312.dp, fontScale = 1f))
    }

    @Test
    fun largerFontOrNarrowerSpaceUsesTheScrollableLayout() {
        assertTrue(shouldUseScrollableFuelLevelSelector(312.dp, fontScale = 1.3f))
        assertTrue(shouldUseScrollableFuelLevelSelector(280.dp, fontScale = 1f))
    }

    @Test
    fun everyFuelLevelHasItsOwnStableLabelIncludingBothExtremes() {
        assertEquals(
            listOf("Vacío", "1/8", "1/4", "3/8", "1/2", "5/8", "3/4", "7/8", "Lleno"),
            fuelLevelAfterSteps.indices.map(::fuelLevelAfterLabel)
        )
        assertEquals(0.0, fuelLevelAfterSteps.first(), 0.0)
        assertEquals(0.5, fuelLevelAfterSteps[4], 0.0)
        assertEquals(1.0, fuelLevelAfterSteps.last(), 0.0)
    }
}
