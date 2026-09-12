package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleRepository.VehicleSummary
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel

@Composable
fun MyVehiclesScreen(
    innerPadding: PaddingValues,
    viewModel: MyVehiclesViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onActiveVehicleDeleted: () -> Unit
) {
    val summaries by viewModel.vehicleSummaries.collectAsStateWithLifecycle()
    val activeVehicleId by viewModel.activeVehicleId.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val switchingVehicleId by viewModel.switchingVehicleId.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingDeletion = remember { mutableStateOf<VehicleSummary?>(null) }
    val detailsSummary = remember { mutableStateOf<VehicleSummary?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        summaries.forEach { summary ->
            VehicleSummaryCard(
                summary = summary,
                active = summary.vehicle.id == activeVehicleId,
                selectionEnabled = !isWorking,
                menuEnabled = !isWorking,
                isSwitching = summary.vehicle.id == switchingVehicleId,
                onSelect = {
                    if (summary.vehicle.id != activeVehicleId) {
                        viewModel.selectVehicle(summary.vehicle.id)
                    }
                },
                onDetails = { detailsSummary.value = summary },
                onEdit = { onEdit(summary.vehicle.id) },
                onDelete = { pendingDeletion.value = summary }
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Añadir vehículo") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }

    detailsSummary.value?.let { summary ->
        VehicleDetailsDialog(summary = summary, onDismiss = { detailsSummary.value = null })
    }

    pendingDeletion.value?.let { summary ->
        AlertDialog(
            onDismissRequest = { if (!isWorking) pendingDeletion.value = null },
            title = { Text("Eliminar vehículo") },
            text = {
                Text("También se eliminarán ${summary.entryCount} consumos asociados. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isWorking,
                    onClick = {
                        viewModel.deleteVehicle(summary.vehicle.id) {
                            pendingDeletion.value = null
                            if (summary.vehicle.id == activeVehicleId) onActiveVehicleDeleted()
                        }
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !isWorking, onClick = { pendingDeletion.value = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

}

@Composable
private fun VehicleSummaryCard(
    summary: VehicleSummary,
    active: Boolean,
    selectionEnabled: Boolean,
    menuEnabled: Boolean,
    isSwitching: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val menuExpanded = remember { mutableStateOf(false) }
    Card(
        onClick = { if (!active && selectionEnabled) onSelect() },
        enabled = true,
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) CardBlueDark else CardBlueLight
        ),
        border = if (active) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (summary.vehicle.category == VehicleCategory.MOTO) {
                    Icons.Default.TwoWheeler
                } else {
                    Icons.Default.DirectionsCar
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "${summary.vehicle.brand} ${summary.vehicle.model}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Km actuales: ${summary.currentKm.toKilometersDisplay()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (active) {
                    Text(
                        "Activo",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    listOfNotNull(
                        summary.vehicle.year?.toString(),
                        summary.vehicle.type?.toDisplayName()
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = { menuExpanded.value = true }, enabled = menuEnabled) {
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones del vehículo")
                }
            }
            DropdownMenu(
                expanded = menuExpanded.value,
                onDismissRequest = { menuExpanded.value = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Información") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = { menuExpanded.value = false; onDetails() }
                )
                DropdownMenuItem(
                    text = { Text("Editar") },
                    onClick = { menuExpanded.value = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar") },
                    onClick = { menuExpanded.value = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun VehicleDetailsDialog(
    summary: VehicleSummary,
    onDismiss: () -> Unit
) {
    val vehicle = summary.vehicle
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información del vehículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Tipo", if (vehicle.category == VehicleCategory.MOTO) "Moto" else "Coche")
                DetailLine("Marca", vehicle.brand)
                DetailLine("Modelo", vehicle.model)
                vehicle.year?.let { DetailLine("Año", it.toString()) }
                vehicle.type?.let { DetailLine("Propulsión", it.toDisplayName()) }
                if (vehicle.type.supportsFuelEntries && vehicle.fuelTankCapacity > 0.0) {
                    DetailLine("Capacidad de depósito", "${vehicle.fuelTankCapacity.toSpanishDecimal()} L")
                }
                if (vehicle.type.supportsElectricEntries && vehicle.batteryCapacity > 0.0) {
                    DetailLine("Capacidad útil de batería", "${vehicle.batteryCapacity.toSpanishDecimal()} kWh")
                }
                DetailLine("Kilometraje inicial", vehicle.initialKm.toKilometersDisplay())
                DetailLine("Kilometraje actual", summary.currentKm.toKilometersDisplay())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun com.bgr3108.kilonom.data.VehicleType.toDisplayName(): String =
    name.lowercase().replace("_", " ")
        .replace("hibrido", "híbrido")
        .replace("electrico", "eléctrico")
        .replace("diesel", "diésel")
        .replaceFirstChar { it.titlecase() }
