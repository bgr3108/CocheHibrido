package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.util.toDateTimeString
import androidx.compose.runtime.getValue
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight

@Composable
fun StatisticsScreen(
    viewModel: HomeViewModel
) {
    val consumoGasolina by viewModel
        .consumoGasolina
        .collectAsStateWithLifecycle()

    val consumoElectrico by viewModel
        .consumoElectrico
        .collectAsStateWithLifecycle()

    val porcentajeElectrico by viewModel
        .porcentajeElectrico
        .collectAsStateWithLifecycle()

    val ultimoGasolina by viewModel
        .ultimoGasolina
        .collectAsStateWithLifecycle()

    val ultimoElectrico by viewModel
        .ultimoElectrico
        .collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Estadísticas",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {

            // 🔋 Última carga
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),

                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                )
            ) {


                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        "Última carga",
                        style = MaterialTheme.typography.titleSmall
                    )

                    ultimoElectrico?.let { entry ->

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(entry.fecha.toDateTimeString())

                        Text(
                            "${entry.cantidad.toSpanishDecimal()} kWh"
                        )

                        Text(
                            "${entry.precio.toSpanishDecimal()} €"
                        )
                    }
                }
            }

            // ⛽ Último repostaje
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),

                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                )
            ) {


                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        "Último repostaje",
                        style = MaterialTheme.typography.titleSmall
                    )

                    ultimoGasolina?.let { entry ->

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(entry.fecha.toDateTimeString())

                        Text(
                            "${entry.cantidad.toSpanishDecimal()} L"
                        )

                        Text(
                            "${entry.precio.toSpanishDecimal()} €"
                        )
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),

            colors = CardDefaults.cardColors(
                containerColor = if (isDark) CardBlueDark else CardBlueLight
            ),

            elevation = CardDefaults.cardElevation(4.dp)
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
            ) {

                Text(
                    text = "Consumos",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Gasolina: ${
                        consumoGasolina.toSpanishDecimal()
                    } L/100km"
                )

                Text(
                    "Eléctrico: ${
                        consumoElectrico.toSpanishDecimal()
                    } kWh/100km"
                )

                Text(
                    "Uso eléctrico: ${
                        porcentajeElectrico.toSpanishDecimal()
                    }%"
                )
            }
        }
    }
}