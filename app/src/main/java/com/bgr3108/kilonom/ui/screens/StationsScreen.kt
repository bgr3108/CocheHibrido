package com.bgr3108.kilonom.ui.screens

import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.util.ExternalLinks
import com.bgr3108.kilonom.util.openExternalUrl
import com.bgr3108.kilonom.viewmodel.StationsViewModel

@Composable
fun StationsScreen(
    innerPadding: PaddingValues,
    viewModel: StationsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val municipalities by viewModel.municipalities.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CacheStatus(
                hasCache = state.hasCache,
                isStale = state.isStale,
                refreshing = state.isRefreshing,
                error = state.refreshError,
                downloadedAtMillis = state.metadata?.downloadedAtMillis,
                modifier = Modifier.weight(1f)
            )
            if (state.hasCache) {
                TextButton(onClick = viewModel::clearCache, enabled = !state.isRefreshing) {
                    Text("Borrar caché")
                }
            }
        }

        if (state.hasCache) {
            StationFilters(
                selectedFuelType = state.filter.fuelType,
                provinces = provinces,
                selectedProvince = state.filter.province,
                municipalities = municipalities,
                selectedMunicipality = state.filter.municipality,
                onFuelTypeSelected = viewModel::selectFuelType,
                onProvinceSelected = viewModel::selectProvince,
                onMunicipalitySelected = viewModel::selectMunicipality
            )
            if (stations.isEmpty()) {
                Text(
                    "No hay estaciones que coincidan con los filtros seleccionados.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(stations, key = { it.externalId }) { station -> StationCard(station) }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider()
                            Text(
                                "Datos: Ministerio para la Transición Ecológica y el Reto Demográfico (MITECO)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            TextButton(onClick = { context.openExternalUrl(ExternalLinks.MITECO_STATIONS_URL) }) {
                                Text("Ver fuente de datos")
                            }
                        }
                    }
                }
            }
        } else if (!state.isRefreshing) {
            Text(
                "Necesitas conexión para cargar las estaciones por primera vez.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

@Composable
private fun CacheStatus(
    hasCache: Boolean,
    isStale: Boolean,
    refreshing: Boolean,
    error: String?,
    downloadedAtMillis: Long?,
    modifier: Modifier = Modifier
) {
    val text = when {
        refreshing && !hasCache -> "Cargando estaciones…"
        error != null && hasCache -> "No se pudieron actualizar los datos. Mostrando última información disponible."
        error != null -> "Necesitas conexión para cargar las estaciones por primera vez."
        hasCache && isStale -> "Hay una actualización de estaciones disponible."
        hasCache -> "Datos guardados: ${downloadedAtMillis?.toDisplayDateTime().orEmpty()}"
        else -> ""
    }
    if (text.isNotBlank()) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}

@Composable
private fun StationFilters(
    selectedFuelType: StationFuelType,
    provinces: List<String>,
    selectedProvince: String?,
    municipalities: List<String>,
    selectedMunicipality: String?,
    onFuelTypeSelected: (StationFuelType) -> Unit,
    onProvinceSelected: (String?) -> Unit,
    onMunicipalitySelected: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionMenu(
            "Combustible: ${selectedFuelType.displayName}",
            StationFuelType.entries.map { it.displayName to it.name }
        ) { name -> onFuelTypeSelected(StationFuelType.valueOf(name)) }
        SelectionMenu(
            "Provincia: ${selectedProvince ?: "Todas"}",
            listOf("Todas" to "") + provinces.map { it to it },
            { onProvinceSelected(it.ifBlank { null }) }
        )
        SelectionMenu(
            "Municipio: ${selectedMunicipality ?: "Todos"}",
            listOf("Todos" to "") + municipalities.map { it to it },
            { onMunicipalitySelected(it.ifBlank { null }) }
        )
    }
}

@Composable
private fun SelectionMenu(label: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    val expanded = remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded.value = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            options.forEach { (name, value) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { expanded.value = false; onSelected(value) }
                )
            }
        }
    }
}

@Composable
private fun StationCard(item: StationListItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(item.address, item.municipality, item.province).filter(String::isNotBlank).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${item.productName}: ${"%.3f".format(java.util.Locale.forLanguageTag("es-ES"), item.price)} €",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            item.sourceUpdatedAtMillis?.let {
                Text(
                    "Actualizado: ${it.toDisplayDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Long.toDisplayDateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
    java.util.Locale.forLanguageTag("es-ES")
).format(Date(this))
