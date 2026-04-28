package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.viewmodel.FuelEntryViewModel

@Composable
fun AddConsumptionScreen(
    viewModel: FuelEntryViewModel,
    onClose: () -> Unit
) {

    var cantidad by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var tipoSeleccionado by remember { mutableStateOf(FuelType.GASOLINA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Text("Nuevo repostaje", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it },
            label = { Text("Cantidad (L / kWh)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = precio,
            onValueChange = { precio = it },
            label = { Text("Precio (€)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("Kilómetros") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Button(
                onClick = { tipoSeleccionado = FuelType.GASOLINA },
                modifier = Modifier.weight(1f)
            ) {
                Text("Gasolina")
            }

            Button(
                onClick = { tipoSeleccionado = FuelType.ELECTRICO },
                modifier = Modifier.weight(1f)
            ) {
                Text("Eléctrico")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {

                val entry = FuelEntry(
                    fecha = System.currentTimeMillis(), // 🔥 FIX
                    cantidad = cantidad.toDoubleOrNull() ?: 0.0,
                    precio = precio.toDoubleOrNull() ?: 0.0,
                    tipo = tipoSeleccionado,
                    km = km.toDoubleOrNull() ?: 0.0
                )

                viewModel.saveEntry(entry) {
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancelar")
        }
    }
}