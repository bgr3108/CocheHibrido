package com.bgr3108.kilonom.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.stations.StationsViewMode

class KilonomNavigationDrawerTest {
    @Test
    fun drawer_containsAllMainAndGlobalDestinations() {
        val labels = drawerNavigationItems(showAdPrivacyOptions = false).map { it.label }

        assertEquals(
            listOf(
                "Inicio", "Consumos", "Estadísticas", "Mantenimiento", "Estaciones",
                "Mis vehículos", "Guía de uso", "Privacidad", "Instagram"
            ),
            labels
        )
    }

    @Test
    fun advertisingPreferences_areVisibleOnlyWhenRequired() {
        assertFalse(drawerNavigationItems(false).any { it.action == DrawerAction.AD_PRIVACY_OPTIONS })
        assertTrue(drawerNavigationItems(true).any { it.action == DrawerAction.AD_PRIVACY_OPTIONS })
    }

    @Test
    fun statisticsDestination_isSelectedForTrendsButNotSecondaryDetails() {
        val stats = drawerNavigationItems(false).first { it.route == "stats" }

        assertTrue(isDrawerItemSelected(stats, "stats"))
        assertTrue(isDrawerItemSelected(stats, "stats/trends"))
        assertFalse(isDrawerItemSelected(stats, "cost_detail"))
    }

    @Test
    fun onlyTopLevelRoutesExposeTheDrawer() {
        assertTrue(isDrawerTopLevelRoute("stations"))
        assertTrue(isDrawerTopLevelRoute("privacy"))
        assertFalse(isDrawerTopLevelRoute("maintenance/item/3"))
        assertFalse(isDrawerTopLevelRoute("add_refuel"))
    }

    @Test
    fun selectingTheCurrentDestination_doesNotNavigateAgain() {
        assertFalse(shouldNavigateToDrawerRoute("maintenance", "maintenance"))
        assertTrue(shouldNavigateToDrawerRoute("maintenance", "stations"))
    }

    @Test
    fun drawerWidth_usesMobilePercentageWithoutExceedingMaximum() {
        assertEquals(280.8f, calculateDrawerWidth(360.dp).value, 0.01f)
        assertEquals(320f, calculateDrawerWidth(412.dp).value, 0.01f)
    }

    @Test
    fun back_closesAnOpenDrawerBeforeNavigationCanHandleIt() {
        assertTrue(shouldCloseDrawerOnBack(true))
    }

    @Test
    fun closedDrawer_defersBackToTheExistingNavigationBehavior() {
        assertFalse(shouldCloseDrawerOnBack(false))
    }

    @Test
    fun mapDisablesOnlyTheDrawerEdgeGestureWhileTheMenuButtonRemainsAvailable() {
        assertFalse(drawerGesturesEnabled("stations", StationsViewMode.MAP))
        assertTrue(drawerGesturesEnabled("stations", StationsViewMode.LIST))
        assertTrue(drawerGesturesEnabled("maintenance", StationsViewMode.MAP))
    }

    @Test
    fun vehicleOverflow_isAvailableOnlyOnMyVehicles() {
        assertTrue(showsVehicleTopOverflow("my_vehicles"))
        assertFalse(showsVehicleTopOverflow("home"))
        assertFalse(showsVehicleTopOverflow("maintenance"))
    }
}
