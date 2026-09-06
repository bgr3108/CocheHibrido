package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel

@Composable
fun MaintenanceDetailScreen(
    innerPadding: PaddingValues,
    itemId: Long?,
    viewModel: MaintenanceViewModel,
    onBack: () -> Unit,
    onRegister: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onEditRecord: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val records by viewModel.detailRecords.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.item.id == itemId }
    val itemToDelete = remember { mutableStateOf<MaintenanceItemEntity?>(null) }
    val recordToDelete = remember { mutableStateOf<MaintenanceRecordEntity?>(null) }
    val calendarOpenError = remember(itemId, item?.item?.nextDueDate) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(itemId) { viewModel.selectDetailItem(itemId) }
    if (item == null) {
        MissingMaintenanceScreen(innerPadding, onBack)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(item.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        SectionTitle("Próximo")
        MaintenanceCard {
            Text(item.due.primaryStatusText(), style = MaterialTheme.typography.titleMedium)
            item.due.secondaryStatusText()?.let { Text(it, color = maintenanceSecondaryColor()) }
            if (item.item.nextDueKm != null || item.item.nextDueDate != null) {
                Spacer(Modifier.height(6.dp))
                item.item.nextDueKm?.let { Text("${it.formatKilometers()} km") }
                item.item.nextDueDate?.let { Text(it.formatMaintenanceDate()) }
            }
        }
        state.vehicle?.let { vehicle ->
            item.item.toMaintenanceCalendarEvent(vehicle)?.let { calendarEvent ->
                OutlinedButton(
                    onClick = { calendarOpenError.value = !context.openMaintenanceCalendar(calendarEvent) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Text("  Añadir al calendario")
                }
                Text(
                    "Se abrirá tu aplicación de calendario para que puedas revisar y guardar el evento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = maintenanceSecondaryColor()
                )
                if (calendarOpenError.value) {
                    Text(
                        "No se ha encontrado una aplicación de calendario compatible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        item.item.recurrenceSummary()?.let { summary ->
            SectionTitle("Intervalo de mantenimiento")
            MaintenanceCard {
                Text(summary, style = MaterialTheme.typography.titleMedium)
                item.item.reminderLeadSummaries().forEach { Text(it, color = maintenanceSecondaryColor()) }
            }
        }
        Button(onClick = { onRegister(item.item.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Registrar mantenimiento")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onEditItem(item.item.id) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("  Editar seguimiento")
            }
            OutlinedButton(onClick = { itemToDelete.value = item.item }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("  Eliminar")
            }
        }
        SectionTitle("Historial")
        if (records.isEmpty()) {
            Text("Aún no hay mantenimientos realizados.", color = maintenanceSecondaryColor())
        } else {
            records.forEach { record ->
                MaintenanceCard {
                    Text(record.performedDate?.formatMaintenanceDate() ?: "Fecha no indicada", style = MaterialTheme.typography.titleMedium)
                    record.odometerKm?.let { Text("${it.formatKilometers()} km") }
                    record.cost?.let { Text(it.formatMaintenanceMoney()) }
                    record.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = maintenanceSecondaryColor()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEditRecord(record.id) }) { Text("Editar") }
                        TextButton(onClick = { recordToDelete.value = record }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    itemToDelete.value?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) itemToDelete.value = null },
            title = { Text("Eliminar seguimiento") },
            text = {
                val count = records.size
                Text(
                    if (count > 0) "Se eliminará ${item.name} y sus $count registros del historial."
                    else "Se eliminará ${item.name}."
                )
            },
            confirmButton = {
                TextButton(enabled = !state.isWorking, onClick = {
                    viewModel.deleteItem(target.id) { onBack() }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete.value = null }) { Text("Cancelar") } }
        )
    }
    recordToDelete.value?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.isWorking) recordToDelete.value = null },
            title = { Text("Eliminar registro") },
            text = { Text("¿Eliminar este registro? El próximo aviso del seguimiento no cambiará.") },
            confirmButton = {
                TextButton(enabled = !state.isWorking, onClick = {
                    viewModel.deleteRecord(target.id) { recordToDelete.value = null }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { recordToDelete.value = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
internal fun MissingMaintenanceScreen(innerPadding: PaddingValues, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mantenimiento no encontrado")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onBack) { Text("Volver") }
    }
}
