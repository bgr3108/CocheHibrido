package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.util.toDateString

@Composable
fun ConsumptionListScreen(
    innerPadding: PaddingValues,
    viewModel: FuelEntryViewModel,
    navController: NavController, // 🔥 IMPORTANTE
    onAddClick: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val sortedEntries = entries.sortedByDescending { it.fecha }

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

                    Column(Modifier.padding(16.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                entry.fecha.toDateString(),
                                style = MaterialTheme.typography.labelMedium
                            )

                            Text(
                                "${entry.km.toSpanishDecimal()} km",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // ⛽/🔋 Tipo + cantidad
                        Text(
                            text = if (entry.tipo == FuelType.GASOLINA)
                                "⛽ Gasolina • ${entry.cantidad.toSpanishDecimal()} L"
                            else
                                "🔋 Eléctrico • ${entry.cantidad.toSpanishDecimal()} kWh"
                        )

                        // 🚗 Km
                        Text("Km: ${entry.km.toSpanishDecimal()}")

                        Spacer(modifier = Modifier.height(6.dp))

                        // 💰 Precio unitario
                        val precioUnitario =
                            if (entry.cantidad > 0) entry.precio / entry.cantidad else 0.0

                        val unidad = if (entry.tipo == FuelType.GASOLINA) "€/L" else "€/kWh"

                        Text("Precio: ${precioUnitario.toSpanishDecimal()} $unidad")

                        // 💶 Total
                        Text("Total: ${entry.precio.toSpanishDecimal()} €")

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