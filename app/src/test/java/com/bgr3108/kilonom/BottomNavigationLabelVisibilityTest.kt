package com.bgr3108.kilonom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationLabelVisibilityTest {

    @Test
    fun normalFontScaleKeepsLabelsVisibleForAllDestinations() {
        assertTrue(shouldShowAllBottomNavigationLabels(1f))
        assertTrue(shouldShowAllBottomNavigationLabels(1.15f))
    }

    @Test
    fun largerFontScaleShowsOnlyTheSelectedNavigationLabel() {
        assertFalse(shouldShowAllBottomNavigationLabels(1.16f))
        assertFalse(shouldShowAllBottomNavigationLabels(1.5f))
    }
}
