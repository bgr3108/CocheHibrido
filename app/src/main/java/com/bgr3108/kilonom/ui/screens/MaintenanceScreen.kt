package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.domain.MaintenanceDueStatus
import com.bgr3108.kilonom.domain.MaintenanceDueMeasure
import com.bgr3108.kilonom.domain.primaryDueMeasure
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.viewmodel.MaintenanceItemUiModel
import com.bgr3108.kilonom.viewmodel.MaintenanceRecordUiModel
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun MaintenanceScreen(
    innerPadding: PaddingValues,
    viewModel: MaintenanceViewModel,
    onAdd: () -> Unit,
    onOpenItem: (Long) -> Unit
) {
    RefreshMaintenanceForCurrentDayOnResume(viewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAllHistory by remember { mutableStateOf(false) }
    val shownRecords = if (showAllHistory) state.records else state.records.take(5)

    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mantenimiento", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            state.errorMessage?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!state.hasItems) {
                item {
                    MaintenanceEmptyState(onAdd = onAdd)
                }
            } else {
                item { SectionTitle("Próximos") }
                if (state.upcomingItems.isEmpty()) {
                    item {
                        Text(
                            "No hay próximos avisos configurados.",
                            color = maintenanceSecondaryColor(),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(state.upcomingItems, key = { maintenanceUpcomingItemKey(it.item.id) }) { item ->
                        MaintenanceItemCard(item, onClick = { onOpenItem(item.item.id) })
                    }
                }
                if (state.itemsWithoutReminder.isNotEmpty()) {
                    item { SectionTitle("Sin próximo aviso") }
                    items(state.itemsWithoutReminder, key = { maintenanceUnconfiguredItemKey(it.item.id) }) { item ->
                        MaintenanceItemCard(item, onClick = { onOpenItem(item.item.id) })
                    }
                }
                item { SectionTitle("Historial reciente") }
                if (shownRecords.isEmpty()) {
                    item {
                        Text(
                            "Aún no hay mantenimientos realizados.",
                            color = maintenanceSecondaryColor(),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    items(shownRecords, key = { maintenanceHistoryRecordKey(it.record.id) }) { record ->
                        MaintenanceRecordCard(record, onClick = { onOpenItem(record.item.id) })
                    }
                    if (!showAllHistory && state.records.size > shownRecords.size) {
                        item {
                            TextButton(onClick = { showAllHistory = true }) {
                                Text("Ver todo el historial")
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Añadir mantenimiento")
        }
    }
}

@Composable
private fun MaintenanceEmptyState(onAdd: () -> Unit) {
    MaintenanceCard {
        Text("Aún no has añadido ningún mantenimiento.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Añade el primero para guardar su historial y próximos avisos.",
            color = maintenanceSecondaryColor()
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Añadir mantenimiento") }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
internal fun MaintenanceItemCard(item: MaintenanceItemUiModel, onClick: () -> Unit) {
    MaintenanceCard(onClick = onClick) {
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        val statusColor = when (item.due.status) {
            MaintenanceDueStatus.OVERDUE -> MaterialTheme.colorScheme.error
            MaintenanceDueStatus.DUE_SOON -> MaterialTheme.colorScheme.primary
            else -> maintenanceSecondaryColor()
        }
        Text(item.due.primaryStatusText(), color = statusColor, style = MaterialTheme.typography.bodyMedium)
        val secondaryStatusText = item.due.secondaryStatusText()
        if (secondaryStatusText != null) {
            Text(secondaryStatusText, color = maintenanceSecondaryColor(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun MaintenanceRecordCard(record: MaintenanceRecordUiModel, onClick: () -> Unit) {
    MaintenanceCard(onClick = onClick) {
        Text(record.itemName, style = MaterialTheme.typography.titleMedium)
        record.record.detailsText()?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = maintenanceSecondaryColor())
        }
    }
}

@Composable
internal fun MaintenanceCard(onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(
        containerColor = if (isSystemInDarkTheme()) CardBlueDark else CardBlueLight,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
    if (onClick == null) {
        Card(colors = colors, modifier = Modifier.fillMaxWidth(), content = cardContent)
    } else {
        Card(onClick = onClick, colors = colors, modifier = Modifier.fillMaxWidth(), content = cardContent)
    }
}

@Composable
internal fun maintenanceSecondaryColor() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)

internal fun com.bgr3108.kilonom.domain.MaintenanceDueInfo.primaryStatusText(): String = when (status) {
    MaintenanceDueStatus.OVERDUE -> statusTexts().firstOrNull() ?: "Vencido"
    MaintenanceDueStatus.DUE_SOON -> statusTexts().firstOrNull() ?: "Toca ahora"
    MaintenanceDueStatus.UP_TO_DATE -> statusTexts().firstOrNull() ?: "Al día"
    MaintenanceDueStatus.NO_DUE_CONFIGURED -> "Sin próximo aviso configurado"
}

internal fun com.bgr3108.kilonom.domain.MaintenanceDueInfo.secondaryStatusText(): String? = when (status) {
    MaintenanceDueStatus.OVERDUE,
    MaintenanceDueStatus.DUE_SOON,
    MaintenanceDueStatus.UP_TO_DATE -> statusTexts().drop(1).firstOrNull()
    MaintenanceDueStatus.NO_DUE_CONFIGURED -> null
}

private fun com.bgr3108.kilonom.domain.MaintenanceDueInfo.statusTexts(): List<String> =
    dueMeasuresInDisplayOrder().mapNotNull { statusTextFor(it) }

private fun com.bgr3108.kilonom.domain.MaintenanceDueInfo.dueMeasuresInDisplayOrder(): List<MaintenanceDueMeasure> {
    val primary = primaryDueMeasure() ?: return emptyList()
    return listOf(primary) + (MaintenanceDueMeasure.entries - primary)
}

private fun com.bgr3108.kilonom.domain.MaintenanceDueInfo.statusTextFor(
    measure: MaintenanceDueMeasure
): String? = when (measure) {
    MaintenanceDueMeasure.KILOMETERS -> remainingKm?.let { remaining ->
        when (status) {
            MaintenanceDueStatus.OVERDUE -> remaining.takeIf { it < 0 }?.let { "Vencido hace ${abs(it).formatKilometers()} km" }
            MaintenanceDueStatus.DUE_SOON -> remaining.takeIf { it >= 0 }?.let { if (it == 0L) "Toca ahora" else "Faltan ${it.formatKilometers()} km" }
            MaintenanceDueStatus.UP_TO_DATE -> dueKm?.let { "Próximo a los ${it.formatKilometers()} km" }
            MaintenanceDueStatus.NO_DUE_CONFIGURED -> null
        }
    }

    MaintenanceDueMeasure.DATE -> remainingDays?.let { remaining ->
        when (status) {
            MaintenanceDueStatus.OVERDUE -> remaining.takeIf { it < 0 }?.let { "Vencido hace ${abs(it)} ${if (abs(it) == 1L) "día" else "días"}" }
            MaintenanceDueStatus.DUE_SOON -> remaining.takeIf { it >= 0 }?.let { if (it == 0L) "Toca hoy" else "Faltan $it ${if (it == 1L) "día" else "días"}" }
            MaintenanceDueStatus.UP_TO_DATE -> dueDate?.formatMaintenanceDate()
            MaintenanceDueStatus.NO_DUE_CONFIGURED -> null
        }
    }
}

internal fun Long.formatKilometers(): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(this)

internal fun Long.formatMaintenanceDate(): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(this))

internal fun Double.formatMaintenanceMoney(): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).format(this)

/** Keys are namespaced because an item and a record can legitimately have the same database id. */
internal fun maintenanceUpcomingItemKey(itemId: Long): String = "upcoming-item:$itemId"

internal fun maintenanceUnconfiguredItemKey(itemId: Long): String = "unconfigured-item:$itemId"

internal fun maintenanceHistoryRecordKey(recordId: Long): String = "history-record:$recordId"

internal fun com.bgr3108.kilonom.data.MaintenanceRecordEntity.detailsText(): String? = listOfNotNull(
    performedDate?.formatMaintenanceDate(),
    odometerKm?.let { "${it.formatKilometers()} km" },
    cost?.formatMaintenanceMoney()
).takeIf { it.isNotEmpty() }?.joinToString(" · ")
