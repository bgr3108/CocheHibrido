package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleInfo
import com.bgr3108.kilonom.data.isVehicleSelectionCompatible
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toKilometersOrNull

/** Shared catalog-backed editor used for first setup, adding and editing a vehicle snapshot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleForm(
    title: String,
    initialVehicle: Vehicle?,
    availableVehicles: List<VehicleInfo>,
    selectedCategory: VehicleCategory,
    onCategoryChanged: (VehicleCategory) -> Unit,
    catalogEditable: Boolean,
    minimumEntryKm: Double? = null,
    currentKm: Double? = null,
    isSaving: Boolean,
    errorMessage: String?,
    onSubmit: (Vehicle) -> Unit
) {
    val initialId = initialVehicle?.id
    val categoryName = rememberSaveable(initialId) {
        mutableStateOf((initialVehicle?.category ?: selectedCategory).name)
    }
    var brand by rememberSaveable(initialId) { mutableStateOf(initialVehicle?.brand.orEmpty()) }
    var model by rememberSaveable(initialId) { mutableStateOf(initialVehicle?.model.orEmpty()) }
    var year by rememberSaveable(initialId) { mutableStateOf(initialVehicle?.year?.toString().orEmpty()) }
    var initialKmText by rememberSaveable(initialId) {
        mutableStateOf(initialVehicle?.initialKm?.toKilometersDisplay()?.removeSuffix(" km").orEmpty())
    }
    var brandsExpanded by rememberSaveable { mutableStateOf(false) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }
    var yearsExpanded by rememberSaveable { mutableStateOf(false) }

    val category = VehicleCategory.entries.firstOrNull { it.name == categoryName.value } ?: VehicleCategory.COCHE

    LaunchedEffect(category) {
        onCategoryChanged(category)
    }

    val brands = availableVehicles.map { it.brand }.distinct().sorted()
    val models = availableVehicles.filter { it.brand == brand }.map { it.model }.distinct().sorted()
    val years = availableVehicles.filter { it.brand == brand && it.model == model }
        .map { it.year.toString() }.distinct().sortedDescending()
    val catalogVehicle = availableVehicles.firstOrNull {
        it.brand == brand && it.model == model && it.year.toString() == year
    }
    val selectedVehicle = catalogVehicle ?: initialVehicle
        ?.toVehicleInfoOrNull()
        ?.takeIf { !catalogEditable }
    val initialKm = initialKmText.toKilometersOrNull()
    val kmError = when {
        initialKmText.isBlank() -> null
        initialKm == null -> "Introduce un kilometraje válido"
        minimumEntryKm != null && initialKm > minimumEntryKm ->
            "El kilometraje inicial no puede superar el primer consumo registrado"
        else -> null
    }
    val isValid = isVehicleSelectionCompatible(selectedVehicle, category) &&
        initialKm != null && initialKm >= 0.0 && kmError == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text("Tipo de vehículo", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleCategory.entries.forEach { item ->
                FilterChip(
                    selected = category == item,
                    enabled = catalogEditable && !isSaving,
                    onClick = {
                        if (category != item) {
                            categoryName.value = item.name
                            brand = ""
                            model = ""
                            year = ""
                        }
                    },
                    label = { Text(if (item == VehicleCategory.COCHE) "Coche" else "Moto") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = category == item,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        if (catalogEditable) {
            CatalogDropdown("Marca", brand, brands, brandsExpanded, { brandsExpanded = it }) { value ->
                brand = value
                model = ""
                year = ""
            }
            CatalogDropdown("Modelo", model, models, modelsExpanded, { modelsExpanded = it }) { value ->
                model = value
                year = ""
            }
            CatalogDropdown("Año", year, years, yearsExpanded, { yearsExpanded = it }) { year = it }
        } else {
            Text("Marca: $brand", style = MaterialTheme.typography.bodyLarge)
            Text("Modelo: $model", style = MaterialTheme.typography.bodyLarge)
            if (year.isNotBlank()) Text("Año: $year", style = MaterialTheme.typography.bodyLarge)
            Text(
                "No se puede cambiar la propulsión de un vehículo con consumos registrados.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = initialKmText,
            onValueChange = { value -> if (value.all(Char::isDigit)) initialKmText = value },
            label = { Text("Kilometraje inicial") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = kmError != null,
            supportingText = kmError?.let { message -> { Text(message) } },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth()
        )
        if (currentKm != null) {
            Text(
                "Kilometraje actual: ${currentKm.toKilometersDisplay()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            enabled = isValid && !isSaving,
            onClick = {
                val selected = selectedVehicle ?: return@Button
                val validKm = initialKm ?: return@Button
                onSubmit(
                    Vehicle(
                        id = initialVehicle?.id,
                        brand = selected.brand,
                        model = selected.model,
                        year = selected.year,
                        category = category,
                        type = selected.type,
                        batteryCapacity = selected.batteryCapacity,
                        fuelTankCapacity = selected.fuelTankCapacity,
                        initialKm = validKm
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.padding(2.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Guardar")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CatalogDropdown(
    label: String,
    value: String,
    values: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            values.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelected(item); onExpandedChange(false) })
            }
        }
    }
}

private fun Vehicle.toVehicleInfoOrNull(): VehicleInfo? =
    type?.let {
        VehicleInfo(brand, model, year ?: return null, category, it, batteryCapacity, fuelTankCapacity)
    }
