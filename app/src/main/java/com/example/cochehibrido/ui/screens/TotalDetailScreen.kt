package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.ui.components.StatisticRow
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalDetailScreen(

    viewModel: HomeViewModel,

    onBack: () -> Unit

) {

    val totalKm by viewModel
        .totalKm
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

    val gastoTotalVehiculo by viewModel
        .gastoTotalVehiculo
        .collectAsStateWithLifecycle()

    val costPerKm by viewModel
        .costPerKm
        .collectAsStateWithLifecycle()

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {

                    Text("Totales")

                },

                navigationIcon = {

                    IconButton(

                        onClick = onBack

                    ) {

                        Icon(

                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription = "Volver"

                        )

                    }

                },

                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors()

            )

        }

    ) { innerPadding ->

        TotalContent(

            innerPadding = innerPadding,

            totalKm = totalKm,

            litrosTotales = litrosTotales,

            gastoGasolinaTotal = gastoGasolinaTotal,

            kwhTotales = kwhTotales,

            gastoElectricoTotal = gastoElectricoTotal,

            gastoTotalVehiculo = gastoTotalVehiculo,

            costPerKm = costPerKm

        )

    }

}
@Composable
private fun TotalContent(

    innerPadding: PaddingValues,

    totalKm: Double,

    litrosTotales: Double,

    gastoGasolinaTotal: Double,

    kwhTotales: Double,

    gastoElectricoTotal: Double,

    gastoTotalVehiculo: Double,

    costPerKm: Double

) {

    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)

    ) {

        Card(

            colors = CardDefaults.cardColors(

                containerColor =
                    if (isSystemInDarkTheme())
                        CardBlueDark
                    else
                        CardBlueLight

            ),

            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )

        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Resumen del vehículo",
                        style = MaterialTheme.typography.titleLarge
                    )

                }

                Spacer(modifier = Modifier.height(16.dp))

                StatisticRow(
                    "Kilómetros recorridos",
                    "${totalKm.toSpanishDecimal()} km"
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Gasolina",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatisticRow(
                    "Litros consumidos",
                    "${litrosTotales.toSpanishDecimal()} L"
                )

                StatisticRow(
                    "Gasto total",
                    "${gastoGasolinaTotal.toSpanishDecimal()} €"
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Electricidad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatisticRow(
                    "kWh cargados",
                    "${kwhTotales.toSpanishDecimal()} kWh"
                )

                StatisticRow(
                    "Gasto total",
                    "${gastoElectricoTotal.toSpanishDecimal()} €"
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Resumen económico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                StatisticRow(
                    "Gasto total del vehículo",
                    "${gastoTotalVehiculo.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Coste por km",
                    "${costPerKm.toSpanishDecimal()} €/km"
                )
            }
        }
    }
}