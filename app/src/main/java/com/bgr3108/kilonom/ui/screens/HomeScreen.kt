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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.ui.components.HomeInfoCard
import com.bgr3108.kilonom.domain.calculateUnitPrice
import com.bgr3108.kilonom.util.toDateTimeString
import com.bgr3108.kilonom.util.toSpanishDecimal
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.ResetState


@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenPrivacy: () -> Unit
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
    val resetState by viewModel
        .resetState
        .collectAsStateWithLifecycle()

    val showFuel = vehicle.type.supportsFuelEntries

    val showElectric = vehicle.type.supportsElectricEntries

    val showVehicleDialog = remember {
        mutableStateOf(false)
    }

    val showResetDialog = remember {
        mutableStateOf(false)
    }

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

            IconButton(
                onClick = {
                    showVehicleDialog.value = true
                }
            ) {
                Icon(
                    imageVector = if (vehicle.category == VehicleCategory.MOTO) {
                        Icons.Default.TwoWheeler
                    } else {
                        Icons.Default.DirectionsCar
                    },
                    contentDescription = "Vehículo"
                )
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
                        "Gasolina",
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

    if (showVehicleDialog.value) {

        AlertDialog(
            onDismissRequest = {
                showVehicleDialog.value = false
            },

            title = {
                Text("Vehículo actual")
            },

            text = {

                Column {

                    Text(
                        text = "${vehicle.brand} ${vehicle.model}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val tipoTexto =
                        vehicle.type?.name
                            ?.lowercase()
                            ?.replace("_", " ")
                            ?.replace("hibrido", "híbrido")
                            ?.replace("enchufable", "enchufable")
                            ?.replace("electrico", "eléctrico")
                            ?.replace("diesel", "diésel")
                            ?.replaceFirstChar { it.titlecase() }
                            ?: "-"

                    Row {
                        Text(
                            text = "Año:",
                            modifier = Modifier.width(110.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = vehicle.year?.toString() ?: "-",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row {
                        Text(
                            text = "Tipo:",
                            modifier = Modifier.width(110.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = tipoTexto,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (vehicle.batteryCapacity > 0) {

                        Spacer(modifier = Modifier.height(4.dp))

                        Row {
                            Text(
                                text = "Batería:",
                                modifier = Modifier.width(110.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "${vehicle.batteryCapacity.toSpanishDecimal()} kWh",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (vehicle.fuelTankCapacity > 0) {

                        Spacer(modifier = Modifier.height(4.dp))

                        Row {
                            Text(
                                text = "Depósito:",
                                modifier = Modifier.width(110.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "${vehicle.fuelTankCapacity.toSpanishDecimal()} L",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row {
                        Text(
                            text = "Km iniciales:",
                            modifier = Modifier.width(110.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = vehicle.currentKm.toKilometersDisplay(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showVehicleDialog.value = false
                    }
                ) {
                    Text("Cerrar")
                }
            },

            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            showVehicleDialog.value = false
                            onOpenPrivacy()
                        }
                    ) {
                        Text("Privacidad")
                    }

                    TextButton(
                        onClick = {
                            showResetDialog.value = true
                        }
                    ) {
                        Text("Restablecer")
                    }
                }
            }
        )
    }

    if (showResetDialog.value) {

        val isResetting = resetState == ResetState.LOADING

        AlertDialog(
            onDismissRequest = {
                if (!isResetting) {
                    viewModel.dismissResetError()
                    showResetDialog.value = false
                }
            },

            title = {
                Text("Restablecer aplicación")
            },

            text = {
                Column {
                    Text(
                        "Se eliminarán:\n\n" +
                                "• El vehículo configurado\n" +
                                "• Todos los repostajes y cargas\n\n" +
                                "La aplicación volverá a la pantalla de configuración inicial.\n\n" +
                                "Esta acción no se puede deshacer."
                    )

                    if (resetState == ResetState.ERROR) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No se pudieron borrar todos los datos. Inténtalo de nuevo.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        viewModel.resetApplication()
                    },
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Restablecer")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissResetError()
                        showResetDialog.value = false
                    },
                    enabled = !isResetting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
