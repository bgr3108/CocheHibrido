package com.bgr3108.kilonom.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.toVehicle
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel

@Composable
fun AddVehicleScreen(
    viewModel: MyVehiclesViewModel,
    onCreated: () -> Unit
) {
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val catalog by viewModel.availableVehicles.collectAsStateWithLifecycle()
    val isSaving by viewModel.isWorking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
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
    val summaries by viewModel.vehicleSummaries.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val catalog by viewModel.availableVehicles.collectAsStateWithLifecycle()
    val isSaving by viewModel.isWorking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val summary = summaries.firstOrNull { it.vehicle.id == vehicleId }
    if (vehicleId == null || summary == null) {
        LaunchedEffect(vehicleId, summaries) { if (summaries.isNotEmpty()) onMissing() }
        return
    }
    LaunchedEffect(summary.vehicle.id) { viewModel.selectCategory(summary.vehicle.category) }
    val pendingInitialKmChange = remember { mutableStateOf<Vehicle?>(null) }
    val hasRecordedActivity = summary.entryCount > 0 || summary.maintenanceRecordCount > 0
    VehicleForm(
        title = "Editar vehículo",
        initialVehicle = summary.vehicle.toVehicle(),
        availableVehicles = catalog,
        selectedCategory = category,
        onCategoryChanged = viewModel::selectCategory,
        catalogEditable = !hasRecordedActivity,
        minimumRecordedKm = summary.minimumValidRecordedKm,
        currentKm = summary.currentKm,
        isSaving = isSaving,
        errorMessage = error,
        onSubmit = { updated ->
            if (hasRecordedActivity && updated.initialKm != summary.vehicle.initialKm) pendingInitialKmChange.value = updated
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
