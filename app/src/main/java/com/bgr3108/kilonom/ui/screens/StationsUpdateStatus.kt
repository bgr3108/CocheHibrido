package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.text.DateFormat
import java.util.Date

/** Public-cache status remains available independently of the chosen search area. */
@Composable
internal fun StationsUpdateStatus(
    downloadedAtMillis: Long?,
    stale: Boolean,
    refreshing: Boolean,
    error: String?,
    succeeded: Boolean,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                downloadedAtMillis?.takeIf { it > 0 }?.let {
                    Text(
                        "Última actualización: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                stationUpdateStatusMessage(downloadedAtMillis != null, stale, refreshing, error, succeeded)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text("Actualizar datos") }
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

internal fun stationUpdateStatusMessage(
    hasCache: Boolean,
    stale: Boolean,
    refreshing: Boolean,
    error: String?,
    succeeded: Boolean
): String? = when {
    refreshing -> "Actualizando datos…"
    error != null -> error + if (hasCache) " Se seguirán mostrando los datos guardados."
        else " Necesitas descargar los datos por primera vez."
    succeeded -> "Datos actualizados correctamente."
    hasCache && stale -> "Los datos guardados pueden estar desactualizados."
    !hasCache -> "Necesitas conexión para descargar los datos por primera vez."
    else -> null
}
