package com.bgr3108.kilonom.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.toVehicle
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel

@Composable
fun AddVehicleScreen(
    viewModel: MyVehiclesViewModel,
    onCreated: () -> Unit
) {
    val category by viewModel.selectedCategory.collectAsState()
    val catalog by viewModel.availableVehicles.collectAsState()
    val isSaving by viewModel.isWorking.collectAsState()
    val error by viewModel.error.collectAsState()
    VehicleForm(
        title = "Añadir vehículo",
        initialVehicle = null,
        availableVehicles = catalog,
        selectedCategory = category,
        onCategoryChanged = viewModel::selectCategory,
        catalogEditable = true,
        isSaving = isSaving,
        errorMessage = error,
        onSubmit = { vehicle -> viewModel.createVehicle(vehicle, onCreated) }
    )
}

@Composable
fun VehicleEditorScreen(
    vehicleId: Long?,
    viewModel: MyVehiclesViewModel,
    onSaved: () -> Unit,
    onMissing: () -> Unit
) {
    val summaries by viewModel.vehicleSummaries.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()
    val catalog by viewModel.availableVehicles.collectAsState()
    val isSaving by viewModel.isWorking.collectAsState()
    val error by viewModel.error.collectAsState()
    val summary = summaries.firstOrNull { it.vehicle.id == vehicleId }
    if (vehicleId == null || summary == null) {
        LaunchedEffect(vehicleId, summaries) { if (summaries.isNotEmpty()) onMissing() }
        return
    }
    LaunchedEffect(summary.vehicle.id) { viewModel.selectCategory(summary.vehicle.category) }
    val pendingInitialKmChange = remember { mutableStateOf<Vehicle?>(null) }
    val hasEntries = summary.entryCount > 0
    VehicleForm(
        title = "Editar vehículo",
        initialVehicle = summary.vehicle.toVehicle(),
        availableVehicles = catalog,
        selectedCategory = category,
        onCategoryChanged = viewModel::selectCategory,
        catalogEditable = !hasEntries,
        minimumEntryKm = summary.minimumValidEntryKm,
        currentKm = summary.currentKm,
        isSaving = isSaving,
        errorMessage = error,
        onSubmit = { updated ->
            if (hasEntries && updated.initialKm != summary.vehicle.initialKm) pendingInitialKmChange.value = updated
            else viewModel.updateVehicle(summary.vehicle.id, updated, onSaved)
        }
    )
    pendingInitialKmChange.value?.let { updated ->
        AlertDialog(
            onDismissRequest = { pendingInitialKmChange.value = null },
            title = { Text("Cambiar kilometraje inicial") },
            text = { Text("Cambiar el kilometraje inicial puede modificar los cálculos de distancia y coste por kilómetro.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateVehicle(summary.vehicle.id, updated, onSaved)
                    pendingInitialKmChange.value = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingInitialKmChange.value = null }) { Text("Cancelar") }
            }
        )
    }
}
