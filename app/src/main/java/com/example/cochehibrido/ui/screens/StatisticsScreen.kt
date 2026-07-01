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
import androidx.compose.runtime.getValue
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.data.VehicleType
import com.example.cochehibrido.ui.components.DashboardCard

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

    val precioGasolina by viewModel
        .precioGasolina
        .collectAsStateWithLifecycle()

    val precioElectrico by viewModel
        .precioElectrico
        .collectAsStateWithLifecycle()

    val costPerKm by viewModel
        .costPerKm
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

    val isDark = isSystemInDarkTheme()

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
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Estadísticas",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            DashboardCard(
                title = "Consumos",
                value =
                    if (showFuel)
                        "${consumoGasolina.toSpanishDecimal()} L/100"
                    else
                        "${consumoElectrico.toSpanishDecimal()} kWh/100",

                subtitle = "Consumo medio"
            )

            DashboardCard(
                title = "Precios",
                value =
                    if (showFuel)
                        "${precioGasolina.toSpanishDecimal()} €/L"
                    else
                        "${precioElectrico.toSpanishDecimal()} €/kWh",

                subtitle = "Precio medio"
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),

            colors = CardDefaults.cardColors(
                containerColor = if (isDark) CardBlueDark else CardBlueLight
            ),

            elevation = CardDefaults.cardElevation(4.dp)
        ) {

            Column(
                modifier = Modifier.padding(20.dp)
            ) {

                Text(
                    text = "Totales históricos",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (showFuel) {

                    if (consumoGasolina > 0) {

                        Text(
                            "Gasolina/Diésel/GLP: ${
                                consumoGasolina.toSpanishDecimal()
                            } L/100km"
                        )

                    } else {

                        Text(
                            "Consumo aún no disponible"
                        )
                    }
                }

                if (showElectric) {

                    if (consumoElectrico > 0) {

                        Text(
                            "Eléctrico: ${
                                consumoElectrico.toSpanishDecimal()
                            } kWh/100km"
                        )

                    } else {

                        Text(
                            "Consumo aún no disponible"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (showFuel) {

                    Text(
                        text = "Combustible",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        "Total repostado: ${
                            litrosTotales.toSpanishDecimal()
                        } L"
                    )

                    Text(
                        "Gasto total: ${
                            gastoGasolinaTotal.toSpanishDecimal()
                        } €"
                    )
                    Text(
                        "Precio medio: ${
                            precioGasolina.toSpanishDecimal()
                        } €/L"
                    )
                }

                if (showFuel && showElectric) {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (showElectric) {

                    Text(
                        text = "Electricidad",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        "Total cargado: ${
                            kwhTotales.toSpanishDecimal()
                        } kWh"
                    )

                    Text(
                        "Gasto total: ${
                            gastoElectricoTotal.toSpanishDecimal()
                        } €"
                    )
                    Text(
                        "Precio medio: ${
                            precioElectrico.toSpanishDecimal()
                        } €/kWh"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Coste global",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    "Coste por km: ${
                        costPerKm.toSpanishDecimal()
                    } €/km"
                )
            }
        }
    }
}