package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.LegacyCatalogReconciliation
import com.bgr3108.kilonom.data.toVehicle
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel
import com.bgr3108.kilonom.viewmodel.VehicleCreationState

@Composable
fun AddVehicleScreen(
    viewModel: MyVehiclesViewModel,
    onCreated: () -> Unit
) {
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val catalog by viewModel.selectionCatalog.collectAsStateWithLifecycle()
    val isSaving by viewModel.isWorking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val creationState by viewModel.creationState.collectAsStateWithLifecycle()
    val saved = creationState as? VehicleCreationState.Saved
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, saved) {
        if (saved?.isActive == true || saved?.acknowledged == true) {
            lifecycleOwner.lifecycle.withResumed { onCreated() }
        }
    }
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
            } else if (!saved.isActive && !saved.acknowledged) {
                Text(error ?: "La activación no se completó. Puedes reintentar o continuar sin cambiar de vehículo activo.")
                TextButton(onClick = viewModel::retryCreatedVehicleActivation) { Text("Reintentar activación") }
                TextButton(onClick = viewModel::acknowledgeCreatedVehicle) { Text("Continuar sin activar") }
            }
        }
        return
    }
    VehicleSelectionContent(catalog, viewModel::retrySelectionCatalog) { readyCatalog ->
        VehicleForm(
            title = "Añadir vehículo",
            initialVehicle = null,
            selectionCatalog = readyCatalog,
            selectedCategory = category,
            onCategoryChanged = viewModel::selectCategory,
            catalogEditable = true,
            isSaving = isSaving,
            errorMessage = error,
            onSubmit = viewModel::createVehicle
        )
    }
}

@Composable
fun VehicleEditorScreen(
    vehicleId: Long?,
    viewModel: MyVehiclesViewModel,
    operationOrigin: String,
    onMissing: () -> Unit
) {
    val summaries by viewModel.vehicleSummaries.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val catalog by viewModel.selectionCatalog.collectAsStateWithLifecycle()
    val isSaving by viewModel.isWorking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val operations by viewModel.operationResults.collectAsStateWithLifecycle()
    val summary = summaries.firstOrNull { it.vehicle.id == vehicleId }
    if (vehicleId == null || summary == null) {
        LaunchedEffect(vehicleId, summaries) { if (summaries.isNotEmpty()) onMissing() }
        return
    }
    LaunchedEffect(summary.vehicle.id) { viewModel.selectCategory(summary.vehicle.category) }
    VehicleSelectionContent(catalog, viewModel::retrySelectionCatalog) { readyCatalog ->
        val pendingInitialKmChange = remember { mutableStateOf<Vehicle?>(null) }
        val hasRecordedActivity = summary.entryCount > 0 || summary.maintenanceRecordCount > 0
        val catalogLinkSuggestion = remember(summary.vehicle) {
            (viewModel.reconcileLegacyVehicle(summary.vehicle) as? LegacyCatalogReconciliation.ExactMatch)?.catalogId
        }
        VehicleForm(
            title = "Editar vehículo",
            initialVehicle = summary.vehicle.toVehicle(),
            selectionCatalog = readyCatalog,
            selectedCategory = category,
            onCategoryChanged = viewModel::selectCategory,
            catalogEditable = !hasRecordedActivity,
            minimumRecordedKm = summary.minimumValidRecordedKm,
            currentKm = summary.currentKm,
            catalogLinkSuggestion = catalogLinkSuggestion,
            onLinkToCatalog = catalogLinkSuggestion?.let { catalogId ->
                { viewModel.linkLegacyVehicleToCatalog(summary.vehicle.id, catalogId) }
            },
            isSaving = isSaving,
            errorMessage = operations[operationOrigin]?.error ?: error,
            onSubmit = { updated ->
                if (hasRecordedActivity && updated.initialKm != summary.vehicle.initialKm) pendingInitialKmChange.value = updated
                else viewModel.updateVehicle(summary.vehicle.id, updated, operationOrigin)
            }
        )
        pendingInitialKmChange.value?.let { updated ->
            AlertDialog(
                onDismissRequest = { pendingInitialKmChange.value = null },
                title = { Text("Cambiar kilometraje inicial") },
                text = { Text("Cambiar el kilometraje inicial puede modificar los cálculos de distancia y coste por kilómetro.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateVehicle(summary.vehicle.id, updated, operationOrigin)
                        pendingInitialKmChange.value = null
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingInitialKmChange.value = null }) { Text("Cancelar") }
                }
            )
        }
    }
}
