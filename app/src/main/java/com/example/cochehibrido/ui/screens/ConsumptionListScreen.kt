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

@Composable
fun ConsumptionListScreen(
    innerPadding: PaddingValues,
    viewModel: FuelEntryViewModel,
    navController: NavController, // 🔥 IMPORTANTE
    onAddClick: () -> Unit
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

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
                items(entries, key = { it.id }) { entry ->

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {

                            Text(text = "${entry.fecha}")

                            Text("Km: ${entry.km.toSpanishDecimal()}")

                            Text(
                                text = if (entry.tipo == FuelType.GASOLINA)
                                    "⛽ ${entry.cantidad.toSpanishDecimal()} L"
                                else
                                    "🔋 ${entry.cantidad.toSpanishDecimal()} kWh"
                            )

                            Text(
                                if (entry.precio == 0.0)
                                    "Gratis"
                                else
                                    "Precio: ${entry.precio.toSpanishDecimal()} €"
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                // 🔥 EDITAR
                                Button(
                                    onClick = {
                                        navController.navigate("edit_refuel/${entry.id}")
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Editar")
                                }

                                // 🔥 ELIMINAR
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