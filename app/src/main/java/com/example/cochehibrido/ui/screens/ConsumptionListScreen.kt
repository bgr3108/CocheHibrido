package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.domain.calculateUnitPrice
import com.example.cochehibrido.viewmodel.DateFilter
import com.example.cochehibrido.viewmodel.EnergyFilter
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.viewmodel.supportedDateFilters
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.util.toDateTimeString

@Composable
fun ConsumptionListScreen(
    innerPadding: PaddingValues,
    viewModel: FuelEntryViewModel,
    navController: NavController, // 🔥 IMPORTANTE
    onAddClick: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    var entryToDelete by remember { mutableStateOf<FuelEntry?>(null) }
    var isDateMenuExpanded by remember { mutableStateOf(false) }
    val isDateFilterActive = filterState.dateFilter != DateFilter.ALL
    val entryCountText = if (filterState.hasActiveFilters) {
        "${filteredEntries.size} de ${entries.size}"
    } else {
        "${entries.size} registros"
    }
    val filterChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
    )
    val dateFilterColors = ButtonDefaults.outlinedButtonColors(
        containerColor = if (isDateFilterActive) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        contentColor = if (isDateFilterActive) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
    val dateFilterBorder = BorderStroke(
        width = 1.dp,
        color = if (isDateFilterActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    )
    val dateMenuItemColors = MenuDefaults.itemColors(
        textColor = MaterialTheme.colorScheme.onSurface,
        leadingIconColor = MaterialTheme.colorScheme.onSurface,
        trailingIconColor = MaterialTheme.colorScheme.onSurface
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

        Text("Repostajes", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterState.energyFilter == EnergyFilter.ALL,
                onClick = { viewModel.setEnergyFilter(EnergyFilter.ALL) },
                label = { Text("Todos") },
                colors = filterChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterState.energyFilter == EnergyFilter.ALL,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            FilterChip(
                selected = filterState.energyFilter == EnergyFilter.GASOLINE,
                onClick = { viewModel.setEnergyFilter(EnergyFilter.GASOLINE) },
                label = { Text("Gasolina") },
                colors = filterChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterState.energyFilter == EnergyFilter.GASOLINE,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            FilterChip(
                selected = filterState.energyFilter == EnergyFilter.ELECTRIC,
                onClick = { viewModel.setEnergyFilter(EnergyFilter.ELECTRIC) },
                label = { Text("Electricidad") },
                colors = filterChipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filterState.energyFilter == EnergyFilter.ELECTRIC,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OutlinedButton(
                    onClick = { isDateMenuExpanded = true },
                    colors = dateFilterColors,
                    border = dateFilterBorder
                ) {
                    Text("${filterState.dateFilter.label()} ▼")
                }
                DropdownMenu(
                    expanded = isDateMenuExpanded,
                    onDismissRequest = { isDateMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    supportedDateFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label()) },
                            colors = dateMenuItemColors,
                            onClick = {
                                viewModel.setDateFilter(filter)
                                isDateMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = entryCountText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.width(72.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (filterState.hasActiveFilters) {
                    IconButton(onClick = viewModel::clearFilters) {
                        Icon(
                            imageVector = Icons.Outlined.FilterAltOff,
                            contentDescription = "Restablecer filtros",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (entries.isEmpty() && !filterState.hasActiveFilters) {
            Text("No hay repostajes")
        } else if (filteredEntries.isEmpty()) {
            Text("No hay consumos para los filtros seleccionados.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) CardBlueDark else CardBlueLight
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    entry.fecha.toDateTimeString(),
                                    style = MaterialTheme.typography.labelMedium
                                )

                                Text(
                                    "${entry.km.toSpanishDecimal()} km",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }


                            Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (entry.tipo == FuelType.GASOLINA) {
                                    Icons.Outlined.LocalGasStation
                                } else {
                                    Icons.Outlined.Bolt
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (entry.tipo == FuelType.GASOLINA)
                                    "Gasolina • ${entry.cantidad.toSpanishDecimal()} L"
                                else
                                    "Eléctrico • ${entry.cantidad.toSpanishDecimal()} kWh"
                            )
                        }


                        Spacer(modifier = Modifier.height(6.dp))

                        // 💰 Precio unitario
                        val precioUnitario = calculateUnitPrice(entry) ?: 0.0

                        val unidad = if (entry.tipo == FuelType.GASOLINA) "€/L" else "€/kWh"

                        Text("Precio: ${precioUnitario.toSpanishDecimal()} $unidad")

                        // 💶 Total
                        Text("Total: ${entry.precio.toSpanishDecimal()} €")

                            if (entry.tipo == FuelType.GASOLINA) {

                                Text(
                                    if (entry.fullTank)
                                        "☑ Lleno"
                                    else
                                        "◻ Parcial"
                                )
                            }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Botones
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    navController.navigate("edit_refuel/${entry.id}")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Editar")
                            }

                            OutlinedButton(
                                onClick = { entryToDelete = entry },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }
        }
        }
    }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir consumo"
            )
        }
    }

    // 🔥 CONFIRMACIÓN BORRAR
    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry)
                    entryToDelete = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Eliminar repostaje") },
            text = { Text("¿Seguro que quieres eliminar este registro?") }
        )
    }
    }

private fun DateFilter.label(): String = when (this) {
    DateFilter.ALL -> "Todo"
    DateFilter.THIS_MONTH -> "Este mes"
    DateFilter.LAST_MONTH -> "Mes anterior"
    DateFilter.THIS_YEAR -> "Este año"
}
