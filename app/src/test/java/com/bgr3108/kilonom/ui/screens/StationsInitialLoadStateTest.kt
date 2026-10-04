package com.bgr3108.kilonom.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsInitialLoadStateTest {
    @Test
    fun `empty cache while first refresh runs shows the preparation state`() {
        assertTrue(shouldShowInitialFuelStationsLoading(hasCache = false, refreshing = true))
    }

    @Test
    fun `arriving cached data hides the preparation state`() {
        assertFalse(shouldShowInitialFuelStationsLoading(hasCache = true, refreshing = true))
    }

    @Test
    fun `background refresh with an existing cache keeps normal content visible`() {
        assertFalse(shouldShowInitialFuelStationsLoading(hasCache = true, refreshing = true))
    }

    @Test
    fun `failed initial refresh cannot leave the preparation state visible`() {
        assertFalse(shouldShowInitialFuelStationsLoading(hasCache = false, refreshing = false))
    }

    @Test
    fun `normal cached opening does not show the preparation state`() {
        assertFalse(shouldShowInitialFuelStationsLoading(hasCache = true, refreshing = false))
    }
}
