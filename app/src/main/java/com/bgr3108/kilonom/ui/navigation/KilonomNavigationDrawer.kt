package com.bgr3108.kilonom.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.stations.StationsViewMode

internal enum class DrawerSection(val title: String) {
    PRIMARY("Principal"),
    VEHICLES("Vehículos"),
    HELP_AND_SETTINGS("Ayuda y ajustes")
}

internal enum class DrawerAction {
    NAVIGATE,
    AD_PRIVACY_OPTIONS,
    INSTAGRAM
}

internal data class DrawerItemSpec(
    val label: String,
    val icon: ImageVector,
    val section: DrawerSection,
    val action: DrawerAction = DrawerAction.NAVIGATE,
    val route: String? = null
)

internal fun drawerNavigationItems(showAdPrivacyOptions: Boolean): List<DrawerItemSpec> = buildList {
    add(DrawerItemSpec("Inicio", Icons.Default.Home, DrawerSection.PRIMARY, route = "home"))
    add(DrawerItemSpec("Consumos", Icons.Default.LocalGasStation, DrawerSection.PRIMARY, route = "consumption"))
    add(DrawerItemSpec("Estadísticas", Icons.Default.BarChart, DrawerSection.PRIMARY, route = "stats"))
    add(DrawerItemSpec("Mantenimiento", Icons.Default.Build, DrawerSection.PRIMARY, route = "maintenance"))
    add(DrawerItemSpec("Estaciones", Icons.Default.LocalGasStation, DrawerSection.PRIMARY, route = "stations"))
    add(DrawerItemSpec("Mis vehículos", Icons.Default.DirectionsCar, DrawerSection.VEHICLES, route = "my_vehicles"))
    add(DrawerItemSpec("Guía de uso", Icons.AutoMirrored.Filled.MenuBook, DrawerSection.HELP_AND_SETTINGS, route = "usage_guide"))
    add(DrawerItemSpec("Privacidad", Icons.Default.PrivacyTip, DrawerSection.HELP_AND_SETTINGS, route = "privacy"))
    if (showAdPrivacyOptions) {
        add(DrawerItemSpec("Preferencias de publicidad", Icons.Default.Tune, DrawerSection.HELP_AND_SETTINGS, DrawerAction.AD_PRIVACY_OPTIONS))
    }
    add(DrawerItemSpec("Instagram", Icons.AutoMirrored.Filled.OpenInNew, DrawerSection.HELP_AND_SETTINGS, DrawerAction.INSTAGRAM))
}

internal fun isDrawerTopLevelRoute(route: String?): Boolean = route in setOf(
    "home", "consumption", "stats", "stats/trends", "maintenance", "stations", "my_vehicles", "usage_guide", "privacy"
)

/** MapLibre owns horizontal pans while the map is visible; the menu still opens from its button. */
internal fun drawerGesturesEnabled(route: String?, stationsViewMode: StationsViewMode): Boolean =
    isDrawerTopLevelRoute(route) && !(route == "stations" && stationsViewMode == StationsViewMode.MAP)

internal fun drawerRouteTitle(route: String?): String? = when (route) {
    "home" -> "Inicio"
    "consumption" -> "Consumos"
    "stats", "stats/trends" -> "Estadísticas"
    "maintenance" -> "Mantenimiento"
    "stations" -> "Estaciones"
    "my_vehicles" -> "Mis vehículos"
    "usage_guide" -> "Guía de uso"
    "privacy" -> "Privacidad"
    else -> null
}

internal fun isDrawerItemSelected(item: DrawerItemSpec, currentRoute: String?): Boolean =
    item.action == DrawerAction.NAVIGATE && when (item.route) {
        "stats" -> currentRoute == "stats" || currentRoute == "stats/trends"
        else -> item.route == currentRoute
    }

internal fun shouldNavigateToDrawerRoute(currentRoute: String?, targetRoute: String): Boolean =
    currentRoute != targetRoute

internal fun shouldCloseDrawerOnBack(isDrawerOpen: Boolean): Boolean = isDrawerOpen

internal fun showsVehicleTopOverflow(route: String?): Boolean = route == "my_vehicles"

internal fun calculateDrawerWidth(availableWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    minOf(availableWidth * 0.78f, 320.dp)

@Composable
internal fun KilonomNavigationDrawerContent(
    currentRoute: String?,
    showAdPrivacyOptions: Boolean,
    onItemSelected: (DrawerItemSpec) -> Unit
) {
    val items = drawerNavigationItems(showAdPrivacyOptions)
    val density = LocalDensity.current
    val availableWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    ModalDrawerSheet(
        modifier = Modifier.width(calculateDrawerWidth(availableWidth)),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Kilonom",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)
            )
            DrawerSection.entries.forEach { section ->
                val sectionItems = items.filter { it.section == section }
                if (sectionItems.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp)
                    )
                    sectionItems.forEach { item ->
                        NavigationDrawerItem(
                            label = { Text(item.label) },
                            selected = isDrawerItemSelected(item, currentRoute),
                            onClick = { onItemSelected(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }
    }
}
