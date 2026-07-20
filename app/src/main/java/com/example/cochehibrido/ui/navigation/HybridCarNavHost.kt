package com.example.cochehibrido.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cochehibrido.ui.screens.AddConsumptionScreen
import com.example.cochehibrido.ui.screens.ConsumptionDetailScreen
import com.example.cochehibrido.ui.screens.ConsumptionListScreen
import com.example.cochehibrido.ui.screens.CostDetailScreen
import com.example.cochehibrido.ui.screens.HomeScreen
import com.example.cochehibrido.ui.screens.PriceDetailScreen
import com.example.cochehibrido.ui.screens.StatisticsScreen
import com.example.cochehibrido.ui.screens.TotalDetailScreen
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.viewmodel.HomeViewModel

@Composable
fun HybridCarNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel
){

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
                homeViewModel = homeViewModel,
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
                    homeViewModel = homeViewModel,
                    entry = it,
                    onClose = {
                        navController.popBackStack()
                    }
                )
            }
        }
        composable("stats") {
            StatisticsScreen(
                innerPadding = innerPadding,
                viewModel = homeViewModel,
                onOpenConsumption = {
                    navController.navigate("consumption_detail")
                },
                onOpenPrice = {
                    navController.navigate("price_detail")
                },
                onOpenCost = {
                    navController.navigate("cost_detail")
                },
                onOpenTotal = {
                    navController.navigate("total_detail")
                }
            )
        }
        composable("consumption_detail") {

            ConsumptionDetailScreen(
                viewModel = homeViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )

        }
        composable("price_detail") {
            PriceDetailScreen(
                viewModel = homeViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("cost_detail") {
            CostDetailScreen(
                viewModel = homeViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable("total_detail") {
            TotalDetailScreen(
                viewModel = homeViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}