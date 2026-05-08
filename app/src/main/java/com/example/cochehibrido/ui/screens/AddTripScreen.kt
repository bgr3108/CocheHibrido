package com.example.cochehibrido.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction

import com.example.cochehibrido.data.Trip
import com.example.cochehibrido.viewmodel.TripViewModel
import com.example.cochehibrido.util.toDoubleSafe
import com.example.cochehibrido.util.toDateTimeString
import android.app.TimePickerDialog


import java.util.*

@Composable
fun AddTripScreen(
    innerPadding: PaddingValues,
    viewModel: TripViewModel,
    trip: Trip? = null,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    var fechaMillis by remember {
        mutableStateOf(trip?.fecha ?: System.currentTimeMillis())
    }

    var hour by remember {
        mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY))
    }

    var minute by remember {
        mutableStateOf(calendar.get(Calendar.MINUTE))
    }

    val datePickerDialog = DatePickerDialog(
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

    var km by remember {
        mutableStateOf(trip?.km?.toString()?.replace(".", ",") ?: "")
    }

    var consumoGasolina by remember {
        mutableStateOf(trip?.consumoGasolina?.toString()?.replace(".", ",") ?: "")
    }

    var consumoElectrico by remember {
        mutableStateOf(trip?.consumoElectrico?.toString()?.replace(".", ",") ?: "")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            if (trip == null) "Añadir viaje" else "Editar viaje",
            style = MaterialTheme.typography.headlineSmall
        )

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
        // Km
        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("Km") },

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

        // Gasolina
        OutlinedTextField(
            value = consumoGasolina,
            onValueChange = { consumoGasolina = it },
            label = { Text("Gasolina (L/100km)") },

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

        // Eléctrico
        OutlinedTextField(
            value = consumoElectrico,
            onValueChange = { consumoElectrico = it },
            label = { Text("Eléctrico (kWh/100km)") },

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),

            keyboardActions = KeyboardActions(
                onDone = {
                    guardarTrip(
                        viewModel,
                        trip,
                        fechaMillis,
                        km,
                        consumoGasolina,
                        consumoElectrico,
                        onSaved
                    )
                }
            ),

            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Button(
                onClick = {
                    guardarTrip(
                        viewModel,
                        trip,
                        fechaMillis,
                        km,
                        consumoGasolina,
                        consumoElectrico,
                        onSaved
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
        }
    }
}

private fun guardarTrip(
    viewModel: TripViewModel,
    trip: Trip?,
    fechaMillis: Long, // 🔥 AÑADIR
    km: String,
    consumoGasolina: String,
    consumoElectrico: String,
    onSaved: () -> Unit
) {
    val newTrip = Trip(
        id = trip?.id ?: 0,
        fecha = fechaMillis, // 🔥 clave
        km = km.toDoubleSafe(),
        consumoGasolina = consumoGasolina.toDoubleSafe(),
        consumoElectrico = consumoElectrico.toDoubleSafe()
    )

    viewModel.saveTrip(newTrip) {
        onSaved()
    }
}