package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.domain.EnergyPeriodSummary
import com.bgr3108.kilonom.domain.PeriodComparison
import com.bgr3108.kilonom.domain.PeriodComparisonStatus
import com.bgr3108.kilonom.domain.PeriodMetricComparison
import com.bgr3108.kilonom.domain.PeriodSummary
import com.bgr3108.kilonom.domain.StatisticsPeriod
import com.bgr3108.kilonom.domain.StatisticsPeriodMode
import com.bgr3108.kilonom.domain.currentPeriod
import com.bgr3108.kilonom.ui.components.StatisticRow
import com.bgr3108.kilonom.ui.components.charts.BarChart
import com.bgr3108.kilonom.ui.components.charts.ChartPoint
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.viewmodel.PeriodSummaryViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

@Composable
fun PeriodSummaryContent(
    viewModel: PeriodSummaryViewModel,
    modifier: Modifier = Modifier,
    topPadding: Dp = 16.dp
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.period.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCurrentPeriodOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = topPadding,
                bottom = 16.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PeriodModeSelector(
            selectedMode = selectedPeriod.mode,
            onModeSelected = viewModel::selectMode
        )

        if (selectedPeriod != StatisticsPeriod.All) {
            PeriodNavigator(
                period = selectedPeriod,
                onPrevious = viewModel::showPreviousPeriod,
                onNext = viewModel::showNextPeriod
            )
        }

        if (!uiState.summary.hasEconomicData) {
            EmptyPeriodState()
        } else {
            SummaryContent(
                summary = uiState.summary,
                comparison = uiState.comparison,
                vehicleType = uiState.vehicleType
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PeriodModeSelector(
    selectedMode: StatisticsPeriodMode,
    onModeSelected: (StatisticsPeriodMode) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        labelColor = MaterialTheme.colorScheme.onSurface
    )
    val modes = listOf(
        StatisticsPeriodMode.MONTH to "Mes",
        StatisticsPeriodMode.YEAR to "Año",
        StatisticsPeriodMode.ALL to "Todo"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (mode, label) ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(label) },
                colors = chipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedMode == mode,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    period: StatisticsPeriod,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val canMoveForward = period != currentPeriod(period.mode)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Periodo anterior"
            )
        }
        Text(
            text = period.label(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onNext, enabled = canMoveForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Periodo siguiente"
            )
        }
    }
}

@Composable
private fun EmptyPeriodState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No hay consumos registrados en este periodo.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SummaryContent(
    summary: PeriodSummary,
    comparison: PeriodComparison?,
    vehicleType: VehicleType?
) {
    SummaryCard {
        Text("Gasto total", style = MaterialTheme.typography.labelLarge)
        Text(
            text = "${summary.totalCost.toSpanishDecimal()} €",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        val showFuel = vehicleType.supportsFuelEntries
        val showElectric = vehicleType.supportsElectricEntries
        if (showFuel || showElectric) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (showFuel) {
            EnergySummary(
                title = "Combustible",
                quantityUnit = "L",
                summary = summary.fuel,
                icon = Icons.Default.LocalGasStation
            )
        }
        if (showFuel && showElectric) {
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (showElectric) {
            EnergySummary(
                title = "Electricidad",
                quantityUnit = "kWh",
                summary = summary.electric,
                icon = Icons.Default.Bolt
            )
        }
    }

    SummaryCard {
        val distanceTitle = if (summary.period == StatisticsPeriod.All) {
            "Distancia total"
        } else {
            "Distancia entre registros"
        }
        StatisticRow(distanceTitle, summary.distanceKilometers?.toKilometersDisplay()?.plus(" km") ?: "—")
        StatisticRow(
            "Coste por km",
            summary.costPerKilometer?.let { "${it.toSpanishDecimal()} €/km" } ?: "—"
        )
    }

    comparison?.let { ComparisonCard(it) }

    if (summary.period.mode != StatisticsPeriodMode.MONTH && summary.monthlyExpenses.size >= 2) {
        MonthlyExpenseChart(summary)
    }
}

@Composable
private fun EnergySummary(
    title: String,
    quantityUnit: String,
    summary: EnergyPeriodSummary,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(modifier = Modifier.height(8.dp))
    StatisticRow("Gasto", "${summary.totalCost.toSpanishDecimal()} €")
    StatisticRow("Cantidad", "${summary.quantity.toSpanishDecimal()} $quantityUnit")
    StatisticRow(
        "Precio medio",
        summary.averagePrice?.let { "${it.toSpanishDecimal()} €/$quantityUnit" } ?: "—"
    )
}

@Composable
private fun ComparisonCard(comparison: PeriodComparison) {
    SummaryCard {
        Text("Comparación con el periodo anterior", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        ComparisonRow("Gasto total", comparison.totalCost, "€")
        comparison.distance?.let { ComparisonRow("Distancia", it, "km") }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    comparison: PeriodMetricComparison,
    unit: String
) {
    val value = when (comparison.status) {
        PeriodComparisonStatus.NO_PREVIOUS_DATA -> "Sin datos del periodo anterior"
        PeriodComparisonStatus.NO_COMPARABLE_BASE -> "Sin base comparable"
        PeriodComparisonStatus.VALUE -> {
            val difference = requireNotNull(comparison.difference)
            val percentage = requireNotNull(comparison.percentage)
            "${difference.toSignedSpanishDecimal()} $unit · ${percentage.toSignedSpanishDecimal()} %"
        }
    }
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(
        text = value,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun MonthlyExpenseChart(summary: PeriodSummary) {
    SummaryCard {
        Text("Gasto mensual", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        val labelInterval = ceil(summary.monthlyExpenses.size / 6.0).toInt().coerceAtLeast(1)
        BarChart(
            points = summary.monthlyExpenses.mapIndexed { index, point ->
                ChartPoint(index.toDouble(), point.totalCost.toFloat())
            },
            contentDescription = "Gráfico de gasto mensual: ${summary.monthlyExpenses.size} meses registrados",
            xLabelFormatter = { index ->
                summary.monthlyExpenses.getOrNull(index)?.let { point ->
                    if (index % labelInterval == 0 || index == summary.monthlyExpenses.lastIndex) {
                        "%02d/%d".format(point.month + 1, point.year)
                    } else {
                        ""
                    }
                }.orEmpty()
            },
            yLabelFormatter = { value -> "${value.toSpanishDecimal()} €" }
        )
    }
}

@Composable
private fun SummaryCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) CardBlueDark else CardBlueLight
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

private fun StatisticsPeriod.label(): String = when (this) {
    is StatisticsPeriod.Month -> {
        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(
            Calendar.getInstance().apply {
                clear()
                set(year, month, 1)
            }.time
        )
        monthName.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
        } + " $year"
    }

    is StatisticsPeriod.Year -> year.toString()
    StatisticsPeriod.All -> "Todo"
}

private fun Double.toSignedSpanishDecimal(): String =
    "${if (this > 0.0) "+" else ""}${toSpanishDecimal()}"
