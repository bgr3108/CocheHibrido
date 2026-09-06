package com.bgr3108.kilonom.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceDetailLayoutTest {

    @Test
    fun normalFontScaleAndPhoneWidth_keepActionsInOneRow() {
        assertFalse(shouldStackMaintenanceDetailActions(fontScale = 1f, availableWidth = 411.dp))
    }

    @Test
    fun largeFontScale_stacksActionsToKeepTheEditLabelIntact() {
        assertTrue(shouldStackMaintenanceDetailActions(fontScale = 1.3f, availableWidth = 411.dp))
    }

    @Test
    fun narrowWidths_stackActionsEvenAtNormalFontScale() {
        assertTrue(shouldStackMaintenanceDetailActions(fontScale = 1f, availableWidth = 320.dp))
    }
}
