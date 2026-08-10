package com.bgr3108.kilonom.ui.screens

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.compose.foundation.isSystemInDarkTheme
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.ui.theme.CardBlueDark

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.domain.calculateUnitPrice
import com.bgr3108.kilonom.viewmodel.DateFilter
import com.bgr3108.kilonom.viewmodel.EnergyFilter
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.supportedDateFilters
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toDateTimeString

@Composable
fun ConsumptionListScreen(
    innerPadding: PaddingValues,
    viewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    navController: NavController, // 🔥 IMPORTANTE
    onAddClick: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val vehicle by homeViewModel.vehicle.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val entryToDelete = remember { mutableStateOf<FuelEntry?>(null) }
    var isDateMenuExpanded by remember { mutableStateOf(false) }
    val isDateFilterActive = filterState.dateFilter != DateFilter.ALL
    val showFuel = vehicle.type.supportsFuelEntries
    val showElectric = vehicle.type.supportsElectricEntries

    LaunchedEffect(showFuel, showElectric) {
        val selectedEnergy = filterState.energyFilter
        val hasInvalidEnergyFilter =
            (selectedEnergy == EnergyFilter.GASOLINE && !showFuel) ||
                    (selectedEnergy == EnergyFilter.ELECTRIC && !showElectric)

        if (hasInvalidEnergyFilter) {
            viewModel.setEnergyFilter(EnergyFilter.ALL)
        }
    }
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDateFilters()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

        Text("Consumos", style = MaterialTheme.typography.headlineSmall)

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
            if (showFuel) {
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
            }
            if (showElectric) {
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
            Text("No hay consumos")
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
                                    "${entry.km.toKilometersDisplay()} km",
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
                                onClick = { entryToDelete.value = entry },
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
    entryToDelete.value?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete.value = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry)
                    entryToDelete.value = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete.value = null }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Eliminar consumo") },
            text = { Text("¿Seguro que quieres eliminar este consumo?") }
        )
    }
    }

private fun DateFilter.label(): String = when (this) {
    DateFilter.ALL -> "Todo"
    DateFilter.THIS_MONTH -> "Este mes"
    DateFilter.LAST_MONTH -> "Mes anterior"
    DateFilter.THIS_YEAR -> "Este año"
}
