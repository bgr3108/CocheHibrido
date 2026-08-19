package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import java.util.Calendar
import com.bgr3108.kilonom.util.toFiniteDoubleOrNull
import com.bgr3108.kilonom.util.toKilometersDisplay
import com.bgr3108.kilonom.util.toKilometersInput
import com.bgr3108.kilonom.util.toKilometersOrNull
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import android.app.TimePickerDialog
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.supportsElectricEntries
import com.bgr3108.kilonom.data.supportsFuelEntries
import com.bgr3108.kilonom.data.fuelLevelAfterSteps
import com.bgr3108.kilonom.data.fuelLevelAfterPercentageText
import com.bgr3108.kilonom.data.initialFuelLevelAfter
import com.bgr3108.kilonom.data.isFullTankLevel
import com.bgr3108.kilonom.data.isSupportedFuelLevelAfter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape

@Composable
fun AddConsumptionScreen(
    viewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    innerPadding: PaddingValues,
    entry: FuelEntry? = null,
    onClose: () -> Unit
){
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val focusManager = LocalFocusManager.current

    val currentVehicle by homeViewModel
        .vehicle
        .collectAsState()

    val entries by viewModel.entries.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val initialDateMillis = entry?.fecha ?: System.currentTimeMillis()
    val initialCalendar = Calendar.getInstance().apply {
        timeInMillis = initialDateMillis
    }

    var fechaMillis by rememberSaveable(entry?.id) {
        mutableLongStateOf(initialDateMillis)
    }

    var hour by rememberSaveable(entry?.id) {
        mutableIntStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY))
    }

    var minute by rememberSaveable(entry?.id) {
        mutableIntStateOf(initialCalendar.get(Calendar.MINUTE))
    }

    val selectedDateCalendar = Calendar.getInstance().apply {
        timeInMillis = fechaMillis
    }

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, y, m, d ->

            val cal = Calendar.getInstance()

            cal.timeInMillis = fechaMillis

            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)

            fechaMillis = cal.timeInMillis

            TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->

                    hour = selectedHour
                    minute = selectedMinute
                },
                hour,
                minute,
                true
            ).show()
        },
        selectedDateCalendar.get(Calendar.YEAR),
        selectedDateCalendar.get(Calendar.MONTH),
        selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
    )

    var cantidad by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.cantidad?.toString()?.replace(".", ",") ?: "")
    }
    var porcentajeInicio by rememberSaveable(entry?.id) {
        mutableStateOf("")
    }

    var porcentajeFin by rememberSaveable(entry?.id) {
        mutableStateOf("")
    }

    var precio by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.precio?.toString()?.replace(".", ",") ?: "")
    }

    var km by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.km?.toKilometersInput() ?: "")
    }

    val tipoSeleccionadoName = rememberSaveable(entry?.id) {
        mutableStateOf(
            (entry?.tipo ?: defaultFuelTypeFor(currentVehicle.type)).name
        )
    }

    val tipoSeleccionado = FuelType.entries
        .firstOrNull { it.name == tipoSeleccionadoName.value }
        ?: FuelType.GASOLINA

    var fullTank by rememberSaveable(entry?.id) {
        mutableStateOf(entry?.fullTank ?: true)
    }

    var fuelLevelAfter by rememberSaveable(entry?.id) {
        mutableStateOf(initialFuelLevelAfter(entry))
    }

    var errorCapacidad by remember {
        mutableStateOf<String?>(null)
    }

    var errorPorcentaje by remember {
        mutableStateOf<String?>(null)
    }

    var errorKm by rememberSaveable(entry?.id) {
        mutableStateOf(
            entry
                ?.takeIf { it.km.toKilometersInput().isEmpty() }
                ?.let {
                    "El kilometraje guardado no es válido. Introduce un número entero igual o mayor que cero"
                }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Button(
            onClick = {
                datePickerDialog.show()
            }
        ) {

            Text(
                String.format(
                    currentLocale,
                    "%s %02d:%02d",

                    java.text.SimpleDateFormat(
                        "dd/MM/yyyy",
                        currentLocale
                    ).format(fechaMillis),

                    hour,
                    minute
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (entry == null) "Nuevo consumo" else "Editar consumo",
            style = MaterialTheme.typography.headlineSmall
        )

        if (tipoSeleccionado == FuelType.ELECTRICO) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                OutlinedTextField(
                    value = porcentajeInicio,
                    onValueChange = {
                        porcentajeInicio = it
                    },
                    label = {
                        Text("% inicio")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (
                    cantidad.isBlank() &&
                    porcentajeInicio.isNotBlank() &&
                    porcentajeFin.isNotBlank()
                ) {

                    val inicio = porcentajeInicio.toFiniteDoubleOrNull()
                    val fin = porcentajeFin.toFiniteDoubleOrNull()

                    if (inicio != null && fin != null) {
                        val porcentajeCargado = fin - inicio

                        val kwhEstimados =
                            (currentVehicle.batteryCapacity * porcentajeCargado) / 100.0

                        Text(
                            text = "≈ ${
                                String.format(
                                    currentLocale,
                                    "%.1f",
                                    kwhEstimados
                                )
                            } kWh estimados",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OutlinedTextField(
                    value = porcentajeFin,
                    onValueChange = {
                        porcentajeFin = it
                    },
                    label = {
                        Text("% fin")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val textoCantidad =
            if (tipoSeleccionado == FuelType.ELECTRICO)
                "Cantidad (kWh)"
            else
                "Cantidad (L)"

        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it },

            label = {
                Text(textoCantidad)
            },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            ),
            singleLine = true,

            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text =
                if (tipoSeleccionado == FuelType.ELECTRICO)
                    "Capacidad batería: ${currentVehicle.batteryCapacity} kWh"
                else
                    "Capacidad depósito: ${currentVehicle.fuelTankCapacity} L",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = precio,
            onValueChange = { precio = it },
            label = { Text("Importe total (€)") },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),

            keyboardActions = KeyboardActions(
                onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                }
            ),

            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = km,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) {
                    km = value
                    errorKm = null
                }
            },
            label = { Text("Kilómetros") },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),

            modifier = Modifier.fillMaxWidth()
        )

        if (tipoSeleccionado == FuelType.GASOLINA) {
            FuelLevelAfterSelector(
                selectedLevel = fuelLevelAfter,
                onLevelSelected = { level ->
                    fuelLevelAfter = level
                    fullTank = isFullTankLevel(level)
                }
            )
        }

        val showFuelOption = currentVehicle.type.supportsFuelEntries
        val showElectricOption = currentVehicle.type.supportsElectricEntries

        if (showFuelOption && showElectricOption) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = {
                        tipoSeleccionadoName.value = FuelType.GASOLINA.name
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (tipoSeleccionado == FuelType.GASOLINA)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surface,

                        contentColor =
                            if (tipoSeleccionado == FuelType.GASOLINA)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Gasolina/Diésel/GLP")
                }

                Button(
                    onClick = {
                        tipoSeleccionadoName.value = FuelType.ELECTRICO.name
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (tipoSeleccionado == FuelType.ELECTRICO)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surface,

                        contentColor =
                            if (tipoSeleccionado == FuelType.ELECTRICO)
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Eléctrico")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        errorKm?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        errorPorcentaje?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        errorCapacidad?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }


        Button(
            onClick = {
                val finalCalendar = Calendar.getInstance().apply {
                    timeInMillis = fechaMillis

                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }

                errorCapacidad = null
                errorPorcentaje = null
                errorKm = null

                val precioFinal = precio.toFiniteDoubleOrNull()

                if (precioFinal == null || precioFinal < 0.0) {
                    errorCapacidad = "El importe debe ser un número válido igual o mayor que cero"
                    return@Button
                }

                val kmNuevo = km.toKilometersOrNull()

                if (kmNuevo == null || kmNuevo < 0.0) {
                    errorKm = "Los kilómetros deben ser un número entero válido igual o mayor que cero"
                    return@Button
                }

                val usarPorcentajes =
                    tipoSeleccionado == FuelType.ELECTRICO &&
                            (
                                cantidad.isBlank() ||
                                        porcentajeInicio.isNotBlank() ||
                                        porcentajeFin.isNotBlank()
                                )

                val cantidadCalculada = if (usarPorcentajes) {
                    val inicio = porcentajeInicio.toFiniteDoubleOrNull()
                    val fin = porcentajeFin.toFiniteDoubleOrNull()

                    if (inicio == null || fin == null) {
                        errorPorcentaje = "Los porcentajes deben ser números válidos"
                        return@Button
                    }

                    if (inicio !in 0.0..100.0) {
                        errorPorcentaje = "El % inicial debe estar entre 0 y 100"
                        return@Button
                    }

                    if (fin !in 0.0..100.0) {
                        errorPorcentaje = "El % final debe estar entre 0 y 100"
                        return@Button
                    }

                    if (fin < inicio) {
                        errorPorcentaje = "El % final no puede ser menor que el inicial"
                        return@Button
                    }

                    (currentVehicle.batteryCapacity * (fin - inicio)) / 100.0
                } else {
                    null
                }

                val cantidadFinal =
                    if (cantidad.isNotBlank()) {
                        cantidad.toFiniteDoubleOrNull()
                    } else {
                        cantidadCalculada
                    }

                if (
                    cantidadFinal == null ||
                    !cantidadFinal.isFinite() ||
                    cantidadFinal <= 0.0
                ) {
                    errorCapacidad = "La cantidad debe ser un número válido mayor que cero"
                    return@Button
                }

                if (
                    tipoSeleccionado == FuelType.GASOLINA &&
                    cantidadFinal > currentVehicle.fuelTankCapacity &&
                    currentVehicle.fuelTankCapacity > 0
                ) {
                    errorCapacidad =
                        "La cantidad supera la capacidad del depósito (${currentVehicle.fuelTankCapacity} L)"
                    return@Button
                }

                val selectedFuelLevel = fuelLevelAfter
                if (
                    tipoSeleccionado == FuelType.GASOLINA &&
                    selectedFuelLevel != null &&
                    !isSupportedFuelLevelAfter(selectedFuelLevel)
                ) {
                    errorCapacidad = "Selecciona uno de los niveles disponibles del depósito"
                    return@Button
                }

                if (
                    tipoSeleccionado == FuelType.ELECTRICO &&
                    cantidadFinal > currentVehicle.batteryCapacity &&
                    currentVehicle.batteryCapacity > 0
                ) {
                    errorCapacidad =
                        "La cantidad supera la capacidad de la batería (${currentVehicle.batteryCapacity} kWh)"
                    return@Button
                }

                val fechaNueva = finalCalendar.timeInMillis

                val otrosRegistros =
                    entries.filter { it.id != (entry?.id ?: 0) }

                val registroAnterior =
                    otrosRegistros
                        .filter { it.fecha < fechaNueva }
                        .maxByOrNull { it.fecha }

                val registroPosterior =
                    otrosRegistros
                        .filter { it.fecha > fechaNueva }
                        .minByOrNull { it.fecha }

// 🔥 Siempre comprobar km inicial del Setup
                if (kmNuevo < currentVehicle.currentKm) {

                    errorKm =
                        "Los kilómetros no pueden ser inferiores al kilometraje inicial (${currentVehicle.currentKm.toKilometersDisplay()} km)"

                    return@Button
                }

// 🔥 Comprobar registro anterior
                if (
                    registroAnterior != null &&
                    kmNuevo < registroAnterior.km
                ) {

                    errorKm =
                        "Los kilómetros no pueden ser inferiores al registro anterior (${registroAnterior.km.toKilometersDisplay()} km)"

                    return@Button
                }

// 🔥 Comprobar registro posterior
                if (
                    registroPosterior != null &&
                    kmNuevo > registroPosterior.km
                ) {

                    errorKm =
                        "Los kilómetros no pueden ser superiores al registro posterior (${registroPosterior.km.toKilometersDisplay()} km)"

                    return@Button
                }

                val newEntry = FuelEntry(
                    id = entry?.id ?: 0,
                    fecha = finalCalendar.timeInMillis,
                    cantidad = cantidadFinal,
                    precio = precioFinal,
                    tipo = tipoSeleccionado,
                    km = kmNuevo,
                    fullTank = if (tipoSeleccionado == FuelType.GASOLINA && fuelLevelAfter != null) {
                        isFullTankLevel(fuelLevelAfter)
                    } else {
                        fullTank
                    },
                    fuelLevelAfter = if (tipoSeleccionado == FuelType.GASOLINA) fuelLevelAfter else null
                )

                viewModel.saveEntry(
                    entry = newEntry,
                    onSaved = onClose,
                    onError = {
                        errorCapacidad = "No se pudo guardar el registro. Inténtalo de nuevo."
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving
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

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving
        ) {
            Text("Cancelar")
        }
    }
}

@Composable
private fun FuelLevelAfterSelector(
    selectedLevel: Double?,
    onLevelSelected: (Double) -> Unit
) {
    Text(
        text = "Nivel del depósito después de repostar",
        style = MaterialTheme.typography.bodyLarge
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        fuelLevelAfterSteps.forEachIndexed { index, level ->
            val selected = selectedLevel == level
            val label = when (index) {
                0 -> "Vacío"
                2 -> "¼"
                4 -> "½"
                6 -> "¾"
                8 -> "Lleno"
                else -> null
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (label == null) 20.dp else 28.dp)
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onLevelSelected(level) }
                        .semantics {
                            contentDescription = "Nivel del depósito ${fuelLevelAfterPercentageText(level)}"
                        }
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }

    selectedLevel?.let { level ->
        Text(
            text = "Nivel estimado: ${fuelLevelAfterPercentageText(level)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun defaultFuelTypeFor(vehicleType: VehicleType?): FuelType =
    if (vehicleType.supportsElectricEntries && !vehicleType.supportsFuelEntries) {
        FuelType.ELECTRICO
    } else {
        FuelType.GASOLINA
    }
