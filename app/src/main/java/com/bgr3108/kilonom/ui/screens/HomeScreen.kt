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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.ui.components.HomeInfoCard
import com.bgr3108.kilonom.ui.components.VehicleCollectionIcon
import com.bgr3108.kilonom.domain.calculateUnitPrice
import com.bgr3108.kilonom.util.toDateTimeString
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.viewmodel.HomeViewModel


@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenMyVehicles: () -> Unit
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
    }

}
