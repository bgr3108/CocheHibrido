package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.VehicleCreationState

/** First-run wrapper around the shared vehicle editor. */
@Composable
fun SetupScreen(homeViewModel: HomeViewModel) {
    val catalog by homeViewModel.selectionCatalog.collectAsStateWithLifecycle()
    val category by homeViewModel.setupVehicleCategory.collectAsStateWithLifecycle()
    val isSaving by homeViewModel.isSavingVehicle.collectAsStateWithLifecycle()
    val saveError by homeViewModel.vehicleSaveError.collectAsStateWithLifecycle()
    val creationState by homeViewModel.initialVehicleCreation.collectAsStateWithLifecycle()
    val saved = creationState as? VehicleCreationState.Saved

    if (saved != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("El vehículo está guardado.")
            if (isSaving) {
                CircularProgressIndicator()
                Text("Activando el vehículo…")
            } else if (!saved.isActive) {
                Text(saveError ?: "La activación no se completó. Puedes reintentarla sin volver a guardar el vehículo.")
                TextButton(onClick = homeViewModel::retryInitialVehicleActivation) { Text("Reintentar activación") }
            }
        }
        return
    }

    VehicleSelectionContent(catalog, homeViewModel::retrySelectionCatalog) { readyCatalog ->
        VehicleForm(
            title = "Configura tu vehículo",
            initialVehicle = null,
            selectionCatalog = readyCatalog,
            selectedCategory = category,
            onCategoryChanged = homeViewModel::selectSetupVehicleCategory,
            catalogEditable = true,
            isSaving = isSaving,
            errorMessage = saveError,
            onSubmit = homeViewModel::saveInitialVehicle
        )
    }
}
