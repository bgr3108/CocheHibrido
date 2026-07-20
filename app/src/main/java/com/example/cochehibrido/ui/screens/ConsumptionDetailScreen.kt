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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.ui.components.StatisticRow
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumptionDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {

    val consumoGasolina by viewModel
        .consumoGasolina
        .collectAsStateWithLifecycle()

    val consumoElectrico by viewModel
        .consumoElectrico
        .collectAsStateWithLifecycle()

    val mejorConsumoGasolina by viewModel
        .mejorConsumoGasolina
        .collectAsStateWithLifecycle()

    val peorConsumoGasolina by viewModel
        .peorConsumoGasolina
        .collectAsStateWithLifecycle()

    val numeroTramosGasolina by viewModel
        .numeroTramosGasolina
        .collectAsStateWithLifecycle()

    val mejorConsumoElectrico by viewModel
        .mejorConsumoElectrico
        .collectAsStateWithLifecycle()

    val peorConsumoElectrico by viewModel
        .peorConsumoElectrico
        .collectAsStateWithLifecycle()

    val numeroTramosElectricos by viewModel
        .numeroTramosElectricos
        .collectAsStateWithLifecycle()

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {
                    Text("Consumos")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },

                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }

    ) { innerPadding ->

        ConsumptionContent(

            innerPadding = innerPadding,

            consumoGasolina = consumoGasolina,
            mejorConsumoGasolina = mejorConsumoGasolina,
            peorConsumoGasolina = peorConsumoGasolina,
            numeroTramosGasolina = numeroTramosGasolina,

            consumoElectrico = consumoElectrico,
            mejorConsumoElectrico = mejorConsumoElectrico,
            peorConsumoElectrico = peorConsumoElectrico,
            numeroTramosElectricos = numeroTramosElectricos

        )

    }
}

@Composable
private fun ConsumptionContent(

    innerPadding: PaddingValues,

    consumoGasolina: Double,
    mejorConsumoGasolina: Double,
    peorConsumoGasolina: Double,
    numeroTramosGasolina: Int,

    consumoElectrico: Double,
    mejorConsumoElectrico: Double,
    peorConsumoElectrico: Double,
    numeroTramosElectricos: Int

) {

    LazyColumn(

        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),

        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp
        ),

        verticalArrangement = Arrangement.spacedBy(16.dp)

    ) {

        item {
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

                            imageVector = Icons.Default.LocalGasStation,

                            contentDescription = null,

                            tint = MaterialTheme.colorScheme.primary

                        )

                        Text(
                            "Gasolina",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StatisticRow(
                        "Consumo medio",
                        "${consumoGasolina.toSpanishDecimal()} L/100 km"
                    )

                    StatisticRow(
                        "Mejor consumo",
                        "${mejorConsumoGasolina.toSpanishDecimal()} L/100 km"
                    )

                    StatisticRow(
                        "Peor consumo",
                        "${peorConsumoGasolina.toSpanishDecimal()} L/100 km"
                    )

                    StatisticRow(
                        "Tramos calculados",
                        numeroTramosGasolina.toString()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(
                        modifier = Modifier.height(220.dp)
                    )
                }
            }
        }
        item {
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

                            imageVector = Icons.Default.Bolt,

                            contentDescription = null,

                            tint = MaterialTheme.colorScheme.primary

                        )

                        Text(
                            "Electricidad",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    StatisticRow(
                        "Consumo medio",
                        "${consumoElectrico.toSpanishDecimal()} kWh/100 km"
                    )

                    StatisticRow(
                        "Mejor consumo",
                        "${mejorConsumoElectrico.toSpanishDecimal()} kWh/100 km"
                    )

                    StatisticRow(
                        "Peor consumo",
                        "${peorConsumoElectrico.toSpanishDecimal()} kWh/100 km"
                    )

                    StatisticRow(
                        "Tramos calculados",
                        numeroTramosElectricos.toString()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(20.dp))

                    Spacer(
                        modifier = Modifier.height(220.dp)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(300.dp))
}