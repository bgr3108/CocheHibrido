package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.util.toSpanishDecimal
import com.example.cochehibrido.util.toDateTimeString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.cochehibrido.data.VehicleType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.foundation.layout.width


@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    viewModel: HomeViewModel
) {
    // 🔵 TARJETAS (NO TOCAR)
    val precioGasolina by viewModel.precioGasolina.collectAsStateWithLifecycle()
    val totalLitrosGasolina by viewModel
        .totalLitrosGasolina
        .collectAsStateWithLifecycle()
    val totalKwhElectricos by viewModel
        .totalKwhElectricos
        .collectAsStateWithLifecycle()
    val costeElectricoKm by viewModel.costeElectricoKm.collectAsStateWithLifecycle(initialValue = 0.0)
    val isDark = isSystemInDarkTheme()
// 🟢 NUEVO (CÁLCULO REAL)
    val totalKm by viewModel.totalKm.collectAsStateWithLifecycle()
    val totalCost by viewModel.totalCost.collectAsStateWithLifecycle()
    val costPerKm by viewModel.costPerKm.collectAsStateWithLifecycle()
    val kmEsteMes by viewModel
        .kmEsteMes
        .collectAsStateWithLifecycle()

    val gastoEsteMes by viewModel
        .gastoEsteMes
        .collectAsStateWithLifecycle()
    val ultimoGasolina by viewModel
        .ultimoGasolina
        .collectAsStateWithLifecycle()

    val ultimoElectrico by viewModel
        .ultimoElectrico
        .collectAsStateWithLifecycle()

    val vehicle by viewModel
        .vehicle
        .collectAsStateWithLifecycle()

    val showFuel =
        vehicle.type != VehicleType.ELECTRICO

    val showElectric =
        vehicle.type == VehicleType.ELECTRICO ||
                vehicle.type == VehicleType.HIBRIDO_ENCHUFABLE
    val gastoGasolinaTotal by viewModel
        .gastoGasolinaTotal
        .collectAsStateWithLifecycle(initialValue = 0.0)

    val gastoElectricoTotal by viewModel
        .gastoElectricoTotal
        .collectAsStateWithLifecycle(initialValue = 0.0)

    var showVehicleDialog by remember {
        mutableStateOf(false)
    }

    var showResetDialog by remember {
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
                    showVehicleDialog = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = "Vehículo"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),

            colors = CardDefaults.cardColors(
                containerColor = if (isDark) CardBlueDark else CardBlueLight
            )
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "Este mes",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Km recorridos: ${
                        kmEsteMes.toSpanishDecimal()
                    } km"
                )

                Text(
                    "Gasto: ${
                        gastoEsteMes.toSpanishDecimal()
                    } €"
                )
            }
        }

        // 🔥 NUEVA TARJETA: cálculo real
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) CardBlueDark else CardBlueLight
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ){
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Text(
                    text = "Cálculo total",
                    style = MaterialTheme.typography.titleMedium
                )

                Text("Km totales: %.1f km".format(totalKm))
                Text("Coste total: %.2f €".format(totalCost))
                Text("€/km real: %.3f €".format(costPerKm))

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            if (showElectric) {
            // 🔋 ELÉCTRICO
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        end = if (showFuel) 8.dp else 0.dp
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text("🔋 Eléctrico")

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "${totalKwhElectricos.toSpanishDecimal()} kWh cargados",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        "${gastoElectricoTotal.toSpanishDecimal()} € gastados",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "%.3f €/km".format(costeElectricoKm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${totalKwhElectricos.toSpanishDecimal()} kWh totales",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            }
            if (showFuel) {
            // ⛽ GASOLINA
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (showElectric) 8.dp else 0.dp
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) CardBlueDark else CardBlueLight
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Text("⛽ Gasolina")

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "%.2f €/L".format(precioGasolina),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "${totalLitrosGasolina.toSpanishDecimal()} L repostados",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        "${gastoGasolinaTotal.toSpanishDecimal()} € gastados",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                if (showElectric) {
                // 🔋 Última carga
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            end = if (showFuel) 8.dp else 0.dp
                        ),

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

                            if (entry.cantidad > 0) {
                                Text(
                                    "${(entry.precio / entry.cantidad).toSpanishDecimal()} €/kWh"
                                )
                            }
                        }
                    }
                }
                }
                if (showFuel) {
                // ⛽ Último repostaje
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = if (showElectric) 8.dp else 0.dp
                        ),

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

                            if (entry.cantidad > 0) {
                                Text(
                                    "${(entry.precio / entry.cantidad).toSpanishDecimal()} €/L"
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    if (showVehicleDialog) {

        AlertDialog(
            onDismissRequest = {
                showVehicleDialog = false
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
                            text = vehicle.currentKm.toSpanishDecimal(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showVehicleDialog = false
                    }
                ) {
                    Text("Cerrar")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showResetDialog = true
                    }
                ) {
                    Text("Restablecer")
                }
            }
        )
    }

    if (showResetDialog) {

        AlertDialog(
            onDismissRequest = {
                showResetDialog = false
            },

            title = {
                Text("Restablecer aplicación")
            },

            text = {
                Text(
                    "Se eliminarán:\n\n" +
                            "• El vehículo configurado\n" +
                            "• Todos los repostajes y cargas\n\n" +
                            "La aplicación volverá a la pantalla de configuración inicial.\n\n" +
                            "Esta acción no se puede deshacer."
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {

                        // Aquí irá el borrado

                        showResetDialog = false
                        showVehicleDialog = false
                    }
                ) {
                    Text("Restablecer")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
