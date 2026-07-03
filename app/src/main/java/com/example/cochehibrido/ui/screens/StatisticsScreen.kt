package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.data.VehicleType
import com.example.cochehibrido.ui.components.DashboardCard
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.viewmodel.HomeViewModel

@Composable
fun StatisticsScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenConsumption: () -> Unit
) {

    val consumoGasolina by viewModel
        .consumoGasolina
        .collectAsStateWithLifecycle()

    val consumoElectrico by viewModel
        .consumoElectrico
        .collectAsStateWithLifecycle()

    val precioGasolina by viewModel
        .precioGasolina
        .collectAsStateWithLifecycle()

    val precioElectrico by viewModel
        .precioElectrico
        .collectAsStateWithLifecycle()

    val litrosTotales by viewModel
        .litrosTotales
        .collectAsStateWithLifecycle(0.0)

    val gastoGasolinaTotal by viewModel
        .gastoGasolinaTotal
        .collectAsStateWithLifecycle(0.0)

    val kwhTotales by viewModel
        .kwhTotales
        .collectAsStateWithLifecycle(0.0)

    val gastoElectricoTotal by viewModel
        .gastoElectricoTotal
        .collectAsStateWithLifecycle(0.0)

    val totalKm by viewModel
        .totalKm
        .collectAsStateWithLifecycle()

    val costPerKm by viewModel
        .costPerKm
        .collectAsStateWithLifecycle()

    val vehicle by viewModel
        .vehicle
        .collectAsStateWithLifecycle()

    val showFuel =
        vehicle.type != VehicleType.ELECTRICO

    val showElectric =
        vehicle.type == VehicleType.ELECTRICO ||
                vehicle.type == VehicleType.HIBRIDO_ENCHUFABLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Estadísticas",
            style = MaterialTheme.typography.headlineSmall
        )

        DashboardCard(
            title = "Consumos",
            icon = Icons.Default.LocalGasStation,
            showDetailArrow = true,
            onClick = {
                onOpenConsumption()
            }
        ) {

            if (showFuel) {

                Text(
                    "Gasolina",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    "${consumoGasolina.toSpanishDecimal()} L/100 km",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showFuel && showElectric) {

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (showElectric) {

                Text(
                    "Electricidad",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    "${consumoElectrico.toSpanishDecimal()} kWh/100 km",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

        }

        DashboardCard(
            title = "Precios",
            icon = Icons.Default.AccountBalanceWallet,
            showDetailArrow = true
        ) {

            if (showFuel) {

                Text(
                    "Gasolina",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    precioGasolina.toSpanishDecimal(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "€/L",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (showFuel && showElectric) {

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))
            }

            if (showElectric) {

                Text(
                    "Electricidad",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    precioElectrico.toSpanishDecimal(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "€/kWh",
                    style = MaterialTheme.typography.bodySmall
                )
            }

        }

        DashboardCard(
            title = "Totales",
            icon = Icons.Default.Route,
            showDetailArrow = true
        ) {

            if (showFuel) {

                Text(
                    "Combustible"
                )

                Text(
                    "${litrosTotales.toSpanishDecimal()} L",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showElectric) {

                Text(
                    "Electricidad"
                )

                Text(
                    "${kwhTotales.toSpanishDecimal()} kWh",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                "Kilómetros"
            )

            Text(
                "${totalKm.toSpanishDecimal()} km",
                style = MaterialTheme.typography.titleLarge
            )

        }

        DashboardCard(
            title = "Costes",
            icon = Icons.Default.Payments,
            showDetailArrow = true
        ) {

            if (showFuel) {

                Text(
                    "Gasolina"
                )

                Text(
                    "${gastoGasolinaTotal.toSpanishDecimal()} €",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (showElectric) {

                Text(
                    "Electricidad"
                )

                Text(
                    "${gastoElectricoTotal.toSpanishDecimal()} €",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(
                "Coste por km"
            )

            Text(
                "${costPerKm.toSpanishDecimal()} €/km",
                style = MaterialTheme.typography.titleLarge
            )

        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}