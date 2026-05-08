package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.util.toDateTimeString
import java.util.Calendar
import com.example.cochehibrido.util.toDoubleSafe
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import android.app.TimePickerDialog
import java.util.Locale

@Composable
fun AddConsumptionScreen(
    viewModel: FuelEntryViewModel,
    entry: FuelEntry? = null,
    onClose: () -> Unit
){
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val focusManager = LocalFocusManager.current

// 🔥 ESTO VA PRIMERO
    var fechaMillis by remember {
        mutableStateOf(System.currentTimeMillis())
    }
    var hour by remember {
        mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY))
    }

    var minute by remember {
        mutableStateOf(calendar.get(Calendar.MINUTE))
    }

// 🔥 DESPUÉS el DatePicker
    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, y, m, d ->
            val cal = Calendar.getInstance()
            cal.set(y, m, d)
            fechaMillis = cal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    var cantidad by remember {
        mutableStateOf(entry?.cantidad?.toString()?.replace(".", ",") ?: "")
    }

    var precio by remember {
        mutableStateOf(entry?.precio?.toString()?.replace(".", ",") ?: "")
    }

    var km by remember {
        mutableStateOf(entry?.km?.toString()?.replace(".", ",") ?: "")
    }

    var tipoSeleccionado by remember {
        mutableStateOf(entry?.tipo ?: FuelType.GASOLINA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(onClick = { datePickerDialog.show() }) {
            Text("Fecha: ${fechaMillis.toDateTimeString()}")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {

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

            }
        ) {
            Text(
                String.format(
                    Locale.getDefault(),
                    "Hora: %02d:%02d",
                    hour,
                    minute
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))


        Text("Nuevo repostaje", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it },
            label = { Text("Cantidad (L o kWh)") },

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
            value = precio,
            onValueChange = { precio = it },
            label = { Text("Precio (€)") },

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
            onValueChange = { km = it },
            label = { Text("Kilómetros") },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),

            keyboardActions = KeyboardActions(
                onDone = {
                    val finalCalendar = Calendar.getInstance().apply {
                        timeInMillis = fechaMillis

                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                    }
                    val newEntry = FuelEntry(
                        id = entry?.id ?: 0,
                        fecha = finalCalendar.timeInMillis,
                        cantidad = cantidad.toDoubleSafe(),
                        precio = precio.toDoubleSafe(),
                        tipo = tipoSeleccionado,
                        km = km.toDoubleSafe()
                    )

                    viewModel.saveEntry(newEntry) {
                        onClose()
                    }
                }
            ),

            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Button(
                onClick = { tipoSeleccionado = FuelType.GASOLINA },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tipoSeleccionado == FuelType.GASOLINA)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface,
                    contentColor = if (tipoSeleccionado == FuelType.GASOLINA)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Gasolina")
            }

            Button(
                onClick = { tipoSeleccionado = FuelType.ELECTRICO },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tipoSeleccionado == FuelType.ELECTRICO)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surface,
                    contentColor = if (tipoSeleccionado == FuelType.ELECTRICO)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Eléctrico")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val finalCalendar = Calendar.getInstance().apply {
                    timeInMillis = fechaMillis

                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }
                val newEntry = FuelEntry(
                    id = entry?.id ?: 0, // 🔥 CLAVE
                    fecha = finalCalendar.timeInMillis,
                    cantidad = cantidad.toDoubleSafe(),
                    precio = precio.toDoubleSafe(),
                    tipo = tipoSeleccionado,
                    km = km.toDoubleSafe()
                )

                viewModel.saveEntry(newEntry) {
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancelar")
        }
    }
}