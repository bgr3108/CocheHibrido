package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.RecordLoadState

/** The holder retains a draft through retry/loading, without ever placing database objects in Bundle. */
@Composable
internal fun <T> RecordLoadContent(
    state: RecordLoadState<T>,
    formKey: String,
    padding: PaddingValues,
    onRetry: () -> Unit,
    onBack: (() -> Unit)?,
    content: @Composable (T) -> Unit
) {
    val drafts = rememberSaveableStateHolder()
    val message = when (state) {
        is RecordLoadState.Found -> {
            drafts.SaveableStateProvider("${state.vehicleId}:$formKey") { content(state.value) }
            return
        }
        RecordLoadState.Loading -> "Cargando registro…"
        RecordLoadState.NotFound -> "Registro no encontrado"
        is RecordLoadState.Error -> "No se ha podido leer el registro. Tus datos no se han modificado."
    }
    Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state == RecordLoadState.Loading) CircularProgressIndicator()
        Text(message)
        if (state is RecordLoadState.Error) {
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
        if (onBack != null) TextButton(onClick = onBack) { Text("Volver") }
    }
}
