package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bgr3108.kilonom.ui.theme.CardBlueLight
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import androidx.compose.foundation.isSystemInDarkTheme
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.isVehicleSelectionCompatible
import com.bgr3108.kilonom.util.toFiniteDoubleOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    homeViewModel: HomeViewModel,
    onDone: () -> Unit
){

    var km by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable {
        mutableStateOf("")
    }
    var selectedModel by rememberSaveable {
        mutableStateOf("")
    }

    var expandedModels by remember {
        mutableStateOf(false)
    }
    var expandedBrands by remember {
        mutableStateOf(false)
    }
    var selectedYear by rememberSaveable {
        mutableStateOf("")
    }

    var expandedYears by remember {
        mutableStateOf(false)
    }
    val vehicles by homeViewModel.availableVehicles.collectAsState()
    val selectedCategoryName = rememberSaveable {
        mutableStateOf(homeViewModel.setupVehicleCategory.value.name)
    }
    val selectedCategory = VehicleCategory.entries.firstOrNull {
        it.name == selectedCategoryName.value
    } ?: VehicleCategory.COCHE
    val isSaving by homeViewModel.isSavingVehicle.collectAsState()
    val vehicleSaveFailed by homeViewModel.vehicleSaveFailed.collectAsState()
    val isDark = isSystemInDarkTheme()
    val categoryChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )

    LaunchedEffect(selectedCategory) {
        homeViewModel.selectSetupVehicleCategory(selectedCategory)
    }

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
    val initialMileage = km.toFiniteDoubleOrNull()
    val kmErrorMessage = when {
        km.isBlank() -> null
        initialMileage == null -> "Introduce un kilometraje válido"
        initialMileage < 0.0 -> "El kilometraje no puede ser negativo"
        else -> null
    }
    val isConfigurationValid =
        isVehicleSelectionCompatible(selectedVehicle, selectedCategory) &&
                initialMileage != null &&
                initialMileage >= 0.0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

        Text(
            text = "Configura tu vehículo",
            style = MaterialTheme.typography.headlineSmall
        )

            Text(
                text = "Tipo de vehículo",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            if (selectedCategory != category) {
                                selectedCategoryName.value = category.name
                                selectedBrand = ""
                                selectedModel = ""
                                selectedYear = ""
                            }
                        },
                        label = {
                            Text(
                                if (category == VehicleCategory.COCHE) "Coche" else "Moto"
                            )
                        },
                        colors = categoryChipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedCategory == category,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            if (selectedCategory == VehicleCategory.MOTO && vehicles.isEmpty()) {
                Text(
                    text = "Aún no hay motos disponibles en el catálogo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

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
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
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
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
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
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            )
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
                isError = kmErrorMessage != null,
                supportingText = kmErrorMessage?.let { message ->
                    { Text(message) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (km.isNotBlank() && selectedVehicle == null) {
                Text(
                    text = "Selecciona un tipo de vehículo",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (vehicleSaveFailed) {
                Text(
                    text = "No se pudo guardar la configuración. Inténtalo de nuevo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
                val vehicle = selectedVehicle
                    ?.takeIf { isVehicleSelectionCompatible(it, selectedCategory) }
                    ?: return@Button
                val validMileage = km.toFiniteDoubleOrNull()
                    ?.takeIf { it >= 0.0 }
                    ?: return@Button

                homeViewModel.saveInitialVehicle(
                    Vehicle(
                        brand = vehicle.brand,
                        model = vehicle.model,
                        year = vehicle.year,
                        category = selectedCategory,
                        type = vehicle.type,
                        batteryCapacity = vehicle.batteryCapacity,
                        fuelTankCapacity = vehicle.fuelTankCapacity,
                        currentKm = validMileage
                    ),
                    onSaved = onDone
                )
            },
            enabled = isConfigurationValid && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Guardar")
            }
        }
    }
    }
}
