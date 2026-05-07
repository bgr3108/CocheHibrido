package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.data.BaselineRepository
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SetupScreen(
    baselineRepository: BaselineRepository,
    onDone: () -> Unit
) {

    var km by remember { mutableStateOf("") }
    var gasolina by remember { mutableStateOf("") }
    var electrico by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Configura tu coche",
            style = MaterialTheme.typography.headlineSmall
        )

            OutlinedTextField(
                value = km,
                onValueChange = { km = it },
                label = { Text("Km actuales") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )

        OutlinedTextField(
            value = gasolina,
            onValueChange = { gasolina = it },
            label = { Text("Consumo gasolina (L/100km)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = electrico,
            onValueChange = { electrico = it },
            label = { Text("Consumo eléctrico (kWh/100km)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            onClick = {

                baselineRepository.saveBaseline(
                    km.toDoubleOrNull() ?: 0.0,
                    gasolina.toDoubleOrNull() ?: 0.0,
                    electrico.toDoubleOrNull() ?: 0.0
                )

                onDone()
            },
            enabled =
                km.isNotBlank() &&
                        gasolina.isNotBlank() &&
                        electrico.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
    }
}