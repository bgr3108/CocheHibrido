package com.bgr3108.kilonom.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBannerSlotTest {

    @Test
    fun mainRoutes_useTheReservedBannerSlot() {
        listOf("home", "consumption", "stats", "stats/trends", "maintenance").forEach { route ->
            assertTrue(routeUsesAdBannerSlot(route))
        }
    }

    @Test
    fun secondaryRoutes_doNotUseTheReservedBannerSlot() {
        listOf("my_vehicles", "maintenance_detail/3", "usage_guide", null).forEach { route ->
            assertFalse(routeUsesAdBannerSlot(route))
        }
    }

    @Test
    fun onlyDebugBuilds_reservePlaceholderHeight() {
        assertEquals(AdBannerSlotHeight, adBannerSlotHeight(isDebugBuild = true))
        assertEquals(0.dp, adBannerSlotHeight(isDebugBuild = false))
    }
}
