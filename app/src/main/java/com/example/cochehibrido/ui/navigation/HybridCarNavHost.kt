package com.example.cochehibrido.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.collectAsState

import com.example.cochehibrido.ui.screens.*
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.viewmodel.HomeViewModel
import com.example.cochehibrido.viewmodel.TripViewModel

@Composable
fun HybridCarNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    tripViewModel: TripViewModel
) {

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        composable("home") {
            HomeScreen(
                innerPadding = innerPadding,
                viewModel = homeViewModel
            )
        }

        composable("consumption") {
            ConsumptionListScreen(
                innerPadding = innerPadding,
                viewModel = fuelViewModel,
                navController = navController,
                onAddClick = {
                    navController.navigate("add_refuel")
                }
            )
        }

        composable("add_refuel") {
            AddConsumptionScreen(
                viewModel = fuelViewModel,
                onClose = {
                    navController.popBackStack()
                }
            )
        }
        composable("edit_refuel/{id}") { backStackEntry ->

            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()

            val entry = fuelViewModel.entries
                .collectAsState(initial = emptyList())
                .value
                .find { it.id == id }

            entry?.let {
                AddConsumptionScreen(
                    viewModel = fuelViewModel,
                    entry = it,
                    onClose = {
                        navController.popBackStack()
                    }
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
        composable("add_trip") {
            AddTripScreen(
                innerPadding = innerPadding,
                viewModel = tripViewModel,
                onSaved = {
                    navController.popBackStack()
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }
        composable("edit_trip/{tripId}") { backStackEntry ->

            val tripId = backStackEntry.arguments?.getString("tripId")?.toIntOrNull()

            val trip = tripViewModel.trips.collectAsState().value
                .find { it.id == tripId }

            if (trip != null) {
                AddTripScreen(
                    innerPadding = innerPadding,
                    viewModel = tripViewModel,
                    trip = trip,
                    onSaved = {
                        navController.popBackStack()
                    },
                    onCancel = {
                        navController.popBackStack()
                    }
                )
            }
        }
        composable("stats") {
            StatisticsScreen()
        }
    }
}