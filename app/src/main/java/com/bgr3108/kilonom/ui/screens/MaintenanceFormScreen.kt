package com.bgr3108.kilonom.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition
import com.bgr3108.kilonom.domain.isDocumentMaintenance
import com.bgr3108.kilonom.viewmodel.MaintenanceItemDraft
import com.bgr3108.kilonom.viewmodel.MaintenanceRecordDraft
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import java.util.Calendar

enum class MaintenanceFormMode {
    CREATE_ITEM,
    REGISTER_RECORD,
    EDIT_ITEM,
    EDIT_RECORD
}

private enum class ReminderMode { NONE, KM, DATE, BOTH }

@Composable
fun MaintenanceFormScreen(
    innerPadding: PaddingValues,
    mode: MaintenanceFormMode,
    itemId: Long? = null,
    recordId: Long? = null,
    viewModel: MaintenanceViewModel,
    onClose: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.item.id == itemId }?.item
    val record = state.records.firstOrNull { it.record.id == recordId }?.record
    if ((mode == MaintenanceFormMode.REGISTER_RECORD || mode == MaintenanceFormMode.EDIT_ITEM) && item == null ||
        mode == MaintenanceFormMode.EDIT_RECORD && record == null
    ) {
        MissingMaintenanceScreen(innerPadding, onClose)
        return
    }

    val sourceItem = item ?: state.records.firstOrNull { it.record.id == recordId }?.item
    val editableType = mode == MaintenanceFormMode.CREATE_ITEM
    val editableReminder = mode != MaintenanceFormMode.EDIT_RECORD
    val editableRecord = mode != MaintenanceFormMode.EDIT_ITEM
    val defaults = remember(mode, itemId, recordId, sourceItem, record) {
        MaintenanceFormDefaults(
            type = sourceItem?.type,
            tyrePosition = sourceItem?.tyrePosition,
            customName = sourceItem?.customName.orEmpty(),
            nextDueKm = sourceItem?.nextDueKm?.toString().orEmpty(),
            nextDueDate = sourceItem?.nextDueDate,
            recordDate = record?.performedDate ?: if (mode == MaintenanceFormMode.REGISTER_RECORD) todayAtStartOfDayForForm() else null,
            recordKm = record?.odometerKm?.toString().orEmpty(),
            cost = record?.cost?.toString().orEmpty(),
            notes = record?.notes.orEmpty(),
            includeRecord = mode == MaintenanceFormMode.REGISTER_RECORD || mode == MaintenanceFormMode.EDIT_RECORD,
            reminderMode = reminderMode(sourceItem?.nextDueKm, sourceItem?.nextDueDate)
        )
    }
    val selectedType = rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.type?.name) }
    var tyrePosition by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.tyrePosition?.name) }
    var customName by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.customName) }
    var includeRecord by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.includeRecord) }
    var recordDate by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.recordDate) }
    var recordKm by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.recordKm) }
    var cost by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.cost) }
    var notes by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.notes) }
    var dueKm by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.nextDueKm) }
    var dueDate by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.nextDueDate) }
    val selectedReminder = rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.reminderMode.name) }
    val typeExpanded = remember { mutableStateOf(false) }
    val formError = remember { mutableStateOf<String?>(null) }

    val maintenanceType = selectedType.value?.let { selected -> MaintenanceType.entries.firstOrNull { it.name == selected } }
    val documentType = maintenanceType?.isDocumentMaintenance() == true
    val reminder = ReminderMode.entries.first { it.name == selectedReminder.value }
    val hasDueKm = reminder == ReminderMode.KM || reminder == ReminderMode.BOTH
    val hasDueDate = reminder == ReminderMode.DATE || reminder == ReminderMode.BOTH
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun chooseDate(current: Long?, onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = current ?: System.currentTimeMillis() }
        DatePickerDialog(context, { _, year, month, day ->
            onSelected(Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text(formTitle(mode), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
        }
        Text("Tipo", style = MaterialTheme.typography.titleMedium)
        if (editableType) {
            Box {
                OutlinedButton(
                    onClick = { typeExpanded.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = maintenanceOutlinedButtonColors(),
                    border = maintenanceOutlinedBorder()
                ) {
                    Text(maintenanceType?.displayName() ?: "Selecciona un tipo", modifier = Modifier.weight(1f))
                }
                DropdownMenu(
                    expanded = typeExpanded.value,
                    onDismissRequest = { typeExpanded.value = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.availableTypes.sortedBy { it.displayName() }.forEach { available ->
                        DropdownMenuItem(
                            text = { Text(available.displayName()) },
                            colors = maintenanceDropdownItemColors(),
                            onClick = {
                                selectedType.value = available.name
                                tyrePosition = null
                                typeExpanded.value = false
                            }
                        )
                    }
                }
            }
        } else {
            Text(maintenanceType?.displayName().orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        if (maintenanceType == MaintenanceType.TYRES) {
            Text("Posición", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TyrePosition.entries.forEach { position ->
                    FilterChip(
                        selected = tyrePosition == position.name,
                        onClick = { tyrePosition = position.name },
                        enabled = editableType && !state.isWorking,
                        label = { Text(position.displayName()) },
                        colors = maintenanceFilterChipColors(),
                        border = maintenanceFilterChipBorder(
                            enabled = editableType && !state.isWorking,
                            selected = tyrePosition == position.name
                        )
                    )
                }
            }
        }
        if (maintenanceType == MaintenanceType.OTHER) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                enabled = mode != MaintenanceFormMode.EDIT_RECORD && !state.isWorking,
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = maintenanceTextFieldColors()
            )
        }

        if (maintenanceType != null && editableRecord) {
            Text("Realizado", style = MaterialTheme.typography.titleMedium)
            if (mode == MaintenanceFormMode.CREATE_ITEM) {
                FilterChip(
                    selected = includeRecord,
                    onClick = { includeRecord = !includeRecord },
                    label = { Text(if (includeRecord) "Añadir mantenimiento realizado" else "Sin mantenimiento realizado") },
                    colors = maintenanceFilterChipColors(),
                    border = maintenanceFilterChipBorder(
                        enabled = !state.isWorking,
                        selected = includeRecord
                    )
                )
            }
            if (includeRecord) {
                DateField("Fecha", recordDate, enabled = !state.isWorking, onClick = { chooseDate(recordDate) { recordDate = it } })
                if (!documentType) {
                    OutlinedTextField(
                        value = recordKm,
                        onValueChange = { if (it.all(Char::isDigit)) recordKm = it },
                        label = { Text("Kilometraje (opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = maintenanceTextFieldColors()
                    )
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Coste (€) opcional") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = maintenanceTextFieldColors()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas opcionales") },
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = maintenanceTextFieldColors()
                )
            }
        }
        if (maintenanceType != null && editableReminder) {
            Text("Próximo", style = MaterialTheme.typography.titleMedium)
            ReminderMode.entries.forEach { option ->
                FilterChip(
                    selected = reminder == option,
                    onClick = { selectedReminder.value = option.name },
                    label = { Text(option.displayName()) },
                    colors = maintenanceFilterChipColors(),
                    border = maintenanceFilterChipBorder(
                        enabled = !state.isWorking,
                        selected = reminder == option
                    ),
                    modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                )
            }
            if (hasDueKm) {
                OutlinedTextField(
                    value = dueKm,
                    onValueChange = { if (it.all(Char::isDigit)) dueKm = it },
                    label = { Text("Próximo kilometraje") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    enabled = !state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = maintenanceTextFieldColors()
                )
            }
            if (hasDueDate) DateField("Próxima fecha", dueDate, enabled = !state.isWorking, onClick = { chooseDate(dueDate) { dueDate = it } })
        }
        formError.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val selectedMaintenanceType = maintenanceType ?: run {
                    formError.value = validateSelectedMaintenanceType(null)
                    return@Button
                }
                formError.value = validateMaintenanceForm(
                    type = selectedMaintenanceType,
                    tyrePosition = tyrePosition,
                    customName = customName,
                    includeRecord = includeRecord,
                    documentType = documentType,
                    recordDate = recordDate,
                    recordKm = recordKm,
                    cost = cost,
                    hasDueKm = hasDueKm,
                    dueKm = dueKm,
                    hasDueDate = hasDueDate,
                    dueDate = dueDate
                )
                if (formError.value != null) return@Button
                val draft = MaintenanceItemDraft(
                    type = selectedMaintenanceType,
                    tyrePosition = tyrePosition?.let(TyrePosition::valueOf),
                    customName = customName.trim().takeIf { it.isNotEmpty() },
                    nextDueKm = dueKm.toLongOrNull(),
                    nextDueDate = dueDate
                )
                val recordDraft = if (includeRecord) MaintenanceRecordDraft(
                    performedDate = recordDate,
                    odometerKm = if (documentType) null else optionalOdometerKm(recordKm),
                    cost = cost.toLocalizedDoubleOrNull(),
                    notes = notes
                ) else null
                when (mode) {
                    MaintenanceFormMode.CREATE_ITEM -> viewModel.createItem(draft, recordDraft, onClose)
                    MaintenanceFormMode.REGISTER_RECORD -> viewModel.registerRecord(item!!, draft, recordDraft!!, onClose)
                    MaintenanceFormMode.EDIT_ITEM -> viewModel.updateItem(item!!, draft, onClose)
                    MaintenanceFormMode.EDIT_RECORD -> viewModel.updateRecord(record!!, recordDraft!!, onClose)
                }
            },
            enabled = !state.isWorking,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isWorking) CircularProgressIndicator() else Text("Guardar")
        }
    }
}

private data class MaintenanceFormDefaults(
    val type: MaintenanceType?, val tyrePosition: TyrePosition?, val customName: String,
    val nextDueKm: String, val nextDueDate: Long?, val recordDate: Long?, val recordKm: String,
    val cost: String, val notes: String, val includeRecord: Boolean, val reminderMode: ReminderMode
)

@Composable
private fun DateField(label: String, value: Long?, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = maintenanceOutlinedButtonColors(),
        border = maintenanceOutlinedBorder(enabled)
    ) {
        Text(if (value == null) label else "$label: ${value.formatMaintenanceDate()}")
    }
}

internal fun validateMaintenanceForm(
    type: MaintenanceType, tyrePosition: String?, customName: String, includeRecord: Boolean,
    documentType: Boolean, recordDate: Long?, recordKm: String, cost: String,
    hasDueKm: Boolean, dueKm: String, hasDueDate: Boolean, dueDate: Long?
): String? {
    if (type == MaintenanceType.TYRES && tyrePosition == null) return "Selecciona la posición de los neumáticos"
    if (type == MaintenanceType.OTHER && customName.trim().isEmpty()) return "El nombre personalizado es obligatorio"
    val validCost = cost.toLocalizedDoubleOrNull()
    if (cost.isNotBlank() && (validCost == null || validCost < 0.0)) return "El coste no es válido"
    if (hasDueKm && dueKm.toLongOrNull() == null) return "Introduce el próximo kilometraje"
    if (hasDueDate && dueDate == null) return "Introduce la próxima fecha"
    if (includeRecord) {
        if (recordDate == null) return "Introduce la fecha del mantenimiento"
        val km = optionalOdometerKm(recordKm)
        if (!documentType && recordKm.isNotBlank() && km == null) return "El kilometraje no es válido"
        val due = dueKm.toLongOrNull()
        if (!documentType && hasDueKm && km != null && due != null && due < km) {
            return "El próximo kilometraje no puede ser anterior al mantenimiento realizado"
        }
    }
    return null
}

internal fun optionalOdometerKm(value: String): Long? = value.trim()
    .takeIf { it.isNotEmpty() }
    ?.toLongOrNull()
    ?.takeIf { it >= 0L }

internal fun validateSelectedMaintenanceType(type: MaintenanceType?): String? =
    if (type == null) "Selecciona un tipo de mantenimiento" else null

private fun String.toLocalizedDoubleOrNull(): Double? = trim()
    .takeIf { it.isNotEmpty() }
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() }

@Composable
private fun maintenanceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    errorBorderColor = MaterialTheme.colorScheme.error,
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun maintenanceOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

@Composable
private fun maintenanceOutlinedBorder(enabled: Boolean = true) = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.38f)
)

@Composable
private fun maintenanceFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = Color.Transparent,
    disabledLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

@Composable
private fun maintenanceFilterChipBorder(enabled: Boolean, selected: Boolean) =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
        disabledSelectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    )

@Composable
private fun maintenanceDropdownItemColors() = MenuDefaults.itemColors(
    textColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

private fun MaintenanceType.displayName(): String = when (this) {
    MaintenanceType.OIL_AND_FILTER -> "Aceite y filtro"
    MaintenanceType.BRAKES -> "Frenos"
    MaintenanceType.TYRES -> "Neumáticos"
    MaintenanceType.BATTERY_12V -> "Batería 12 V"
    MaintenanceType.GENERAL_SERVICE -> "Revisión general"
    MaintenanceType.CHAIN_AND_DRIVETRAIN -> "Cadena / transmisión"
    MaintenanceType.ITV -> "ITV"
    MaintenanceType.INSURANCE -> "Seguro"
    MaintenanceType.CIRCULATION_TAX -> "Impuesto de circulación"
    MaintenanceType.OTHER -> "Otro"
}

private fun TyrePosition.displayName(): String = when (this) {
    TyrePosition.ALL -> "Todos"
    TyrePosition.FRONT -> "Delanteros"
    TyrePosition.REAR -> "Traseros"
}

private fun ReminderMode.displayName(): String = when (this) {
    ReminderMode.NONE -> "Sin aviso"
    ReminderMode.KM -> "Por kilometraje"
    ReminderMode.DATE -> "Por fecha"
    ReminderMode.BOTH -> "Kilometraje y fecha"
}

private fun reminderMode(km: Long?, date: Long?): ReminderMode = when {
    km != null && date != null -> ReminderMode.BOTH
    km != null -> ReminderMode.KM
    date != null -> ReminderMode.DATE
    else -> ReminderMode.NONE
}

private fun formTitle(mode: MaintenanceFormMode): String = when (mode) {
    MaintenanceFormMode.CREATE_ITEM -> "Añadir mantenimiento"
    MaintenanceFormMode.REGISTER_RECORD -> "Registrar mantenimiento"
    MaintenanceFormMode.EDIT_ITEM -> "Editar seguimiento"
    MaintenanceFormMode.EDIT_RECORD -> "Editar registro"
}

private fun todayAtStartOfDayForForm(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
