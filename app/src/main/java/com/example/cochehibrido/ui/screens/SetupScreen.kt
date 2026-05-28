package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.data.BaselineRepository
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.cochehibrido.viewmodel.HomeViewModel
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.data.VehicleRepository
import com.example.cochehibrido.data.Vehicle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    baselineRepository: BaselineRepository,
    vehicleRepository: VehicleRepository,
    homeViewModel: HomeViewModel,
    onDone: () -> Unit
){

    var km by remember { mutableStateOf("") }
    var selectedBrand by remember {
        mutableStateOf("")
    }
    var selectedModel by remember {
        mutableStateOf("")
    }

    var expandedModels by remember {
        mutableStateOf(false)
    }
    var expandedBrands by remember {
        mutableStateOf(false)
    }
    var selectedYear by remember {
        mutableStateOf("")
    }

    var expandedYears by remember {
        mutableStateOf(false)
    }
    val vehicles by homeViewModel.availableVehicles.collectAsState()
    val isDark = isSystemInDarkTheme()

    val brands = vehicles
        .map { it.brand }
        .distinct()
        .sorted()
    val models = vehicles
        .filter { it.brand == selectedBrand }
        .map { it.model }
        .distinct()
        .sorted()
    val years = vehicles
        .filter {
            it.brand == selectedBrand &&
                    it.model == selectedModel
        }
        .map { it.year.toString() }
        .distinct()
        .sortedDescending()
    val selectedVehicle = vehicles.find {

        it.brand == selectedBrand &&
                it.model == selectedModel &&
                it.year.toString() == selectedYear
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Configura tu coche",
            style = MaterialTheme.typography.headlineSmall
        )
            ExposedDropdownMenuBox(
                expanded = expandedBrands,
                onExpandedChange = {
                    expandedBrands = !expandedBrands
                }
            ) {

                OutlinedTextField(
                    value = selectedBrand,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Marca")
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedBrands,
                    onDismissRequest = {
                        expandedBrands = false
                    }
                ) {

                    brands.forEach { brand ->

                        DropdownMenuItem(
                            text = {
                                Text(brand)
                            },
                            onClick = {

                                selectedBrand = brand

                                selectedModel = ""
                                selectedYear = ""

                                expandedBrands = false
                            }
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(
                expanded = expandedModels,
                onExpandedChange = {
                    expandedModels = !expandedModels
                }
            ) {

                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Modelo")
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedModels,
                    onDismissRequest = {
                        expandedModels = false
                    }
                ) {

                    models.forEach { model ->

                        DropdownMenuItem(
                            text = {
                                Text(model)
                            },
                            onClick = {

                                selectedModel = model

                                selectedYear = ""

                                expandedModels = false
                            }
                        )
                    }
                }
            }
            if (years.isNotEmpty()) {

                ExposedDropdownMenuBox(
                    expanded = expandedYears,
                    onExpandedChange = {
                        expandedYears = !expandedYears
                    }
                ) {

                    OutlinedTextField(
                        value = selectedYear,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text("Año")
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedYears,
                        onDismissRequest = {
                            expandedYears = false
                        }
                    ) {

                        years.forEach { year ->

                            DropdownMenuItem(
                                text = {
                                    Text(year)
                                },
                                onClick = {

                                    selectedYear = year
                                    expandedYears = false
                                }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = km,
                onValueChange = {
                    km = it.replace("\n", "")
                },
                label = { Text("Km actuales") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )
            selectedVehicle?.let { vehicle ->

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) CardBlueDark else CardBlueLight
                    ),
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Text(
                            text = "${vehicle.brand} ${vehicle.model}",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text("Año: ${vehicle.year}")

                        Text(
                            "Tipo: ${
                                vehicle.type.name
                                    .lowercase()
                                    .replace("_", " ")
                                    .replaceFirstChar {
                                        it.uppercase()
                                    }
                                    .replace("Hibrido", "Híbrido")
                                    .replace("enchufable", "Enchufable")
                                    .replace("electrico", "Eléctrico")
                                    .replace("diesel", "Diésel")
                            }"
                        )

                        if (vehicle.batteryCapacity > 0) {
                            Text(
                                "Batería: ${vehicle.batteryCapacity} kWh"
                            )
                        }

                        if (vehicle.fuelTankCapacity > 0) {
                            Text(
                                "Depósito: ${vehicle.fuelTankCapacity} L"
                            )
                        }
                    }
                }
            }

        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            onClick = {

                baselineRepository.saveBaseline(
                    km.toDoubleOrNull() ?: 0.0,
                    0.0,
                    0.0
                )

                selectedVehicle?.let {

                    vehicleRepository.saveVehicle(
                        Vehicle(
                            brand = it.brand,
                            model = it.model,
                            year = it.year,
                            type = it.type,
                            batteryCapacity = it.batteryCapacity,
                            fuelTankCapacity = it.fuelTankCapacity
                        )
                    )
                }

                onDone()
            },
            enabled = km.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }
    }
    }
}