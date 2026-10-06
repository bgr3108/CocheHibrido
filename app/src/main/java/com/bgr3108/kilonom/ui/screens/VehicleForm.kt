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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleInfo
import com.bgr3108.kilonom.data.VehicleIdentificationField
import com.bgr3108.kilonom.data.VehicleSelectionCatalog
import com.bgr3108.kilonom.data.VehicleSelectionVariant
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.VehicleVersionAssistant
import com.bgr3108.kilonom.data.VehicleVersionAssistantResolution
import com.bgr3108.kilonom.data.assistantLabel
import com.bgr3108.kilonom.data.isVehicleSelectionCompatible
import com.bgr3108.kilonom.data.versionAssistantFor
import com.bgr3108.kilonom.util.ExternalLinks
import com.bgr3108.kilonom.util.openExternalUrl
import com.bgr3108.kilonom.util.openVehicleRequestEmail
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toKilometersOrNull

/** Shared catalog-backed editor used for first setup, adding and editing a vehicle snapshot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleForm(
    title: String,
    initialVehicle: Vehicle?,
    selectionCatalog: VehicleSelectionCatalog,
    selectedCategory: VehicleCategory,
    onCategoryChanged: (VehicleCategory) -> Unit,
    catalogEditable: Boolean,
    minimumRecordedKm: Double? = null,
    currentKm: Double? = null,
    isSaving: Boolean,
    errorMessage: String?,
    onSubmit: (Vehicle) -> Unit
) {
    val context = LocalContext.current
    val initialId = initialVehicle?.id
    val categoryName = rememberSaveable(initialId) {
        mutableStateOf((initialVehicle?.category ?: selectedCategory).name)
    }
    var brandId by rememberSaveable(initialId) { mutableStateOf("") }
    var modelId by rememberSaveable(initialId) { mutableStateOf("") }
    var year by rememberSaveable(initialId) { mutableStateOf(initialVehicle?.year?.toString().orEmpty()) }
    var variantId by rememberSaveable(initialId) { mutableStateOf("") }
    var initialKmText by rememberSaveable(initialId) {
        mutableStateOf(initialVehicle?.initialKm?.toKilometersDisplay()?.removeSuffix(" km").orEmpty())
    }
    var brandsExpanded by rememberSaveable { mutableStateOf(false) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }
    var yearsExpanded by rememberSaveable { mutableStateOf(false) }
    var variantsExpanded by rememberSaveable { mutableStateOf(false) }
    var showVersionAssistant by rememberSaveable(initialId) { mutableStateOf(false) }
    var assistantEnergyName by rememberSaveable(initialId) { mutableStateOf<String?>(null) }
    var assistantAnswers by remember { mutableStateOf<Map<VehicleIdentificationField, Set<String>>>(emptyMap()) }
    var skippedAssistantHints by remember { mutableStateOf<Set<VehicleIdentificationField>>(emptySet()) }

    val category = VehicleCategory.entries.firstOrNull { it.name == categoryName.value } ?: VehicleCategory.COCHE

    LaunchedEffect(category) {
        onCategoryChanged(category)
    }

    val initialSelection = selectionCatalog.initialSelectionFor(initialVehicle)
    LaunchedEffect(selectionCatalog, category, initialId, initialSelection) {
        if (catalogEditable && selectionCatalog.category == category && brandId.isBlank()) {
            initialSelection?.let { selection ->
                brandId = selection.brandId
                modelId = selection.modelId
                year = selection.year.toString()
                variantId = selection.variantId
            }
        }
    }

    val brands = selectionCatalog.brands()
    val models = if (brandId.isBlank()) emptyList() else selectionCatalog.modelsFor(brandId)
    val years = if (modelId.isBlank()) emptyList() else selectionCatalog.yearsFor(brandId, modelId)
    val variants = year.toIntOrNull()?.let { selectedYear ->
        selectionCatalog.variantsFor(brandId, modelId, selectedYear)
    }.orEmpty()
    LaunchedEffect(selectionCatalog, brandId, modelId, year, variants) {
        variantId = when {
            variants.size == 1 -> variants.single().id
            variants.any { it.id == variantId } -> variantId
            else -> ""
        }
    }
    val selectedVariant = variants.firstOrNull { it.id == variantId }
    val assistant = year.toIntOrNull()?.let { selectedYear ->
        selectionCatalog.versionAssistantFor(brandId, modelId, selectedYear)
    }
    val selectedAssistantEnergy = assistantEnergyName?.let { energyName ->
        VehicleType.entries.firstOrNull { it.name == energyName }
    }
    val selectedAssistantResolution = assistant?.let { helper ->
        selectedAssistantEnergy?.let { helper.resolve(it, assistantAnswers, skippedAssistantHints) }
    }
    val selectedBrandName = brands.firstOrNull { it.id == brandId }?.displayName
    val selectedModelName = models.firstOrNull { it.id == modelId }?.displayName

    LaunchedEffect(assistant) {
        if (assistant == null) {
            showVersionAssistant = false
            assistantEnergyName = null
        }
    }
    val selectedVehicle = selectedVariant?.vehicle ?: initialVehicle
        ?.toVehicleInfoOrNull()
        ?.takeIf { !catalogEditable }
    val initialKm = initialKmText.toKilometersOrNull()
    val kmError = when {
        initialKmText.isBlank() -> null
        initialKm == null -> "Introduce un kilometraje válido"
        minimumRecordedKm != null && initialKm > minimumRecordedKm ->
            "El kilometraje inicial no puede superar el primer kilometraje registrado"
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
            .padding(24.dp)
            .padding(bottom = 24.dp),
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
                            brandId = ""
                            modelId = ""
                            year = ""
                            variantId = ""
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
            CatalogDropdown(
                label = "Marca",
                value = brands.firstOrNull { it.id == brandId }?.displayName.orEmpty(),
                values = brands.map { CatalogDropdownOption(it.id, it.displayName) },
                expanded = brandsExpanded,
                onExpandedChange = { brandsExpanded = it }
            ) { value ->
                brandId = value
                modelId = ""
                year = ""
                variantId = ""
            }
            CatalogDropdown(
                label = "Modelo",
                value = models.firstOrNull { it.id == modelId }?.displayName.orEmpty(),
                values = models.map { CatalogDropdownOption(it.id, it.displayName) },
                expanded = modelsExpanded,
                onExpandedChange = { modelsExpanded = it }
            ) { value ->
                modelId = value
                year = ""
                variantId = ""
            }
            CatalogDropdown(
                label = "Año",
                value = year,
                values = years.map { CatalogDropdownOption(it.toString(), it.toString()) },
                expanded = yearsExpanded,
                onExpandedChange = { yearsExpanded = it }
            ) { value ->
                year = value
                variantId = ""
            }
            if (variants.size > 1) {
                CatalogDropdown(
                    label = "Variante",
                    value = variants.firstOrNull { it.id == variantId }?.displayName.orEmpty(),
                    values = variants.map { CatalogDropdownOption(it.id, it.displayName) },
                    expanded = variantsExpanded,
                    onExpandedChange = { variantsExpanded = it }
                ) { variantId = it }
                TextButton(
                    onClick = {
                        assistantEnergyName = null
                        assistantAnswers = emptyMap()
                        skippedAssistantHints = emptySet()
                        showVersionAssistant = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¿No sabes cuál es tu versión?")
                }
            } else if (
                selectedVariant?.vehicle?.type == VehicleType.HIBRIDO_ENCHUFABLE &&
                selectedVariant.automaticDisplayName != selectedVariant.displayName
            ) {
                Text(
                    "Versión seleccionada: ${selectedVariant.automaticDisplayName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (brandId.isNotBlank() && modelId.isNotBlank() && year.isNotBlank() && variants.isEmpty()) {
                Text(
                    "No tenemos una versión disponible para este año.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = {
                        context.openVehicleRequestEmail(
                            brand = selectedBrandName,
                            model = selectedModelName,
                            year = year,
                            variant = null
                        )
                    }
                ) {
                    Text("Solicitar que la añadamos")
                }
            }
        } else {
            Text("Marca: ${initialVehicle?.brand.orEmpty()}", style = MaterialTheme.typography.bodyLarge)
            Text("Modelo: ${initialVehicle?.model.orEmpty()}", style = MaterialTheme.typography.bodyLarge)
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
        VehicleCatalogHelpCard(
            onSendEmail = {
                context.openVehicleRequestEmail(
                    brand = selectedBrandName,
                    model = selectedModelName,
                    year = year,
                    variant = selectedVariant?.automaticDisplayName
                )
            },
            onOpenInstagram = { context.openExternalUrl(ExternalLinks.INSTAGRAM_PROFILE_URL) }
        )
    }

    if (showVersionAssistant && assistant != null && selectedBrandName != null && selectedModelName != null) {
        VehicleVersionAssistantSheet(
            brand = selectedBrandName,
            model = selectedModelName,
            year = year,
            assistant = assistant,
            selectedEnergy = selectedAssistantEnergy,
            resolution = selectedAssistantResolution,
            onEnergySelected = {
                assistantEnergyName = it.name
                assistantAnswers = emptyMap()
                skippedAssistantHints = emptySet()
            },
            onIdentificationSelected = { field, values ->
                assistantAnswers = assistantAnswers + (field to values)
            },
            onIdentificationNotFound = { field ->
                skippedAssistantHints = skippedAssistantHints + field
            },
            onUseVariant = { variant ->
                variantId = variant.id
                showVersionAssistant = false
                assistantEnergyName = null
            },
            onRequestHelp = {
                context.openVehicleRequestEmail(
                    brand = selectedBrandName,
                    model = selectedModelName,
                    year = year,
                    variant = null,
                    fuel = selectedAssistantEnergy?.assistantLabel(),
                    note = "No sé qué versión corresponde."
                )
            },
            onDismiss = {
                showVersionAssistant = false
                assistantEnergyName = null
                assistantAnswers = emptyMap()
                skippedAssistantHints = emptySet()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleVersionAssistantSheet(
    brand: String,
    model: String,
    year: String,
    assistant: VehicleVersionAssistant,
    selectedEnergy: VehicleType?,
    resolution: VehicleVersionAssistantResolution?,
    onEnergySelected: (VehicleType) -> Unit,
    onIdentificationSelected: (VehicleIdentificationField, Set<String>) -> Unit,
    onIdentificationNotFound: (VehicleIdentificationField) -> Unit,
    onUseVariant: (VehicleSelectionVariant) -> Unit,
    onRequestHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Ayúdanos a identificar tu vehículo", style = MaterialTheme.typography.titleLarge)
            Text(
                "$brand $model · $year",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text("¿Qué tipo de vehículo tienes?", style = MaterialTheme.typography.titleMedium)
            assistant.energyOptions.forEach { option ->
                FilterChip(
                    selected = selectedEnergy == option.type,
                    onClick = { onEnergySelected(option.type) },
                    label = { Text(option.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (resolution) {
                is VehicleVersionAssistantResolution.Match -> VehicleVersionMatch(
                    variant = resolution.variant,
                    onUseVariant = { onUseVariant(resolution.variant) }
                )
                is VehicleVersionAssistantResolution.AskIdentification -> {
                    val hint = resolution.hint
                    Text(hint.instruction, style = MaterialTheme.typography.titleSmall)
                    Text(hint.question, style = MaterialTheme.typography.titleMedium)
                    Text(hint.helperText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    hint.options.forEach { option ->
                        TextButton(
                            onClick = { onIdentificationSelected(hint.field, option.values) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(option.displayName) }
                    }
                    TextButton(onClick = { onIdentificationNotFound(hint.field) }, modifier = Modifier.fillMaxWidth()) {
                        Text("No lo encuentro")
                    }
                }
                VehicleVersionAssistantResolution.Unresolved -> {
                    Text(
                        "No podemos distinguir estas versiones con los datos disponibles.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Elegir manualmente")
                        }
                        TextButton(onClick = onRequestHelp, modifier = Modifier.weight(1f)) {
                            Text("Necesito ayuda")
                        }
                    }
                }
                null -> Unit
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Puedes consultar la documentación del vehículo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "P.1 indica la cilindrada, P.2 la potencia en kW, P.3 el combustible y D.2/D.3 el tipo, variante o denominación comercial.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Seguir eligiendo manualmente")
            }
        }
    }
}

@Composable
private fun VehicleVersionMatch(
    variant: VehicleSelectionVariant,
    onUseVariant: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Hemos encontrado una coincidencia", style = MaterialTheme.typography.titleSmall)
            Text(variant.vehicle.brand, style = MaterialTheme.typography.bodyLarge)
            Text("${variant.vehicle.model} · ${variant.vehicle.year}")
            Text(variant.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            variant.vehicle.fuelTankCapacity.takeIf { it > 0.0 }?.let { capacity ->
                Text("Depósito: ${capacity.toCleanNumber()} L", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onUseVariant, modifier = Modifier.fillMaxWidth()) {
                Text("Usar esta versión")
            }
        }
    }
}

private fun Double.toCleanNumber(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString().replace('.', ',')

@Composable
private fun VehicleCatalogHelpCard(
    onSendEmail: () -> Unit,
    onOpenInstagram: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "¿No encuentras tu coche o moto?",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Escríbenos indicando marca, modelo, año y motorización y lo añadiremos a Kilonom.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onSendEmail,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Enviar correo")
                }
                TextButton(
                    onClick = onOpenInstagram,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Instagram")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CatalogDropdown(
    label: String,
    value: String,
    values: List<CatalogDropdownOption>,
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
                DropdownMenuItem(text = { Text(item.label) }, onClick = { onSelected(item.id); onExpandedChange(false) })
            }
        }
    }
}

private data class CatalogDropdownOption(val id: String, val label: String)

private fun Vehicle.toVehicleInfoOrNull(): VehicleInfo? =
    type?.let {
        VehicleInfo(brand, model, year ?: return null, category, it, batteryCapacity, fuelTankCapacity)
    }
