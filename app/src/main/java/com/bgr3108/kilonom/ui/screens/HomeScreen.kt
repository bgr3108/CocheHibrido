package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.ui.components.HomeInfoCard
import com.bgr3108.kilonom.ui.components.VehicleCollectionIcon
import com.bgr3108.kilonom.domain.HomeTrendInsight
import com.bgr3108.kilonom.domain.HomeTrendMetric
import com.bgr3108.kilonom.domain.TrendStatus
import com.bgr3108.kilonom.domain.calculateUnitPrice
import com.bgr3108.kilonom.util.toDateTimeString
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.util.formatTrendPercentageCompact
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import java.util.Locale


@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenMyVehicles: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenMaintenance: () -> Unit
) {

    val precioGasolina by viewModel.precioGasolina.collectAsStateWithLifecycle()
    val precioElectrico by viewModel.precioElectrico.collectAsStateWithLifecycle()
    val costPerKm by viewModel.costPerKm.collectAsStateWithLifecycle()
    val ultimoGasolina by viewModel
        .ultimoGasolina
        .collectAsStateWithLifecycle()

    val ultimoElectrico by viewModel
        .ultimoElectrico
        .collectAsStateWithLifecycle()

    val vehicle by viewModel
        .vehicle
        .collectAsStateWithLifecycle()
    val configuredVehicleCategories by viewModel
        .configuredVehicleCategories
        .collectAsStateWithLifecycle()
    val homeTrendInsight by viewModel
        .homeTrendInsight
        .collectAsStateWithLifecycle()

    val showFuel = vehicle.type.supportsFuelEntries

    val showElectric = vehicle.type.supportsElectricEntries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp)
    ) {

        // Título
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = "Inicio",
                style = MaterialTheme.typography.headlineSmall
            )

            IconButton(onClick = onOpenMyVehicles) {
                VehicleCollectionIcon(configuredVehicleCategories)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            HomeInfoCard(
                modifier = Modifier.weight(1f),
                title = "Precio medio",
                icon = Icons.Default.AccountBalanceWallet
            ) {

                if (showFuel) {

                    Text(
                        "Combustible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "${precioGasolina.toSpanishDecimal()} €/L",
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                if (showElectric) {

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Electricidad",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "${precioElectrico.toSpanishDecimal()} €/kWh",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            HomeInfoCard(
                modifier = Modifier.weight(1f),
                title = "Coste por km",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                value = "${costPerKm.toSpanishDecimal()} €/km"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showFuel && showElectric) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                HomeInfoCard(
                    modifier = Modifier.weight(1f),
                    title = "Última carga",
                    icon = Icons.Default.Bolt
                ) {

                    ultimoElectrico?.let { entry ->

                        Text(entry.fecha.toDateTimeString())

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("${entry.cantidad.toSpanishDecimal()} kWh")

                        Text("${entry.precio.toSpanishDecimal()} €")

                        calculateUnitPrice(entry)?.let { precioUnitario ->
                            Text(
                                "${precioUnitario.toSpanishDecimal()} €/kWh"
                            )
                        }

                    } ?: Text(
                        "Sin registros",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HomeInfoCard(
                    modifier = Modifier.weight(1f),
                    title = "Último repostaje",
                    icon = Icons.Default.LocalGasStation
                ) {

                    ultimoGasolina?.let { entry ->

                        Text(entry.fecha.toDateTimeString())

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("${entry.cantidad.toSpanishDecimal()} L")

                        Text("${entry.precio.toSpanishDecimal()} €")

                        calculateUnitPrice(entry)?.let { precioUnitario ->
                            Text(
                                "${precioUnitario.toSpanishDecimal()} €/L"
                            )
                        }

                    } ?: Text(
                        "Sin registros",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        } else if (showFuel) {

            HomeInfoCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Último repostaje",
                icon = Icons.Default.LocalGasStation
            ) {

                ultimoGasolina?.let { entry ->

                    Text(entry.fecha.toDateTimeString())

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("${entry.cantidad.toSpanishDecimal()} L")

                    Text("${entry.precio.toSpanishDecimal()} €")

                    calculateUnitPrice(entry)?.let { precioUnitario ->
                        Text(
                            "${precioUnitario.toSpanishDecimal()} €/L"
                        )
                    }

                } ?: Text(
                    "Sin registros",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            HomeInfoCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Última carga",
                icon = Icons.Default.Bolt
            ) {

                ultimoElectrico?.let { entry ->

                    Text(entry.fecha.toDateTimeString())

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("${entry.cantidad.toSpanishDecimal()} kWh")

                    Text("${entry.precio.toSpanishDecimal()} €")

                    calculateUnitPrice(entry)?.let { precioUnitario ->
                        Text(
                            "${precioUnitario.toSpanishDecimal()} €/kWh"
                        )
                    }

                } ?: Text(
                    "Sin registros",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HomeInfoCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Mantenimiento",
            icon = Icons.Default.Build,
            minHeight = 0.dp,
            headerToContentSpacing = 4.dp,
            contentTopSpacing = 0.dp,
            onClick = onOpenMaintenance
        ) {
            Text(
                "Consulta próximos avisos e historial.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        HomeInfoCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Tendencia",
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            minHeight = 0.dp,
            onClick = onOpenTrends
        ) {
            Text(
                text = homeTrendInsight.title(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = homeTrendInsight.color()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = homeTrendInsight.toHomeTrendDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

}

private fun HomeTrendInsight.title(): String = when (metric) {
    HomeTrendMetric.INSUFFICIENT_DATA -> "Necesitamos más datos"
    HomeTrendMetric.STABLE -> "Todo estable"
    HomeTrendMetric.FUEL_CONSUMPTION ->
        if (status == TrendStatus.UP) "Consumo al alza" else "Consumo a la baja"

    HomeTrendMetric.ELECTRIC_CONSUMPTION ->
        if (status == TrendStatus.UP) "Consumo eléctrico al alza" else "Consumo eléctrico a la baja"

    HomeTrendMetric.PHEV_ENERGY_CHARGED ->
        if (status == TrendStatus.UP) "Energía cargada al alza" else "Energía cargada a la baja"

    HomeTrendMetric.COST_PER_HUNDRED_KM ->
        if (status == TrendStatus.UP) "Coste al alza" else "Coste a la baja"
}

internal fun HomeTrendInsight.toHomeTrendDescription(
    locale: Locale = Locale.getDefault()
): String = when (metric) {
    HomeTrendMetric.INSUFFICIENT_DATA ->
        "Registra algunos consumos más para detectar tendencias."

    HomeTrendMetric.STABLE ->
        "Los datos analizados se mantienen dentro de lo habitual."

    HomeTrendMetric.FUEL_CONSUMPTION ->
        "El consumo de combustible ha ${status.toVerb()} un ${percentage(locale)}."

    HomeTrendMetric.ELECTRIC_CONSUMPTION ->
        "Tu consumo eléctrico ha ${status.toVerb()} un ${percentage(locale)}."

    HomeTrendMetric.PHEV_ENERGY_CHARGED ->
        "La energía cargada por cada 100 km ha ${status.toVerb()} un ${percentage(locale)}."

    HomeTrendMetric.COST_PER_HUNDRED_KM ->
        "Tu coste por 100 km ha ${status.toVerb()} un ${percentage(locale)}."
}

private fun TrendStatus.toVerb(): String = if (this == TrendStatus.UP) "aumentado" else "bajado"

private fun HomeTrendInsight.percentage(locale: Locale): String = formatTrendPercentageCompact(
    percentageChange = requireNotNull(percentageChange),
    locale = locale
)

@Composable
private fun HomeTrendInsight.color() = when (status) {
    TrendStatus.UP -> MaterialTheme.colorScheme.error
    TrendStatus.DOWN -> MaterialTheme.colorScheme.primary
    TrendStatus.STABLE,
    TrendStatus.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurface
}
