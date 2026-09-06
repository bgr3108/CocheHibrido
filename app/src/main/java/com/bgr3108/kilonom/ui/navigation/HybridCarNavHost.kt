package com.bgr3108.kilonom.ui.navigation

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
import com.bgr3108.kilonom.ui.screens.AddConsumptionScreen
import com.bgr3108.kilonom.ui.screens.ConsumptionDetailScreen
import com.bgr3108.kilonom.ui.screens.ConsumptionListScreen
import com.bgr3108.kilonom.ui.screens.CostDetailScreen
import com.bgr3108.kilonom.ui.screens.HomeScreen
import com.bgr3108.kilonom.ui.screens.PriceDetailScreen
import com.bgr3108.kilonom.ui.screens.PrivacyScreen
import com.bgr3108.kilonom.ui.screens.StatisticsScreen
import com.bgr3108.kilonom.ui.screens.StatisticsTab
import com.bgr3108.kilonom.ui.screens.TotalDetailScreen
import com.bgr3108.kilonom.ui.screens.MyVehiclesScreen
import com.bgr3108.kilonom.ui.screens.AddVehicleScreen
import com.bgr3108.kilonom.ui.screens.VehicleEditorScreen
import com.bgr3108.kilonom.ui.screens.UsageGuideScreen
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.PeriodSummaryViewModel
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import com.bgr3108.kilonom.ui.screens.MaintenanceScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceDetailScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceFormScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceFormMode

@Composable
fun HybridCarNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    periodSummaryViewModel: PeriodSummaryViewModel,
    myVehiclesViewModel: MyVehiclesViewModel,
    maintenanceViewModel: MaintenanceViewModel
){

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        composable("home") {
            HomeScreen(
                innerPadding = innerPadding,
                viewModel = homeViewModel,
                maintenanceViewModel = maintenanceViewModel,
                onOpenMyVehicles = {
                    navController.navigate("my_vehicles")
                },
                onOpenTrends = {
                    navController.navigate("stats/trends")
                },
                onOpenMaintenance = {
                    navController.navigate("maintenance") {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable("maintenance") {
            MaintenanceScreen(
                innerPadding = innerPadding,
                viewModel = maintenanceViewModel,
                onAdd = { navController.navigate("maintenance/add") },
                onOpenItem = { itemId -> navController.navigate("maintenance/item/$itemId") }
            )
        }
        composable("maintenance/add") {
            MaintenanceFormScreen(
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.CREATE_ITEM,
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/item/{itemId}") { entry ->
            val itemId = entry.arguments?.getString("itemId")?.toLongOrNull()
            MaintenanceDetailScreen(
                innerPadding = innerPadding,
                itemId = itemId,
                viewModel = maintenanceViewModel,
                onBack = { navController.popBackStack() },
                onRegister = { id -> navController.navigate("maintenance/item/$id/register") },
                onEditItem = { id -> navController.navigate("maintenance/item/$id/edit") },
                onEditRecord = { id -> navController.navigate("maintenance/record/$id/edit") }
            )
        }
        composable("maintenance/item/{itemId}/register") { entry ->
            MaintenanceFormScreen(
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.REGISTER_RECORD,
                itemId = entry.arguments?.getString("itemId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/item/{itemId}/edit") { entry ->
            MaintenanceFormScreen(
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.EDIT_ITEM,
                itemId = entry.arguments?.getString("itemId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/record/{recordId}/edit") { entry ->
            MaintenanceFormScreen(
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.EDIT_RECORD,
                recordId = entry.arguments?.getString("recordId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }

        composable("my_vehicles") {
            MyVehiclesScreen(
                innerPadding = innerPadding,
                viewModel = myVehiclesViewModel,
                homeViewModel = homeViewModel,
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate("add_vehicle") },
                onEdit = { id -> navController.navigate("edit_vehicle/$id") },
                onOpenUsageGuide = { navController.navigate("usage_guide") },
                onOpenPrivacy = { navController.navigate("privacy") },
                onActiveVehicleDeleted = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("usage_guide") {
            UsageGuideScreen(onBack = { navController.popBackStack() })
        }

        composable("add_vehicle") {
            AddVehicleScreen(
                viewModel = myVehiclesViewModel,
                onCreated = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable("edit_vehicle/{id}") { backStackEntry ->
            VehicleEditorScreen(
                vehicleId = backStackEntry.arguments?.getString("id")?.toLongOrNull(),
                viewModel = myVehiclesViewModel,
                onSaved = { navController.popBackStack() },
                onMissing = { navController.popBackStack() }
            )
        }

        composable("consumption") {
            ConsumptionListScreen(
                innerPadding = innerPadding,
                viewModel = fuelViewModel,
                homeViewModel = homeViewModel,
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
                },
                periodSummaryViewModel = periodSummaryViewModel,
                initialTab = StatisticsTab.GLOBAL
            )
        }
        composable("stats/trends") {
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
                },
                periodSummaryViewModel = periodSummaryViewModel,
                initialTab = StatisticsTab.TRENDS
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
        composable("privacy") {
            PrivacyScreen(
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
