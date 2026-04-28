package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel
) {
    val precioGasolina by viewModel.precioGasolina.collectAsStateWithLifecycle()
    val precioElectrico by viewModel.precioElectrico.collectAsStateWithLifecycle()

    val costeGasolinaKm by viewModel.costeGasolinaKm.collectAsStateWithLifecycle(initialValue = 0.0)
    val costeElectricoKm by viewModel.costeElectricoKm.collectAsStateWithLifecycle(initialValue = 0.0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {

        // Título
        Text(
            text = "Inicio",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta principal
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Resumen energético",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Coste por km calculado automáticamente",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            // 🔋 ELÉCTRICO
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text("🔋 Eléctrico")

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "%.2f €/kWh".format(precioElectrico),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "%.3f €/km".format(costeElectricoKm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ⛽ GASOLINA
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text("⛽ Gasolina")

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "%.2f €/L".format(precioGasolina),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "%.3f €/km".format(costeGasolinaKm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}