package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.viewmodel.HomeViewModel

/** First-run wrapper around the shared vehicle editor. */
@Composable
fun SetupScreen(
    homeViewModel: HomeViewModel,
    onDone: () -> Unit
) {
    val vehicles by homeViewModel.availableVehicles.collectAsStateWithLifecycle()
    val category by homeViewModel.setupVehicleCategory.collectAsStateWithLifecycle()
    val isSaving by homeViewModel.isSavingVehicle.collectAsStateWithLifecycle()
    val saveFailed by homeViewModel.vehicleSaveFailed.collectAsStateWithLifecycle()

    VehicleForm(
        title = "Configura tu vehículo",
        initialVehicle = null,
        availableVehicles = vehicles,
        selectedCategory = category,
        onCategoryChanged = homeViewModel::selectSetupVehicleCategory,
        catalogEditable = true,
        isSaving = isSaving,
        errorMessage = if (saveFailed) "No se pudo guardar la configuración. Inténtalo de nuevo." else null,
        onSubmit = { vehicle -> homeViewModel.saveInitialVehicle(vehicle, onDone) }
    )
}
