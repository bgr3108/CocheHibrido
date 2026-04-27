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

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.util.toDoubleSafe

import java.util.*

@Composable
fun AddConsumptionScreen(
    innerPadding: PaddingValues,
    viewModel: FuelEntryViewModel,
    entry: FuelEntry? = null, // 🔥 PARA EDITAR
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val calendar = Calendar.getInstance()

    var fecha by remember {
        mutableStateOf(
            entry?.fecha ?: "%02d-%02d-%04d".format(
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR)
            )
        )
    }

    var cantidad by remember {
        mutableStateOf(entry?.cantidad?.toString()?.replace(".", ",") ?: "")
    }

    var precio by remember {
        mutableStateOf(entry?.precio?.toString()?.replace(".", ",") ?: "")
    }

    var km by remember {
        mutableStateOf(entry?.km?.toString()?.replace(".", ",") ?: "")
    }

    var tipo by remember {
        mutableStateOf(entry?.tipo ?: FuelType.GASOLINA)
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
            if (entry == null) "Añadir repostaje" else "Editar repostaje",
            style = MaterialTheme.typography.headlineSmall
        )

        // 📅 Fecha
        Button(onClick = { datePickerDialog.show() }) {
            Text("Fecha: $fecha")
        }

        // 🔥 Tipo
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { tipo = FuelType.GASOLINA },
                modifier = Modifier.weight(1f)
            ) {
                Text("Gasolina")
            }
            Button(
                onClick = { tipo = FuelType.ELECTRICO },
                modifier = Modifier.weight(1f)
            ) {
                Text("Eléctrico")
            }
        }

        // Cantidad
        OutlinedTextField(
            value = cantidad,
            onValueChange = { cantidad = it },
            label = {
                Text(if (tipo == FuelType.GASOLINA) "Litros" else "kWh")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions {
                focusManager.moveFocus(FocusDirection.Down)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Precio
        OutlinedTextField(
            value = precio,
            onValueChange = { precio = it },
            label = { Text("Precio (€)") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions {
                focusManager.moveFocus(FocusDirection.Down)
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Km
        OutlinedTextField(
            value = km,
            onValueChange = { km = it },
            label = { Text("Km del coche") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    guardar(viewModel, entry, fecha, cantidad, precio, tipo, km, onSaved)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

            Button(
                onClick = {
                    guardar(viewModel, entry, fecha, cantidad, precio, tipo, km, onSaved)
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

private fun guardar(
    viewModel: FuelEntryViewModel,
    entry: FuelEntry?,
    fecha: String,
    cantidad: String,
    precio: String,
    tipo: FuelType,
    km: String,
    onSaved: () -> Unit
) {
    val newEntry = FuelEntry(
        id = entry?.id ?: 0, // 🔥 clave para editar
        fecha = fecha,
        cantidad = cantidad.toDoubleSafe(),
        precio = precio.toDoubleSafe(),
        tipo = tipo,
        km = km.toDoubleSafe()
    )

    viewModel.saveEntry(newEntry) {
        onSaved()
    }
}