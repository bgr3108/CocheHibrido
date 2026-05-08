package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark


@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel
) {
    // 🔵 TARJETAS (NO TOCAR)
    val precioGasolina by viewModel.precioGasolina.collectAsStateWithLifecycle()
    val precioElectrico by viewModel.precioElectrico.collectAsStateWithLifecycle()
    val costeGasolinaKm by viewModel.costeGasolinaKm.collectAsStateWithLifecycle(initialValue = 0.0)
    val costeElectricoKm by viewModel.costeElectricoKm.collectAsStateWithLifecycle(initialValue = 0.0)
    val isDark = isSystemInDarkTheme()
// 🟢 NUEVO (CÁLCULO REAL)
    val totalKm by viewModel.totalKm.collectAsStateWithLifecycle()
    val totalCost by viewModel.totalCost.collectAsStateWithLifecycle()
    val costPerKm by viewModel.costPerKm.collectAsStateWithLifecycle()

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

        // 🔥 NUEVA TARJETA: cálculo real
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) CardBlueDark else CardBlueLight
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ){
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Cálculo total",
                    style = MaterialTheme.typography.titleMedium
                )

                Text("Km totales: %.1f km".format(totalKm))
                Text("Coste total: %.2f €".format(totalCost))
                Text("€/km real: %.3f €".format(costPerKm))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            // 🔋 ELÉCTRICO
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                ),
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
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                ),
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