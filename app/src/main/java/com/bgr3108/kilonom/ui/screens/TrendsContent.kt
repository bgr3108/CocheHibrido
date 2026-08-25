package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.isPlugInHybrid
import com.bgr3108.kilonom.domain.MetricTrend
import com.bgr3108.kilonom.domain.OverallTrendStatus
import com.bgr3108.kilonom.domain.TrendMagnitude
import com.bgr3108.kilonom.domain.TrendStatus
import com.bgr3108.kilonom.domain.VehicleTrendSummary
import com.bgr3108.kilonom.ui.components.DashboardCard
import com.bgr3108.kilonom.util.formatTrendPercentageDetailed
import com.bgr3108.kilonom.util.toSpanishDecimal

@Composable
fun TrendsContent(
    summary: VehicleTrendSummary,
    vehicleType: VehicleType?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardCard(title = "Estado general") {
            Text(
                text = summary.overallStatus.toMessage(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = summary.overallStatus.color()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary.overallStatus.toDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (summary.fuelConsumption != null || summary.electricConsumption != null) {
            DashboardCard(title = "Consumo") {
                summary.fuelConsumption?.let { trend ->
                    TrendMetric(
                        title = "Combustible",
                        unit = "L/100 km",
                        trend = trend,
                        increaseIsNegative = true
                    )
                }
                if (summary.fuelConsumption != null && summary.electricConsumption != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                summary.electricConsumption?.let { trend ->
                    TrendMetric(
                        title = if (vehicleType.isPlugInHybrid) {
                            "Electricidad cargada"
                        } else {
                            "Electricidad"
                        },
                        unit = "kWh/100 km",
                        trend = trend,
                        increaseIsNegative = true
                    )
                }
            }
        }

        DashboardCard(title = "Coste") {
            TrendMetric(
                title = "Coste por 100 km",
                unit = "€/100 km",
                trend = summary.costPerHundredKm,
                increaseIsNegative = true
            )
        }

        if (summary.fuelPrice != null || summary.electricPrice != null) {
            DashboardCard(title = "Precio de energía") {
                summary.fuelPrice?.let { trend ->
                    TrendMetric(
                        title = "Combustible",
                        unit = "€/L",
                        trend = trend,
                        increaseIsNegative = false
                    )
                }
                if (summary.fuelPrice != null && summary.electricPrice != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }
                summary.electricPrice?.let { trend ->
                    TrendMetric(
                        title = "Electricidad",
                        unit = "€/kWh",
                        trend = trend,
                        increaseIsNegative = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TrendMetric(
    title: String,
    unit: String,
    trend: MetricTrend,
    increaseIsNegative: Boolean
) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    if (trend.status == TrendStatus.INSUFFICIENT_DATA) {
        Text(
            text = "Aún no hay suficientes datos para detectar una tendencia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val baseline = requireNotNull(trend.baselineValue)
    val recent = requireNotNull(trend.recentValue)
    Text(
        text = "${baseline.toSpanishDecimal()} → ${recent.toSpanishDecimal()} $unit",
        style = MaterialTheme.typography.titleLarge
    )
    Text(
        text = trend.toChangeText(),
        style = MaterialTheme.typography.bodyMedium,
        color = trend.color(increaseIsNegative)
    )
}

private fun MetricTrend.toChangeText(): String = when (status) {
    TrendStatus.STABLE -> "Se mantiene estable"
    TrendStatus.UP -> "↑ ${formatTrendPercentageDetailed(requireNotNull(percentageChange))}${magnitude.suffix()}"
    TrendStatus.DOWN -> "↓ ${formatTrendPercentageDetailed(requireNotNull(percentageChange))}${magnitude.suffix()}"
    TrendStatus.INSUFFICIENT_DATA -> ""
}

private fun TrendMagnitude.suffix(): String = when (this) {
    TrendMagnitude.SLIGHT -> " · cambio ligero"
    TrendMagnitude.RELEVANT -> " · cambio relevante"
    TrendMagnitude.NONE -> ""
}

@Composable
private fun MetricTrend.color(increaseIsNegative: Boolean): Color = when (status) {
    TrendStatus.UP -> if (increaseIsNegative) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TrendStatus.DOWN -> if (increaseIsNegative) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    TrendStatus.STABLE,
    TrendStatus.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun OverallTrendStatus.toMessage(): String = when (this) {
    OverallTrendStatus.INSUFFICIENT_DATA -> "Necesitamos más datos"
    OverallTrendStatus.STABLE -> "Todo estable"
    OverallTrendStatus.CONSUMPTION_UP -> "El consumo está aumentando"
    OverallTrendStatus.CONSUMPTION_DOWN -> "El consumo ha bajado"
    OverallTrendStatus.COST_UP -> "El coste por 100 km ha aumentado"
    OverallTrendStatus.COST_DOWN -> "Tus costes han bajado"
    OverallTrendStatus.CHANGES -> "Hay cambios en tus tendencias"
    OverallTrendStatus.MIXED_CHANGES -> "Hay cambios en tus tendencias"
}

private fun OverallTrendStatus.toDescription(): String = when (this) {
    OverallTrendStatus.INSUFFICIENT_DATA ->
        "Sigue registrando consumos para detectar tendencias fiables."

    OverallTrendStatus.STABLE ->
        "Tus registros recientes se mantienen dentro de la variación habitual."

    OverallTrendStatus.CONSUMPTION_UP,
    OverallTrendStatus.CONSUMPTION_DOWN,
    OverallTrendStatus.COST_UP,
    OverallTrendStatus.COST_DOWN ->
        "Comparamos los tres registros más recientes con tu historial anterior."

    OverallTrendStatus.CHANGES,
    OverallTrendStatus.MIXED_CHANGES ->
        "Hay variaciones entre consumo y coste; consulta el detalle para interpretarlas."
}

@Composable
private fun OverallTrendStatus.color(): Color = when (this) {
    OverallTrendStatus.CONSUMPTION_UP,
    OverallTrendStatus.COST_UP -> MaterialTheme.colorScheme.error

    OverallTrendStatus.CONSUMPTION_DOWN,
    OverallTrendStatus.COST_DOWN -> MaterialTheme.colorScheme.primary

    OverallTrendStatus.INSUFFICIENT_DATA,
    OverallTrendStatus.STABLE,
    OverallTrendStatus.CHANGES,
    OverallTrendStatus.MIXED_CHANGES -> MaterialTheme.colorScheme.onSurface
}
