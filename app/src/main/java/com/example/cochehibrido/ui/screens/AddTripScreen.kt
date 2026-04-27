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
import androidx.compose.ui.text.input.ImeAction

import com.example.cochehibrido.data.Trip
import com.example.cochehibrido.viewmodel.TripViewModel
import com.example.cochehibrido.util.toDoubleSafe

import java.util.*

@Composable
fun AddTripScreen(
    innerPadding: PaddingValues,
    viewModel: TripViewModel,
    trip: Trip? = null, // 🔥 PARA EDITAR
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    var fecha by remember {
        mutableStateOf(
            trip?.fecha ?: "%02d-%02d-%04d".format(
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    var km by remember {
        mutableStateOf(trip?.km?.toString()?.replace(".", ",") ?: "")
    }

    var consumoGasolina by remember {
        mutableStateOf(trip?.consumoGasolina?.toString()?.replace(".", ",") ?: "")
    }

    var consumoElectrico by remember {
        mutableStateOf(trip?.consumoElectrico?.toString()?.replace(".", ",") ?: "")
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, y, m, d -> fecha = "%02d-%02d-%04d".format(d, m + 1, y) },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

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

        // 📅 Fecha
        Button(onClick = { datePickerDialog.show() }) {
            Text("Fecha: $fecha")
        }

        // Km
        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("Km") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions {
                focusManager.moveFocus(FocusDirection.Down)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Consumo gasolina
        OutlinedTextField(
            value = consumoGasolina,
            onValueChange = { consumoGasolina = it },
            label = { Text("Gasolina (L/100km)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions {
                focusManager.moveFocus(FocusDirection.Down)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Consumo eléctrico
        OutlinedTextField(
            value = consumoElectrico,
            onValueChange = { consumoElectrico = it },
            label = { Text("Eléctrico (kWh/100km)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    guardarTrip(viewModel, trip, fecha, km, consumoGasolina, consumoElectrico, onSaved)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Button(
                onClick = {
                    guardarTrip(viewModel, trip, fecha, km, consumoGasolina, consumoElectrico, onSaved)
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
    fecha: String,
    km: String,
    consumoGasolina: String,
    consumoElectrico: String,
    onSaved: () -> Unit
) {
    val newTrip = Trip(
        id = trip?.id ?: 0, // 🔥 clave para editar
        fecha = fecha,
        km = km.toDoubleSafe(),
        consumoGasolina = consumoGasolina.toDoubleSafe(),
        consumoElectrico = consumoElectrico.toDoubleSafe()
    )

    viewModel.saveTrip(newTrip) {
        onSaved()
    }
}