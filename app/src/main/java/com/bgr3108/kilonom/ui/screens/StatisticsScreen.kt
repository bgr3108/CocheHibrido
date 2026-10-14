package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.data.isPlugInHybrid
import com.bgr3108.kilonom.R
import com.bgr3108.kilonom.ui.components.DashboardCard
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.PeriodSummaryViewModel

@Composable
fun StatisticsScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenConsumption: () -> Unit,
    onOpenPrice: () -> Unit,
    onOpenCost: () -> Unit,
    onOpenTotal: () -> Unit,
    periodSummaryViewModel: PeriodSummaryViewModel,
    initialTab: StatisticsTab = StatisticsTab.GLOBAL
){

    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    StatisticsTabContent(
        selectedTab = selectedTab,
        global = {
            GlobalStatisticsContent(
                innerPadding, viewModel, { selectedTab = it },
                onOpenConsumption, onOpenPrice, onOpenCost, onOpenTotal
            )
        },
        periods = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                StatisticsHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    bottomPadding = 6.dp
                )
                PeriodSummaryContent(
                    viewModel = periodSummaryViewModel,
                    modifier = Modifier.weight(1f),
                    topPadding = 6.dp
                )
            }
        },
        trends = {
            val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()
            val trendSummary by viewModel.trendSummary.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                StatisticsHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    bottomPadding = 6.dp
                )
                TrendsContent(
                    summary = trendSummary,
                    vehicleType = vehicle.type,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    )
}

/** Only the selected branch is composed, so hidden tabs own no collectors. */
@Composable
internal fun StatisticsTabContent(
    selectedTab: StatisticsTab,
    global: @Composable () -> Unit,
    periods: @Composable () -> Unit,
    trends: @Composable () -> Unit
) {
    when (selectedTab) {
        StatisticsTab.GLOBAL -> global()
        StatisticsTab.PERIODS -> periods()
        StatisticsTab.TRENDS -> trends()
    }
}

@Composable
private fun GlobalStatisticsContent(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onTabSelected: (StatisticsTab) -> Unit,
    onOpenConsumption: () -> Unit,
    onOpenPrice: () -> Unit,
    onOpenCost: () -> Unit,
    onOpenTotal: () -> Unit
) {
    val vehicle by viewModel
        .vehicle
        .collectAsStateWithLifecycle()

    val showFuel = vehicle.type.supportsFuelEntries

    val showElectric = vehicle.type.supportsElectricEntries

    val isPlugInHybrid = vehicle.type.isPlugInHybrid


    val entries by viewModel
        .entries
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val consumoGasolina = if (showFuel) {
        viewModel.consumoGasolina.collectAsStateWithLifecycle().value
    } else 0.0

    val mejorConsumoGasolina = if (showFuel) {
        viewModel.mejorConsumoGasolina.collectAsStateWithLifecycle().value
    } else 0.0

    val peorConsumoGasolina = if (showFuel) {
        viewModel.peorConsumoGasolina.collectAsStateWithLifecycle().value
    } else 0.0

    val numeroTramosGasolina = if (showFuel) {
        viewModel.numeroTramosGasolina.collectAsStateWithLifecycle().value
    } else 0

    val currentEstimatedFuelConsumption = if (showFuel) {
        viewModel.currentEstimatedFuelConsumption.collectAsStateWithLifecycle().value
    } else null

    val consumoElectrico = if (showElectric) {
        viewModel.consumoElectrico.collectAsStateWithLifecycle().value
    } else 0.0

    val precioGasolina = if (showFuel) {
        viewModel.precioGasolina.collectAsStateWithLifecycle().value
    } else 0.0

    val precioElectrico = if (showElectric) {
        viewModel.precioElectrico.collectAsStateWithLifecycle().value
    } else 0.0

    val litrosTotales = if (showFuel) {
        viewModel.litrosTotales.collectAsStateWithLifecycle(0.0).value
    } else 0.0

    val gastoGasolinaTotal = if (showFuel) {
        viewModel.gastoGasolinaTotal.collectAsStateWithLifecycle(0.0).value
    } else 0.0

    val kwhTotales = if (showElectric) {
        viewModel.kwhTotales.collectAsStateWithLifecycle(0.0).value
    } else 0.0

    val gastoElectricoTotal = if (showElectric) {
        viewModel.gastoElectricoTotal.collectAsStateWithLifecycle(0.0).value
    } else 0.0

    val totalKm by viewModel
        .totalKm
        .collectAsStateWithLifecycle()

    val costPerHundredKm by viewModel
        .costPerHundredKm
        .collectAsStateWithLifecycle()


    if (entries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            StatisticsHeader(
                selectedTab = StatisticsTab.GLOBAL,
                onTabSelected = onTabSelected
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Aún no hay estadísticas",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Registra tus primeros consumos para empezar a ver estadísticas y costes.",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        StatisticsHeader(
            selectedTab = StatisticsTab.GLOBAL,
            onTabSelected = onTabSelected
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        DashboardCard(
            title = if (isPlugInHybrid) "Consumo por 100 km" else "Consumos",
            icon = Icons.Default.LocalGasStation,
            showDetailArrow = true,
            onClick = {
                onOpenConsumption()
            }
        ) {

            if (showFuel) {

                Text(
                    "Combustible",
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Consumo medio",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    "${consumoGasolina.toSpanishDecimal()} L/100 km",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Mejor consumo",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "${mejorConsumoGasolina.toSpanishDecimal()} L/100 km",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "Peor consumo",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "${peorConsumoGasolina.toSpanishDecimal()} L/100 km",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "Tramos calculados",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    numeroTramosGasolina.toString(),
                    style = MaterialTheme.typography.titleLarge
                )

                currentEstimatedFuelConsumption?.let { estimate ->
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Consumo estimado actual",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "≈ ${estimate.consumption.toSpanishDecimal()} L/100 km",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.current_estimated_consumption_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (showFuel && showElectric) {

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (showElectric) {

                Text(
                    if (isPlugInHybrid) "Electricidad cargada" else "Electricidad",
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
            showDetailArrow = true,
            onClick = onOpenPrice
        ) {

            if (showFuel) {

                Text(
                    "Combustible",
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
            title = "Costes",
            icon = Icons.Default.Payments,
            showDetailArrow = true,
            onClick = onOpenCost,
        ) {

            if (showFuel) {

                Text(
                    "Combustible"
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
                "Coste por 100 km"
            )

            Text(
                costPerHundredKm?.let { "${it.toSpanishDecimal()} €/100 km" } ?: "—",
                style = MaterialTheme.typography.titleLarge
            )

        }
        DashboardCard(
            title = "Totales",
            icon = Icons.Default.Route,
            showDetailArrow = true,
            onClick = onOpenTotal
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
                "${totalKm.toKilometersDisplay()} km",
                style = MaterialTheme.typography.titleLarge
            )

        }

        Spacer(modifier = Modifier.height(24.dp))
    }
    }
}
}

enum class StatisticsTab {
    GLOBAL,
    PERIODS,
    TRENDS
}

@Composable
private fun StatisticsHeader(
    selectedTab: StatisticsTab,
    onTabSelected: (StatisticsTab) -> Unit,
    bottomPadding: Dp = 16.dp
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        labelColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomPadding
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                StatisticsTab.GLOBAL to "Global",
                StatisticsTab.PERIODS to "Periodos",
                StatisticsTab.TRENDS to "Tendencias"
            ).forEach { (tab, label) ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    label = { Text(label) },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedTab == tab,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
