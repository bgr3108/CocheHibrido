package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.viewmodel.CarViewModel

@Composable
fun CarScreen(
    innerPadding: PaddingValues,
    viewModel: CarViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Datos del coche",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.marca,
            onValueChange = viewModel::onMarcaChange,
            label = { Text("Marca") }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.modelo,
            onValueChange = viewModel::onModeloChange,
            label = { Text("Modelo") }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.matricula,
            onValueChange = viewModel::onMatriculaChange,
            label = { Text("Matricula") }
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = uiState.kmActuales,
            onValueChange = viewModel::onKmChange,
            label = { Text("Km actuales") }
        )

        Button(onClick = viewModel::saveCar) {
            Text("Guardar datos")
        }

        if (uiState.isSaved) {
            Text(
                text = "Datos guardados correctamente",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
