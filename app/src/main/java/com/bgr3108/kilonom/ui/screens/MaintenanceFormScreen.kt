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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import com.bgr3108.kilonom.data.TyrePosition
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_DAYS
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_KM
import com.bgr3108.kilonom.domain.MaintenanceNextDue
import com.bgr3108.kilonom.domain.MaintenanceNextDueUpdate
import com.bgr3108.kilonom.domain.calculateNextMaintenanceDue
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

internal enum class ReminderMode { NONE, KM, DATE, BOTH }

internal enum class MaintenanceIntervalMode { NONE, KM, TIME, BOTH }

internal enum class MaintenanceNextDueMode { AUTOMATIC, MANUAL, NONE }

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
    val editableItemConfiguration = mode == MaintenanceFormMode.CREATE_ITEM || mode == MaintenanceFormMode.EDIT_ITEM
    val defaults = remember(mode, itemId, recordId, sourceItem, record) {
        MaintenanceFormDefaults(
            type = sourceItem?.type,
            tyrePosition = sourceItem?.tyrePosition,
            customName = sourceItem?.customName.orEmpty(),
            nextDueKm = sourceItem?.nextDueKm?.toString().orEmpty(),
            nextDueDate = sourceItem?.nextDueDate,
            intervalKm = sourceItem?.intervalKm?.toString().orEmpty(),
            intervalTimeValue = sourceItem?.intervalTimeValue?.toString().orEmpty(),
            intervalTimeUnit = sourceItem?.intervalTimeUnit,
            reminderLeadKm = sourceItem?.reminderLeadKm?.toString() ?: DEFAULT_REMINDER_LEAD_KM.toString(),
            reminderLeadDays = sourceItem?.reminderLeadDays?.toString() ?: DEFAULT_REMINDER_LEAD_DAYS.toString(),
            recordDate = record?.performedDate ?: if (mode == MaintenanceFormMode.REGISTER_RECORD) todayAtStartOfDayForForm() else null,
            recordKm = record?.odometerKm?.toString().orEmpty(),
            cost = record?.cost?.toString().orEmpty(),
            notes = record?.notes.orEmpty(),
            includeRecord = mode == MaintenanceFormMode.REGISTER_RECORD || mode == MaintenanceFormMode.EDIT_RECORD,
            reminderMode = reminderMode(sourceItem?.nextDueKm, sourceItem?.nextDueDate),
            intervalMode = intervalMode(sourceItem?.intervalKm, sourceItem?.intervalTimeValue, sourceItem?.intervalTimeUnit),
            nextDueMode = initialNextDueMode(mode, sourceItem)
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
    var intervalKm by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.intervalKm) }
    var intervalTimeValue by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.intervalTimeValue) }
    var intervalTimeUnit by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.intervalTimeUnit?.name) }
    var reminderLeadKm by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.reminderLeadKm) }
    var reminderLeadDays by rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.reminderLeadDays) }
    val selectedReminder = rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.reminderMode.name) }
    val selectedInterval = rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.intervalMode.name) }
    val selectedNextDue = rememberSaveable(mode, itemId, recordId) { mutableStateOf(defaults.nextDueMode.name) }
    val typeExpanded = remember { mutableStateOf(false) }
    val formError = remember { mutableStateOf<String?>(null) }

    val maintenanceType = selectedType.value?.let { selected -> MaintenanceType.entries.firstOrNull { it.name == selected } }
    val documentType = maintenanceType?.isDocumentMaintenance() == true
    val reminder = ReminderMode.entries.first { it.name == selectedReminder.value }
    val recurrence = MaintenanceIntervalMode.entries.first { it.name == selectedInterval.value }
    val nextDueMode = MaintenanceNextDueMode.entries.first { it.name == selectedNextDue.value }
    val hasDueKm = reminder == ReminderMode.KM || reminder == ReminderMode.BOTH
    val hasDueDate = reminder == ReminderMode.DATE || reminder == ReminderMode.BOTH
    val hasIntervalKm = recurrence == MaintenanceIntervalMode.KM || recurrence == MaintenanceIntervalMode.BOTH
    val hasIntervalTime = recurrence == MaintenanceIntervalMode.TIME || recurrence == MaintenanceIntervalMode.BOTH
    val intervalUnit = intervalTimeUnit?.let { value -> MaintenanceTimeUnit.entries.firstOrNull { it.name == value } }
    val configuredInterval = hasIntervalKm || hasIntervalTime
    val intervalValuesAreComplete =
        (!hasIntervalKm || intervalKm.toLongOrNull()?.let { it > 0L } == true) &&
            (!hasIntervalTime || (intervalTimeValue.toIntOrNull()?.let { it > 0 } == true && intervalUnit != null))
    val calculatedAutomaticDue = if (includeRecord && configuredInterval && intervalValuesAreComplete) {
        calculateNextMaintenanceDue(
            performedDate = recordDate,
            performedKm = optionalOdometerKm(recordKm),
            intervalKm = intervalKm.toLongOrNull(),
            intervalTimeValue = intervalTimeValue.toIntOrNull(),
            intervalTimeUnit = intervalUnit
        )
    } else {
        null
    }
    val automaticAvailable = editableReminder && calculatedAutomaticDue?.let {
        it.nextDueKm != null || it.nextDueDate != null
    } == true
    val effectiveNextDueMode = if (nextDueMode == MaintenanceNextDueMode.AUTOMATIC && !automaticAvailable) {
        MaintenanceNextDueMode.MANUAL
    } else {
        nextDueMode
    }
    // Only manual values participate in form validation or persistence. Automatic and
    // no-reminder modes intentionally ignore any values left in the manual controls.
    val manualHasDueKm = effectiveNextDueMode == MaintenanceNextDueMode.MANUAL && hasDueKm
    val manualHasDueDate = effectiveNextDueMode == MaintenanceNextDueMode.MANUAL && hasDueDate
    val automaticDue = calculatedAutomaticDue.takeIf { effectiveNextDueMode == MaintenanceNextDueMode.AUTOMATIC }
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

        if (maintenanceType != null && editableItemConfiguration) {
            IntervalConfiguration(
                mode = recurrence,
                intervalKm = intervalKm,
                intervalTimeValue = intervalTimeValue,
                intervalTimeUnit = intervalUnit,
                enabled = !state.isWorking,
                onModeSelected = { selected ->
                    selectedInterval.value = selected.name
                    if (includeRecord && selected != MaintenanceIntervalMode.NONE) {
                        selectedNextDue.value = MaintenanceNextDueMode.AUTOMATIC.name
                    } else if (selected == MaintenanceIntervalMode.NONE && selectedNextDue.value == MaintenanceNextDueMode.AUTOMATIC.name) {
                        selectedNextDue.value = MaintenanceNextDueMode.MANUAL.name
                    }
                    if (selected != MaintenanceIntervalMode.KM && selected != MaintenanceIntervalMode.BOTH) {
                        intervalKm = ""
                    }
                    if (selected != MaintenanceIntervalMode.TIME && selected != MaintenanceIntervalMode.BOTH) {
                        intervalTimeValue = ""
                        intervalTimeUnit = null
                    }
                },
                onIntervalKmChanged = { intervalKm = it },
                onIntervalTimeValueChanged = { intervalTimeValue = it },
                onIntervalTimeUnitChanged = { intervalTimeUnit = it?.name }
            )
        }

        if (maintenanceType != null && editableRecord) {
            Text("Realizado", style = MaterialTheme.typography.titleMedium)
            if (mode == MaintenanceFormMode.CREATE_ITEM) {
                FilterChip(
                    selected = includeRecord,
                    onClick = {
                        includeRecord = !includeRecord
                        if (!includeRecord && selectedNextDue.value == MaintenanceNextDueMode.AUTOMATIC.name) {
                            selectedNextDue.value = MaintenanceNextDueMode.MANUAL.name
                        } else if (includeRecord && recurrence != MaintenanceIntervalMode.NONE) {
                            selectedNextDue.value = MaintenanceNextDueMode.AUTOMATIC.name
                        }
                    },
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
            Text("Próximo mantenimiento", style = MaterialTheme.typography.titleMedium)
            NextDueModeSelector(
                automaticAvailable = automaticAvailable,
                selected = effectiveNextDueMode,
                enabled = !state.isWorking,
                onSelected = { selectedNextDue.value = it.name }
            )
            when (effectiveNextDueMode) {
                MaintenanceNextDueMode.AUTOMATIC -> {
                    AutomaticDuePreview(automaticDue)
                }

                MaintenanceNextDueMode.MANUAL -> {
                    ManualDueConfiguration(
                        reminder = reminder,
                        dueKm = dueKm,
                        dueDate = dueDate,
                        enabled = !state.isWorking,
                        focusManager = focusManager,
                        onReminderSelected = { selected ->
                            selectedReminder.value = selected.name
                            if (selected != ReminderMode.KM && selected != ReminderMode.BOTH) dueKm = ""
                            if (selected != ReminderMode.DATE && selected != ReminderMode.BOTH) dueDate = null
                        },
                        onDueKmChanged = { dueKm = it },
                        onDueDateClick = { chooseDate(dueDate) { dueDate = it } }
                    )
                }

                MaintenanceNextDueMode.NONE -> {
                    Text("No se configurará ningún próximo aviso.", color = maintenanceSecondaryColor())
                }
            }
            if (editableItemConfiguration) {
                val showLeadKm = hasIntervalKm || (effectiveNextDueMode == MaintenanceNextDueMode.MANUAL && hasDueKm)
                val showLeadDays = hasIntervalTime || (effectiveNextDueMode == MaintenanceNextDueMode.MANUAL && hasDueDate)
                if (showLeadKm || showLeadDays) {
                    ReminderLeadConfiguration(
                        showKm = showLeadKm,
                        showDays = showLeadDays,
                        leadKm = reminderLeadKm,
                        leadDays = reminderLeadDays,
                        enabled = !state.isWorking,
                        focusManager = focusManager,
                        onLeadKmChanged = { reminderLeadKm = it },
                        onLeadDaysChanged = { reminderLeadDays = it }
                    )
                }
            }
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
                    hasDueKm = manualHasDueKm,
                    dueKm = dueKm,
                    hasDueDate = manualHasDueDate,
                    dueDate = dueDate
                )
                if (formError.value == null) {
                    formError.value = validateIntervalConfiguration(
                        mode = recurrence,
                        intervalKm = intervalKm,
                        intervalTimeValue = intervalTimeValue,
                        intervalTimeUnit = intervalUnit
                    ) ?: validateReminderLeads(
                        leadKm = reminderLeadKm,
                        leadDays = reminderLeadDays,
                        validateKm = editableItemConfiguration && (hasIntervalKm || manualHasDueKm),
                        validateDays = editableItemConfiguration && (hasIntervalTime || manualHasDueDate)
                    )
                }
                if (formError.value != null) return@Button
                val reminderDueValues = when (effectiveNextDueMode) {
                    MaintenanceNextDueMode.MANUAL -> selectedReminderDueValues(manualHasDueKm, dueKm, manualHasDueDate, dueDate)
                    else -> null to null
                }
                val draft = MaintenanceItemDraft(
                    type = selectedMaintenanceType,
                    tyrePosition = tyrePosition?.let(TyrePosition::valueOf),
                    customName = customName.trim().takeIf { it.isNotEmpty() },
                    nextDueKm = reminderDueValues.first,
                    nextDueDate = reminderDueValues.second,
                    intervalKm = intervalKm.toLongOrNull().takeIf { hasIntervalKm },
                    intervalTimeValue = intervalTimeValue.toIntOrNull().takeIf { hasIntervalTime },
                    intervalTimeUnit = intervalUnit.takeIf { hasIntervalTime },
                    reminderLeadKm = reminderLeadKm.toLongOrNull() ?: DEFAULT_REMINDER_LEAD_KM,
                    reminderLeadDays = reminderLeadDays.toLongOrNull() ?: DEFAULT_REMINDER_LEAD_DAYS
                )
                val recordDraft = if (includeRecord) MaintenanceRecordDraft(
                    performedDate = recordDate,
                    odometerKm = if (documentType) null else optionalOdometerKm(recordKm),
                    cost = cost.toLocalizedDoubleOrNull(),
                    notes = notes
                ) else null
                when (mode) {
                    MaintenanceFormMode.CREATE_ITEM -> viewModel.createItem(
                        draft,
                        recordDraft,
                        effectiveNextDueMode.toRepositoryUpdate(reminderDueValues),
                        onClose
                    )
                    MaintenanceFormMode.REGISTER_RECORD -> viewModel.registerRecord(
                        item!!,
                        recordDraft!!,
                        effectiveNextDueMode.toRepositoryUpdate(reminderDueValues),
                        onClose
                    )
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

@Composable
private fun IntervalConfiguration(
    mode: MaintenanceIntervalMode,
    intervalKm: String,
    intervalTimeValue: String,
    intervalTimeUnit: MaintenanceTimeUnit?,
    enabled: Boolean,
    onModeSelected: (MaintenanceIntervalMode) -> Unit,
    onIntervalKmChanged: (String) -> Unit,
    onIntervalTimeValueChanged: (String) -> Unit,
    onIntervalTimeUnitChanged: (MaintenanceTimeUnit?) -> Unit
) {
    Text("Intervalo de mantenimiento", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MaintenanceIntervalMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeSelected(option) },
                enabled = enabled,
                label = { Text(option.displayName()) },
                colors = maintenanceFilterChipColors(),
                border = maintenanceFilterChipBorder(enabled, mode == option)
            )
        }
    }
    val showKm = mode == MaintenanceIntervalMode.KM || mode == MaintenanceIntervalMode.BOTH
    val showTime = mode == MaintenanceIntervalMode.TIME || mode == MaintenanceIntervalMode.BOTH
    if (showKm) {
        OutlinedTextField(
            value = intervalKm,
            onValueChange = onIntervalKmChanged,
            enabled = enabled,
            label = { Text("Cada kilometraje") },
            suffix = { Text("km") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = maintenanceTextFieldColors()
        )
    }
    if (showTime) {
        OutlinedTextField(
            value = intervalTimeValue,
            onValueChange = onIntervalTimeValueChanged,
            enabled = enabled,
            label = { Text("Cada") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = maintenanceTextFieldColors()
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MaintenanceTimeUnit.entries.forEach { unit ->
                FilterChip(
                    selected = intervalTimeUnit == unit,
                    onClick = { onIntervalTimeUnitChanged(unit) },
                    enabled = enabled,
                    label = { Text(unit.displayName()) },
                    colors = maintenanceFilterChipColors(),
                    border = maintenanceFilterChipBorder(enabled, intervalTimeUnit == unit)
                )
            }
        }
    }
    if (mode == MaintenanceIntervalMode.BOTH) {
        Text(
            "Kilonom tendrá en cuenta el criterio que ocurra antes.",
            color = maintenanceSecondaryColor(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NextDueModeSelector(
    automaticAvailable: Boolean,
    selected: MaintenanceNextDueMode,
    enabled: Boolean,
    onSelected: (MaintenanceNextDueMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (automaticAvailable) {
            FilterChip(
                selected = selected == MaintenanceNextDueMode.AUTOMATIC,
                onClick = { onSelected(MaintenanceNextDueMode.AUTOMATIC) },
                enabled = enabled,
                label = { Text("Calcular automáticamente") },
                colors = maintenanceFilterChipColors(),
                border = maintenanceFilterChipBorder(enabled, selected == MaintenanceNextDueMode.AUTOMATIC)
            )
        }
        FilterChip(
            selected = selected == MaintenanceNextDueMode.MANUAL,
            onClick = { onSelected(MaintenanceNextDueMode.MANUAL) },
            enabled = enabled,
            label = { Text("Manual") },
            colors = maintenanceFilterChipColors(),
            border = maintenanceFilterChipBorder(enabled, selected == MaintenanceNextDueMode.MANUAL)
        )
        FilterChip(
            selected = selected == MaintenanceNextDueMode.NONE,
            onClick = { onSelected(MaintenanceNextDueMode.NONE) },
            enabled = enabled,
            label = { Text("Sin próximo aviso") },
            colors = maintenanceFilterChipColors(),
            border = maintenanceFilterChipBorder(enabled, selected == MaintenanceNextDueMode.NONE)
        )
    }
}

@Composable
private fun AutomaticDuePreview(nextDue: MaintenanceNextDue?) {
    MaintenanceCard {
        Text("Vista previa del próximo mantenimiento", style = MaterialTheme.typography.titleSmall)
        if (nextDue == null || (nextDue.nextDueKm == null && nextDue.nextDueDate == null)) {
            Text(
                "Completa los datos realizados y el intervalo para calcular el próximo ciclo.",
                color = maintenanceSecondaryColor()
            )
        } else {
            nextDue.nextDueKm?.let { Text("${it.formatKilometers()} km") }
            nextDue.nextDueDate?.let { Text(it.formatMaintenanceDate()) }
        }
    }
}

@Composable
private fun ManualDueConfiguration(
    reminder: ReminderMode,
    dueKm: String,
    dueDate: Long?,
    enabled: Boolean,
    focusManager: FocusManager,
    onReminderSelected: (ReminderMode) -> Unit,
    onDueKmChanged: (String) -> Unit,
    onDueDateClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReminderMode.entries.forEach { option ->
            FilterChip(
                selected = reminder == option,
                onClick = { onReminderSelected(option) },
                enabled = enabled,
                label = { Text(option.displayName()) },
                colors = maintenanceFilterChipColors(),
                border = maintenanceFilterChipBorder(enabled, reminder == option)
            )
        }
    }
    val hasDueKm = reminder == ReminderMode.KM || reminder == ReminderMode.BOTH
    val hasDueDate = reminder == ReminderMode.DATE || reminder == ReminderMode.BOTH
    if (hasDueKm) {
        OutlinedTextField(
            value = dueKm,
            onValueChange = onDueKmChanged,
            label = { Text("Próximo kilometraje") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = maintenanceTextFieldColors()
        )
    }
    if (hasDueDate) DateField("Próxima fecha", dueDate, enabled = enabled, onClick = onDueDateClick)
}

@Composable
private fun ReminderLeadConfiguration(
    showKm: Boolean,
    showDays: Boolean,
    leadKm: String,
    leadDays: String,
    enabled: Boolean,
    focusManager: FocusManager,
    onLeadKmChanged: (String) -> Unit,
    onLeadDaysChanged: (String) -> Unit
) {
    Text("Avísame antes", style = MaterialTheme.typography.titleMedium)
    if (showKm) {
        OutlinedTextField(
            value = leadKm,
            onValueChange = onLeadKmChanged,
            enabled = enabled,
            label = { Text("Aviso previo por kilometraje") },
            suffix = { Text("km antes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = maintenanceTextFieldColors()
        )
    }
    if (showDays) {
        OutlinedTextField(
            value = leadDays,
            onValueChange = onLeadDaysChanged,
            enabled = enabled,
            label = { Text("Aviso previo por fecha") },
            suffix = { Text("días antes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = maintenanceTextFieldColors()
        )
    }
    Text(
        "0 significa avisar solo cuando toque o esté vencido.",
        color = maintenanceSecondaryColor(),
        style = MaterialTheme.typography.bodySmall
    )
}

private data class MaintenanceFormDefaults(
    val type: MaintenanceType?, val tyrePosition: TyrePosition?, val customName: String,
    val nextDueKm: String, val nextDueDate: Long?, val recordDate: Long?, val recordKm: String,
    val intervalKm: String, val intervalTimeValue: String, val intervalTimeUnit: MaintenanceTimeUnit?,
    val reminderLeadKm: String, val reminderLeadDays: String,
    val cost: String, val notes: String, val includeRecord: Boolean, val reminderMode: ReminderMode,
    val intervalMode: MaintenanceIntervalMode, val nextDueMode: MaintenanceNextDueMode
)

internal fun selectedReminderDueValues(
    hasDueKm: Boolean,
    dueKm: String,
    hasDueDate: Boolean,
    dueDate: Long?
): Pair<Long?, Long?> =
    (if (hasDueKm) dueKm.toLongOrNull() else null) to (if (hasDueDate) dueDate else null)

internal fun validateIntervalConfiguration(
    mode: MaintenanceIntervalMode,
    intervalKm: String,
    intervalTimeValue: String,
    intervalTimeUnit: MaintenanceTimeUnit?
): String? {
    val hasKm = mode == MaintenanceIntervalMode.KM || mode == MaintenanceIntervalMode.BOTH
    val hasTime = mode == MaintenanceIntervalMode.TIME || mode == MaintenanceIntervalMode.BOTH
    if (hasKm && intervalKm.toLongOrNull()?.let { it > 0L } != true) {
        return "Introduce un intervalo de kilometraje válido."
    }
    if (hasTime && intervalTimeValue.toIntOrNull()?.let { it > 0 } != true) {
        return "Introduce un intervalo de tiempo válido."
    }
    if (hasTime && intervalTimeUnit == null) return "Selecciona una unidad de tiempo."
    return null
}

internal fun validateReminderLeads(
    leadKm: String,
    leadDays: String,
    validateKm: Boolean,
    validateDays: Boolean
): String? {
    if (validateKm && leadKm.toLongOrNull()?.let { it >= 0L } != true) {
        return "El aviso previo por kilometraje no puede ser negativo."
    }
    if (validateDays && leadDays.toLongOrNull()?.let { it >= 0L } != true) {
        return "El aviso previo por fecha no puede ser negativo."
    }
    return null
}

internal fun MaintenanceNextDueMode.toRepositoryUpdate(
    manualValues: Pair<Long?, Long?>
): MaintenanceNextDueUpdate = when (this) {
    MaintenanceNextDueMode.AUTOMATIC -> MaintenanceNextDueUpdate.AutomaticFromInterval
    MaintenanceNextDueMode.MANUAL -> MaintenanceNextDueUpdate.Manual(manualValues.first, manualValues.second)
    MaintenanceNextDueMode.NONE -> MaintenanceNextDueUpdate.Clear
}

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

private fun MaintenanceIntervalMode.displayName(): String = when (this) {
    MaintenanceIntervalMode.NONE -> "Sin intervalo"
    MaintenanceIntervalMode.KM -> "Por kilometraje"
    MaintenanceIntervalMode.TIME -> "Por tiempo"
    MaintenanceIntervalMode.BOTH -> "Kilometraje y tiempo"
}

private fun MaintenanceTimeUnit.displayName(): String = when (this) {
    MaintenanceTimeUnit.DAYS -> "Días"
    MaintenanceTimeUnit.MONTHS -> "Meses"
    MaintenanceTimeUnit.YEARS -> "Años"
}

private fun reminderMode(km: Long?, date: Long?): ReminderMode = when {
    km != null && date != null -> ReminderMode.BOTH
    km != null -> ReminderMode.KM
    date != null -> ReminderMode.DATE
    else -> ReminderMode.NONE
}

private fun intervalMode(
    intervalKm: Long?,
    intervalTimeValue: Int?,
    intervalTimeUnit: MaintenanceTimeUnit?
): MaintenanceIntervalMode = when {
    intervalKm != null && intervalTimeValue != null && intervalTimeUnit != null -> MaintenanceIntervalMode.BOTH
    intervalKm != null -> MaintenanceIntervalMode.KM
    intervalTimeValue != null && intervalTimeUnit != null -> MaintenanceIntervalMode.TIME
    else -> MaintenanceIntervalMode.NONE
}

private fun initialNextDueMode(
    mode: MaintenanceFormMode,
    item: com.bgr3108.kilonom.data.MaintenanceItemEntity?
): MaintenanceNextDueMode = when {
    mode == MaintenanceFormMode.REGISTER_RECORD && item?.let { intervalMode(it.intervalKm, it.intervalTimeValue, it.intervalTimeUnit) } != MaintenanceIntervalMode.NONE -> MaintenanceNextDueMode.AUTOMATIC
    item?.nextDueKm != null || item?.nextDueDate != null -> MaintenanceNextDueMode.MANUAL
    else -> MaintenanceNextDueMode.NONE
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
