package com.example.cochehibrido

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController

// 🔥 ICONOS (esto es lo que te faltaba)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.navigation.compose.currentBackStackEntryAsState



import com.example.cochehibrido.ui.navigation.HybridCarNavHost
import com.example.cochehibrido.ui.theme.CocheHibridoTheme
import com.example.cochehibrido.viewmodel.AppViewModelProvider
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.viewmodel.HomeViewModel
import com.example.cochehibrido.viewmodel.TripViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.ui.screens.SetupScreen
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.BarChart

class MainActivity : ComponentActivity() {

    private val fuelViewModel: FuelEntryViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val homeViewModel: HomeViewModel by viewModels {
        AppViewModelProvider.Factory
    }


    private val tripViewModel: TripViewModel by viewModels {
        AppViewModelProvider.Factory
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CocheHibridoTheme {
                AppContent(
                    fuelViewModel = fuelViewModel,
                    homeViewModel = homeViewModel,
                    tripViewModel = tripViewModel
                )
            }
        }
    }
}

@Composable
fun AppContent(
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    tripViewModel: TripViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val isVehicleLoading by homeViewModel
        .isVehicleLoading
        .collectAsStateWithLifecycle()
    val vehicle by homeViewModel.vehicle.collectAsStateWithLifecycle()

    if (isVehicleLoading) {

        CircularProgressIndicator()

    } else if (vehicle.type == null) {

        SetupScreen(
            vehicleRepository =
                (context.applicationContext as HybridCarApplication)
                    .container
                    .vehicleRepository,

            homeViewModel = homeViewModel,

            onDone = {
            }
        )

    } else {

    Scaffold(
        bottomBar = {
            NavigationBar {

                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

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
                        onClick = { navController.navigate("home") },
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
                        onClick = { navController.navigate("consumption") },
                        icon = { Icon(Icons.Default.LocalGasStation, contentDescription = null) },
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
                        selected = currentRoute == "trips",
                        onClick = { navController.navigate("trips") },
                        icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                        label = { Text("Viajes") }
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
                        onClick = { navController.navigate("stats") },
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
            homeViewModel = homeViewModel,
            tripViewModel = tripViewModel
        )
    }
}
}