package com.example.cochehibrido.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.compose.*
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.DirectionsCar

import com.example.cochehibrido.ui.screens.*
import com.example.cochehibrido.viewmodel.*

@Composable
fun HybridCarNavHost() {

    val navController = rememberNavController()

    val fuelViewModel: FuelEntryViewModel =
        viewModel(factory = AppViewModelProvider.Factory)

    val homeViewModel: HomeViewModel =
        viewModel(factory = AppViewModelProvider.Factory)

    val tripViewModel: TripViewModel =
        viewModel(factory = AppViewModelProvider.Factory)

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primary, // 🔥 ADIÓS ROSA
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {

                NavigationBarItem(
                    selected = currentRoute == "home",
                    onClick = { navController.navigate("home") },
                    label = { Text("Resumen") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondary,
                        unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                )

                NavigationBarItem(
                    selected = currentRoute == "refuels",
                    onClick = { navController.navigate("refuels") },
                    label = { Text("Repostajes") },
                    icon = { Icon(Icons.Default.LocalGasStation, contentDescription = null) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondary,
                        unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                )

                NavigationBarItem(
                    selected = currentRoute == "trips",
                    onClick = { navController.navigate("trips") },
                    label = { Text("Viajes") },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.secondary,
                        unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    )
                )
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "home"
        ) {

            // 🏠 HOME
            composable("home") {
                HomeScreen(
                    innerPadding = innerPadding,
                    viewModel = homeViewModel
                )
            }

            // ⛽ REPOSTAJES
            composable("refuels") {
                ConsumptionListScreen(
                    innerPadding = innerPadding,
                    viewModel = fuelViewModel,
                    navController = navController,
                    onAddClick = {
                        navController.navigate("add_refuel")
                    }
                )
            }

            // ➕ AÑADIR REPOSTAJE
            composable("add_refuel") {
                AddConsumptionScreen(
                    innerPadding = innerPadding,
                    viewModel = fuelViewModel,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            // ✏️ EDITAR REPOSTAJE
            composable("edit_refuel/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toInt() ?: 0
                val entries by fuelViewModel.entries.collectAsState()
                val entry = entries.find { it.id == id }

                entry?.let {
                    AddConsumptionScreen(
                        innerPadding = innerPadding,
                        viewModel = fuelViewModel,
                        entry = it,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }

            // 🚗 VIAJES
            composable("trips") {
                TripListScreen(
                    innerPadding = innerPadding,
                    viewModel = tripViewModel,
                    homeViewModel = homeViewModel,
                    navController = navController,
                    onAddClick = {
                        navController.navigate("add_trip")
                    }
                )
            }

            // ➕ AÑADIR VIAJE
            composable("add_trip") {
                AddTripScreen(
                    innerPadding = innerPadding,
                    viewModel = tripViewModel,
                    onSaved = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }

            // ✏️ EDITAR VIAJE
            composable("edit_trip/{id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")?.toInt() ?: 0
                val trips by tripViewModel.trips.collectAsState()
                val trip = trips.find { it.id == id }

                trip?.let {
                    AddTripScreen(
                        innerPadding = innerPadding,
                        viewModel = tripViewModel,
                        trip = it,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}