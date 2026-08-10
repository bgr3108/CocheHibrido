package com.bgr3108.kilonom.ui.screens

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
import com.bgr3108.kilonom.ui.components.StatisticRow
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.data.MonthlyCost
import com.bgr3108.kilonom.ui.components.charts.BarChart
import com.bgr3108.kilonom.ui.components.charts.ChartPoint
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {

    val costeTotalGasolina by viewModel
        .costeTotalGasolina
        .collectAsStateWithLifecycle()

    val repostajeMasCaro by viewModel
        .repostajeMasCaro
        .collectAsStateWithLifecycle()

    val repostajeMasBarato by viewModel
        .repostajeMasBarato
        .collectAsStateWithLifecycle()

    val numeroRepostajes by viewModel
        .numeroRepostajes
        .collectAsStateWithLifecycle()

    val costeTotalElectrico by viewModel
        .costeTotalElectrico
        .collectAsStateWithLifecycle()

    val cargaMasCara by viewModel
        .cargaMasCara
        .collectAsStateWithLifecycle()

    val cargaMasBarataDePago by viewModel
        .cargaMasBarataDePago
        .collectAsStateWithLifecycle()

    val cargasGratuitas by viewModel
        .cargasGratuitas
        .collectAsStateWithLifecycle()

    val numeroCargas by viewModel
        .numeroCargas
        .collectAsStateWithLifecycle()

    val historialCostesGasolina by viewModel
        .historialCostesGasolina
        .collectAsStateWithLifecycle()

    val historialCostesElectricos by viewModel
        .historialCostesElectricos
        .collectAsStateWithLifecycle()

    val costesMensualesGasolina by viewModel
        .monthlyGasolineCosts
        .collectAsStateWithLifecycle()

    val costesMensualesElectricos by viewModel
        .monthlyElectricCosts
        .collectAsStateWithLifecycle()

    val vehicle by viewModel
        .vehicle
        .collectAsStateWithLifecycle()

    val showFuel = vehicle.type.supportsFuelEntries
    val showElectric = vehicle.type.supportsElectricEntries

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {
                    Text("Costes")
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

                colors = TopAppBarDefaults.topAppBarColors()
            )
        }

    ) { innerPadding ->

        CostContent(

            innerPadding = innerPadding,

            costeTotalGasolina = costeTotalGasolina,
            repostajeMasCaro = repostajeMasCaro,
            repostajeMasBarato = repostajeMasBarato,
            numeroRepostajes = numeroRepostajes,

            costeTotalElectrico = costeTotalElectrico,
            cargaMasCara = cargaMasCara,
            cargaMasBarataDePago = cargaMasBarataDePago,
            numeroCargas = numeroCargas,
            cargasGratuitas = cargasGratuitas,

            historialCostesGasolina = historialCostesGasolina,
            historialCostesElectricos = historialCostesElectricos,
            costesMensualesGasolina = costesMensualesGasolina,
            costesMensualesElectricos = costesMensualesElectricos,
            showFuel = showFuel,
            showElectric = showElectric

        )

    }
}

@Composable
private fun CostContent(

    innerPadding: PaddingValues,

    costeTotalGasolina: Double,
    repostajeMasCaro: Double,
    repostajeMasBarato: Double,
    numeroRepostajes: Int,

    costeTotalElectrico: Double,
    cargaMasCara: Double,
    cargaMasBarataDePago: Double?,
    numeroCargas: Int,
    cargasGratuitas: Int,

    historialCostesGasolina: List<ChartPoint>,
    historialCostesElectricos: List<ChartPoint>,
    costesMensualesGasolina: List<MonthlyCost>,
    costesMensualesElectricos: List<MonthlyCost>,
    showFuel: Boolean,
    showElectric: Boolean

) {

    Column(

        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)

    ) {

        if (showFuel) {
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
                    "Coste total",
                    "${costeTotalGasolina.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Repostaje más caro",
                    "${repostajeMasCaro.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Repostaje más barato",
                    "${repostajeMasBarato.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Repostajes",
                    numeroRepostajes.toString()
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Gasto mensual",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (historialCostesGasolina.isEmpty()) {
                    Text(
                        text = "Registra consumos para ver la evolución mensual de tus gastos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    BarChart(
                        points = historialCostesGasolina,
                        contentDescription = "Gráfico de gasto mensual de gasolina: ${historialCostesGasolina.size} meses registrados",
                        xLabelFormatter = { index ->
                            costesMensualesGasolina
                                .getOrNull(index)
                                ?.month
                                .orEmpty()
                        },
                        yLabelFormatter = { value ->
                            "${value.toSpanishDecimal()} €"
                        }
                    )
                }
            }
            }
        }

        if (showElectric) {
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
                    "Coste total",
                    "${costeTotalElectrico.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Carga más cara",
                    "${cargaMasCara.toSpanishDecimal()} €"
                )

                StatisticRow(
                    "Carga más barata",
                    cargaMasBarataDePago?.let {
                        "${it.toSpanishDecimal()} €"
                    } ?: "No hay cargas de pago"
                )

                StatisticRow(
                    "Cargas",
                    numeroCargas.toString()
                )

                StatisticRow(
                    "Cargas gratuitas",
                    cargasGratuitas.toString()
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Gasto mensual",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (historialCostesElectricos.isEmpty()) {
                    Text(
                        text = "Registra consumos para ver la evolución mensual de tus gastos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    BarChart(
                        points = historialCostesElectricos,
                        contentDescription = "Gráfico de gasto mensual de electricidad: ${historialCostesElectricos.size} meses registrados",
                        xLabelFormatter = { index ->
                            costesMensualesElectricos
                                .getOrNull(index)
                                ?.month
                                .orEmpty()
                        },
                        yLabelFormatter = { value ->
                            "${value.toSpanishDecimal()} €"
                        }
                    )
                }
            }
            }
        }
    }
}
