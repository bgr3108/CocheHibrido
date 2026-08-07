package com.example.cochehibrido.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
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
                innerPadding = innerPadding,
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

            if (entry != null) {
                AddConsumptionScreen(
                    viewModel = fuelViewModel,
                    homeViewModel = homeViewModel,
                    innerPadding = innerPadding,
                    entry = entry,
                    onClose = {
                        navController.popBackStack()
                    }
                )
            } else {
                MissingEntryScreen(
                    innerPadding = innerPadding,
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(
                                navController.graph.findStartDestination().id
                            ) {
                                launchSingleTop = true
                            }
                        }
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

@Composable
private fun MissingEntryScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Registro no encontrado")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Volver")
        }
    }
}
