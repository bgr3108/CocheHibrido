package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
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
    val sortedEntries = entries.sortedByDescending { it.fecha }
    val isDark = isSystemInDarkTheme()
    var entryToDelete by remember { mutableStateOf<FuelEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {

        Text("Repostajes", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Añadir repostaje")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (entries.isEmpty()) {
            Text("No hay repostajes")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sortedEntries, key = { it.id }) { entry ->

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
                        val precioUnitario =
                            if (entry.cantidad > 0) entry.precio / entry.cantidad else 0.0

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
}
