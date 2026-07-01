package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.data.VehicleType
import com.example.cochehibrido.ui.components.DashboardCard
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.compose.foundation.layout.PaddingValues

@Composable
fun StatisticsScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel
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

    val costPerKm by viewModel
        .costPerKm
        .collectAsStateWithLifecycle()

    val totalKm by viewModel
        .totalKm
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

        // ============================================================
        // Consumos
        // ============================================================

        if (showFuel && showElectric) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Consumo gasolina",
                    value = consumoGasolina.toSpanishDecimal(),
                    subtitle  = "L/100 km",
                    icon = Icons.Default.LocalGasStation
                )

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Consumo eléctrico",
                    value = consumoElectrico.toSpanishDecimal(),
                    subtitle  = "kWh/100 km",
                    icon = Icons.Default.Bolt
                )
            }

        } else {

            DashboardCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Consumo",
                value = if (showFuel)
                    consumoGasolina.toSpanishDecimal()
                else
                    consumoElectrico.toSpanishDecimal(),
                subtitle  = if (showFuel)
                    "L/100 km"
                else
                    "kWh/100 km",
                icon = if (showFuel)
                    Icons.Default.LocalGasStation
                else
                    Icons.Default.Bolt
            )
        }

        // ============================================================
        // Precio medio
        // ============================================================

        if (showFuel && showElectric) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Precio gasolina",
                    value = precioGasolina.toSpanishDecimal(),
                    subtitle  = "€/L",
                    icon = Icons.Default.AccountBalanceWallet
                )

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Precio electricidad",
                    value = precioElectrico.toSpanishDecimal(),
                    subtitle  = "€/kWh",
                    icon = Icons.Default.Bolt
                )
            }

        } else {

            DashboardCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Precio medio",
                value = if (showFuel)
                    precioGasolina.toSpanishDecimal()
                else
                    precioElectrico.toSpanishDecimal(),
                subtitle  = if (showFuel)
                    "€/L"
                else
                    "€/kWh",
                icon = Icons.Default.AccountBalanceWallet
            )
        }

        // ============================================================
        // Totales
        // ============================================================

        if (showFuel && showElectric) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Repostado",
                    value = litrosTotales.toSpanishDecimal(),
                    subtitle  = "L",
                    icon = Icons.Default.LocalGasStation
                )

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Cargado",
                    value = kwhTotales.toSpanishDecimal(),
                    subtitle  = "kWh",
                    icon = Icons.Default.Bolt
                )
            }

        } else {

            DashboardCard(
                modifier = Modifier.fillMaxWidth(),
                title = if (showFuel) "Repostado" else "Cargado",
                value = if (showFuel)
                    litrosTotales.toSpanishDecimal()
                else
                    kwhTotales.toSpanishDecimal(),
                subtitle  = if (showFuel)
                    "L"
                else
                    "kWh",
                icon = if (showFuel)
                    Icons.Default.LocalGasStation
                else
                    Icons.Default.Bolt
            )
        }

        // ============================================================
        // Gasto
        // ============================================================

        if (showFuel && showElectric) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Gasto gasolina",
                    value = gastoGasolinaTotal.toSpanishDecimal(),
                    subtitle  = "€",
                    icon = Icons.Default.Payments
                )

                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Gasto electricidad",
                    value = gastoElectricoTotal.toSpanishDecimal(),
                    subtitle  = "€",
                    icon = Icons.Default.Payments
                )
            }

        } else {

            DashboardCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Gasto",
                value = if (showFuel)
                    gastoGasolinaTotal.toSpanishDecimal()
                else
                    gastoElectricoTotal.toSpanishDecimal(),
                subtitle  = "€",
                icon = Icons.Default.Payments
            )
        }

        // ============================================================
        // Global
        // ============================================================

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "Coste/km",
                value = costPerKm.toSpanishDecimal(),
                subtitle  = "€/km",
                icon = Icons.AutoMirrored.Filled.TrendingUp
            )

            DashboardCard(
                modifier = Modifier.weight(1f),
                title = "Kilómetros",
                value = totalKm.toSpanishDecimal(),
                subtitle  = "km",
                icon = Icons.Default.Route
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}