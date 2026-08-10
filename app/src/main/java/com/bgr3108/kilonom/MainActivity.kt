package com.bgr3108.kilonom

// 🔥 ICONOS (esto es lo que te faltaba)


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bgr3108.kilonom.ui.navigation.HybridCarNavHost
import com.bgr3108.kilonom.ui.screens.SetupScreen
import com.bgr3108.kilonom.ui.theme.CocheHibridoTheme
import com.bgr3108.kilonom.viewmodel.AppViewModelProvider
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val fuelViewModel: FuelEntryViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val homeViewModel: HomeViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CocheHibridoTheme {
                AppContent(
                    fuelViewModel = fuelViewModel,
                    homeViewModel = homeViewModel
                )
            }
        }
    }
}

@Composable
fun AppContent(
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel
) {
    val navController = rememberNavController()
    val isVehicleLoading by homeViewModel
        .isVehicleLoading
        .collectAsStateWithLifecycle()
    val vehicle by homeViewModel.vehicle.collectAsStateWithLifecycle()

    if (isVehicleLoading) {

        CircularProgressIndicator()

    } else if (vehicle.type == null) {

        SetupScreen(
            homeViewModel = homeViewModel,

            onDone = {
            }
        )

    } else {

        val navBackStackEntry by navController.currentBackStackEntryAsState()

        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in listOf(
            "home",
            "consumption",
            "stats"
        )
        val navigateToTopLevel: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "home",
                            onClick = { navigateToTopLevel("home") },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Inicio") }
                        )

                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "consumption",
                            onClick = { navigateToTopLevel("consumption") },
                            icon = {
                                Icon(
                                    Icons.Default.LocalGasStation,
                                    contentDescription = null
                                )
                            },
                            label = { Text("Consumos") }
                        )
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "stats",
                            onClick = { navigateToTopLevel("stats") },
                            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                            label = { Text("Estadísticas") }
                        )
                    }
                }
            }
        ) { innerPadding ->

            HybridCarNavHost(
                navController = navController,
                innerPadding = innerPadding,
                fuelViewModel = fuelViewModel,
                homeViewModel = homeViewModel
            )
        }
    }
}
