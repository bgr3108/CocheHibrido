package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {

    val precioGasolina by viewModel
        .precioGasolina
        .collectAsStateWithLifecycle()

    val precioMinimoGasolina by viewModel
        .precioMinimoGasolina
        .collectAsStateWithLifecycle()

    val precioMaximoGasolina by viewModel
        .precioMaximoGasolina
        .collectAsStateWithLifecycle()

    val numeroRepostajes by viewModel
        .numeroRepostajes
        .collectAsStateWithLifecycle()

    val precioElectrico by viewModel
        .precioElectrico
        .collectAsStateWithLifecycle()

    val precioMinimoElectrico by viewModel
        .precioMinimoElectrico
        .collectAsStateWithLifecycle()

    val precioMaximoElectrico by viewModel
        .precioMaximoElectrico
        .collectAsStateWithLifecycle()

    val numeroCargas by viewModel
        .numeroCargas
        .collectAsStateWithLifecycle()

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {
                    Text("Precios")
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

        PriceContent(

                innerPadding = innerPadding,

            precioGasolina = precioGasolina,
            precioMinimoGasolina = precioMinimoGasolina,
            precioMaximoGasolina = precioMaximoGasolina,
            numeroRepostajes = numeroRepostajes,

            precioElectrico = precioElectrico,
            precioMinimoElectrico = precioMinimoElectrico,
            precioMaximoElectrico = precioMaximoElectrico,
            numeroCargas = numeroCargas

        )

    }
}

@Composable
private fun PriceContent(

    innerPadding: PaddingValues,

    precioGasolina: Double,
    precioMinimoGasolina: Double,
    precioMaximoGasolina: Double,
    numeroRepostajes: Int,

    precioElectrico: Double,
    precioMinimoElectrico: Double,
    precioMaximoElectrico: Double,
    numeroCargas: Int

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
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.LocalGasStation,

                        contentDescription = null,

                        tint =
                            MaterialTheme.colorScheme.primary

                    )

                    Text(

                        text = "Gasolina",

                        style =
                            MaterialTheme.typography.titleLarge

                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                StatisticRow(
                    "Precio medio",
                    "${precioGasolina.toSpanishDecimal()} €/L"
                )

                StatisticRow(
                    "Precio mínimo",
                    "${precioMinimoGasolina.toSpanishDecimal()} €/L"
                )

                StatisticRow(
                    "Precio máximo",
                    "${precioMaximoGasolina.toSpanishDecimal()} €/L"
                )

                StatisticRow(
                    "Repostajes",
                    numeroRepostajes.toString()
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(

                    text = "Gráfico próximamente",

                    color =
                        MaterialTheme.colorScheme.primary

                )
            }
        }

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
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Bolt,

                        contentDescription = null,

                        tint =
                            MaterialTheme.colorScheme.primary

                    )

                    Text(

                        text = "Electricidad",

                        style =
                            MaterialTheme.typography.titleLarge

                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                StatisticRow(
                    "Precio medio",
                    "${precioElectrico.toSpanishDecimal()} €/kWh"
                )

                StatisticRow(
                    "Precio mínimo",
                    "${precioMinimoElectrico.toSpanishDecimal()} €/kWh"
                )

                StatisticRow(
                    "Precio máximo",
                    "${precioMaximoElectrico.toSpanishDecimal()} €/kWh"
                )

                StatisticRow(
                    "Cargas",
                    numeroCargas.toString()
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(

                    text = "Gráfico próximamente",

                    color =
                        MaterialTheme.colorScheme.primary

                )
            }
        }
    }
}