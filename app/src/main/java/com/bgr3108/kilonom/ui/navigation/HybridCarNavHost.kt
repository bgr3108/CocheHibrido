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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import com.bgr3108.kilonom.viewmodel.ScreenOperationKind
import com.bgr3108.kilonom.viewmodel.ScreenOperationResult
import kotlinx.coroutines.flow.StateFlow
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
import com.bgr3108.kilonom.ui.screens.StationsScreen
import com.bgr3108.kilonom.ui.screens.VehicleCatalogScreen
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.PeriodSummaryViewModel
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import com.bgr3108.kilonom.ui.screens.MaintenanceScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceDetailScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceFormScreen
import com.bgr3108.kilonom.ui.screens.MaintenanceFormMode
import com.bgr3108.kilonom.viewmodel.StationsViewModel
import com.bgr3108.kilonom.viewmodel.VehicleCatalogUpdateViewModel

@Composable
internal fun HybridCarNavHost(
    navController: NavHostController,
    innerPadding: PaddingValues,
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    periodSummaryViewModel: PeriodSummaryViewModel,
    myVehiclesViewModel: MyVehiclesViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    stationsViewModel: StationsViewModel,
    vehicleCatalogUpdateViewModel: VehicleCatalogUpdateViewModel,
    onNavigateTopLevel: (String) -> Unit
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
                onOpenTrends = {
                    onNavigateTopLevel("stats/trends")
                },
                onOpenMaintenance = {
                    onNavigateTopLevel("maintenance")
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
        composable("maintenance/add") { entry ->
            OperationNavigationEffect(entry, navController, maintenanceViewModel.operationResults, maintenanceViewModel::consumeOperation)
            MaintenanceFormScreen(
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.CREATE_ITEM,
                operationOrigin = entry.id,
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/item/{itemId}") { entry ->
            OperationNavigationEffect(entry, navController, maintenanceViewModel.operationResults, maintenanceViewModel::consumeOperation)
            val itemId = entry.arguments?.getString("itemId")?.toLongOrNull()
            MaintenanceDetailScreen(
                operationOrigin = entry.id,
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
            OperationNavigationEffect(entry, navController, maintenanceViewModel.operationResults, maintenanceViewModel::consumeOperation)
            MaintenanceFormScreen(
                operationOrigin = entry.id,
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.REGISTER_RECORD,
                itemId = entry.arguments?.getString("itemId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/item/{itemId}/edit") { entry ->
            OperationNavigationEffect(entry, navController, maintenanceViewModel.operationResults, maintenanceViewModel::consumeOperation)
            MaintenanceFormScreen(
                operationOrigin = entry.id,
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.EDIT_ITEM,
                itemId = entry.arguments?.getString("itemId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }
        composable("maintenance/record/{recordId}/edit") { entry ->
            OperationNavigationEffect(entry, navController, maintenanceViewModel.operationResults, maintenanceViewModel::consumeOperation)
            MaintenanceFormScreen(
                operationOrigin = entry.id,
                innerPadding = innerPadding,
                mode = MaintenanceFormMode.EDIT_RECORD,
                recordId = entry.arguments?.getString("recordId")?.toLongOrNull(),
                viewModel = maintenanceViewModel,
                onClose = { navController.popBackStack() }
            )
        }

        composable("my_vehicles") { entry ->
            OperationNavigationEffect(entry, navController, myVehiclesViewModel.operationResults, myVehiclesViewModel::consumeOperation) {
                onNavigateTopLevel("home")
            }
            MyVehiclesScreen(
                operationOrigin = entry.id,
                innerPadding = innerPadding,
                viewModel = myVehiclesViewModel,
                onAdd = { navController.navigate("add_vehicle") },
                onEdit = { id -> navController.navigate("edit_vehicle/$id") }
            )
        }

        composable("stations") {
            StationsScreen(
                innerPadding = innerPadding,
                viewModel = stationsViewModel
            )
        }

        composable("usage_guide") {
            UsageGuideScreen(innerPadding = innerPadding)
        }

        composable("vehicle_catalog") {
            VehicleCatalogScreen(
                innerPadding = innerPadding,
                viewModel = vehicleCatalogUpdateViewModel
            )
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
            OperationNavigationEffect(backStackEntry, navController, myVehiclesViewModel.operationResults, myVehiclesViewModel::consumeOperation)
            VehicleEditorScreen(
                operationOrigin = backStackEntry.id,
                vehicleId = backStackEntry.arguments?.getString("id")?.toLongOrNull(),
                viewModel = myVehiclesViewModel,
                onMissing = {
                    if (navController.currentBackStackEntry?.id == backStackEntry.id) navController.popBackStack()
                }
            )
        }

        composable("consumption") { entry ->
            OperationNavigationEffect(entry, navController, fuelViewModel.operationResults, fuelViewModel::consumeOperation)
            ConsumptionListScreen(
                operationOrigin = entry.id,
                innerPadding = innerPadding,
                viewModel = fuelViewModel,
                homeViewModel = homeViewModel,
                navController = navController,
                onAddClick = {
                    navController.navigate("add_refuel")
                }
            )
        }

        composable("add_refuel") { entry ->
            OperationNavigationEffect(entry, navController, fuelViewModel.operationResults, fuelViewModel::consumeOperation)
            AddConsumptionScreen(
                operationOrigin = entry.id,
                viewModel = fuelViewModel,
                homeViewModel = homeViewModel,
                innerPadding = innerPadding,
                onClose = {
                    navController.popBackStack()
                }
            )
        }
        composable("edit_refuel/{id}") { backStackEntry ->
            OperationNavigationEffect(backStackEntry, navController, fuelViewModel.operationResults, fuelViewModel::consumeOperation)

            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull()

            val entry = fuelViewModel.entries
                .collectAsStateWithLifecycle(initialValue = emptyList())
                .value
                .find { it.id == id }

            if (entry != null) {
                AddConsumptionScreen(
                    operationOrigin = backStackEntry.id,
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
                innerPadding = innerPadding
            )
        }
    }
}

@Composable
private fun OperationNavigationEffect(
    entry: NavBackStackEntry,
    navController: NavHostController,
    results: StateFlow<Map<String, ScreenOperationResult>>,
    consume: (String, Long) -> Boolean,
    onActiveVehicleDeleted: () -> Unit = {}
) {
    ScreenOperationEffect(entry.id, results, consume,
        isCurrentEntry = { navController.currentBackStackEntry?.id == entry.id }
    ) { result ->
        when (result.kind) {
            ScreenOperationKind.SAVE, ScreenOperationKind.DELETE_ITEM -> navController.popBackStack()
            ScreenOperationKind.DELETE_ACTIVE_VEHICLE -> onActiveVehicleDeleted()
            else -> Unit
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
