package com.bgr3108.kilonom.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationLocationProvider
import com.bgr3108.kilonom.stations.StationSortOrder
import com.bgr3108.kilonom.stations.StationsViewMode
import com.bgr3108.kilonom.stations.canSortStationsByDistance
import com.bgr3108.kilonom.stations.createStationNavigationIntent
import com.bgr3108.kilonom.stations.isDefault
import com.bgr3108.kilonom.stations.locationPermissionGranted
import com.bgr3108.kilonom.stations.summary
import com.bgr3108.kilonom.util.ExternalLinks
import com.bgr3108.kilonom.util.openExternalUrl
import com.bgr3108.kilonom.viewmodel.StationsViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(innerPadding: PaddingValues, viewModel: StationsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var selectedStation by remember { mutableStateOf<StationListItem?>(null) }
    var isLocating by remember { mutableStateOf(false) }

    LaunchedEffect(stations) {
        selectedStation = selectedStationAfterFiltering(selectedStation, stations)
    }

    fun obtainLocation() {
        if (isLocating) return
        scope.launch {
            isLocating = true
            try {
                val location = StationLocationProvider.requestCurrentLocation(context)
                viewModel.updateCurrentLocation(location)
                if (location == null) snackbarHostState.showSnackbar("No se ha podido obtener tu ubicación.")
            } finally {
                isLocating = false
            }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (locationPermissionGranted(permissions)) {
            obtainLocation()
        }
    }
    fun requestLocation() {
        if (StationLocationProvider.hasPermission(context)) {
            obtainLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CacheStatus(
            hasCache = state.hasCache,
            isStale = state.isStale,
            refreshing = state.isRefreshing,
            error = state.refreshError,
            downloadedAtMillis = state.metadata?.downloadedAtMillis
        )

        if (state.hasCache) {
            StationToolbar(
                viewMode = state.viewMode,
                hasActiveFilters = !state.filter.isDefault(),
                filterSummary = state.filter.summary(),
                onViewModeSelected = viewModel::setViewMode,
                onFiltersClick = { showFilters = true }
            )
            when {
                stations.isEmpty() -> Text(
                    "No hay estaciones que coincidan con los filtros seleccionados.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                state.viewMode == StationsViewMode.LIST -> StationList(
                    stations,
                    modifier = Modifier.weight(1f)
                )
                else -> Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    StationMap(
                        stations = stations,
                        currentLocation = state.currentLocation,
                        onStationSelected = { selectedStation = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    FloatingActionButton(
                        onClick = { if (!isLocating) requestLocation() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = STATION_MAP_LOCATION_ACTION_BOTTOM_PADDING)
                    ) {
                        if (isLocating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = "Usar mi ubicación")
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
        SnackbarHost(hostState = snackbarHostState)
    }

    if (showFilters) {
        StationFiltersSheet(
            filter = state.filter,
            provinces = provinces,
            viewModel = viewModel,
            canSortByDistance = canSortStationsByDistance(state.currentLocation),
            onApply = { filter ->
                viewModel.applyFilter(filter)
                showFilters = false
            },
            onReset = {
                viewModel.resetFilters()
                showFilters = false
            },
            onRequestLocation = ::requestLocation,
            onClearCache = viewModel::clearCache,
            onDismiss = { showFilters = false }
        )
    }

    selectedStation?.let { station ->
        ModalBottomSheet(onDismissRequest = { selectedStation = null }) {
            StationDetailsSheet(
                station = station,
                onNavigate = {
                    val intent = createStationNavigationIntent(station)
                    if (intent?.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(stationNavigationUnavailableMessage())
                        }
                    }
                }
            )
        }
    }
}

/** Selection belongs only to the current filtered result and remains ephemeral. */
internal fun selectedStationAfterFiltering(
    selectedStation: StationListItem?,
    visibleStations: List<StationListItem>
): StationListItem? = selectedStation?.let { selected ->
    visibleStations.firstOrNull { it.externalId == selected.externalId }
}

@Composable
private fun CacheStatus(
    hasCache: Boolean,
    isStale: Boolean,
    refreshing: Boolean,
    error: String?,
    downloadedAtMillis: Long?
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
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StationToolbar(
    viewMode: StationsViewMode,
    hasActiveFilters: Boolean,
    filterSummary: String,
    onViewModeSelected: (StationsViewMode) -> Unit,
    onFiltersClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ViewModeSelector(
            selected = viewMode,
            onSelected = onViewModeSelected,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onFiltersClick) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filtros de estaciones",
                tint = if (hasActiveFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
    Text(
        filterSummary,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ViewModeSelector(
    selected: StationsViewMode,
    onSelected: (StationsViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        if (selected == StationsViewMode.LIST) {
            Button(onClick = { onSelected(StationsViewMode.LIST) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
                Text("Lista", modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            OutlinedButton(onClick = { onSelected(StationsViewMode.LIST) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null)
                Text("Lista", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (selected == StationsViewMode.MAP) {
            Button(onClick = { onSelected(StationsViewMode.MAP) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null)
                Text("Mapa", modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            OutlinedButton(onClick = { onSelected(StationsViewMode.MAP) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Map, contentDescription = null)
                Text("Mapa", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationFiltersSheet(
    filter: StationFilter,
    provinces: List<String>,
    viewModel: StationsViewModel,
    canSortByDistance: Boolean,
    onApply: (StationFilter) -> Unit,
    onReset: () -> Unit,
    onRequestLocation: () -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFuel by remember(filter) { mutableStateOf(filter.fuelType) }
    var selectedProvince by remember(filter) { mutableStateOf(filter.province) }
    var selectedMunicipality by remember(filter) { mutableStateOf(filter.municipality) }
    var selectedSort by remember(filter) { mutableStateOf(filter.sortOrder) }
    val municipalities by remember(selectedProvince) { viewModel.observeMunicipalities(selectedProvince) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Filtros de estaciones", style = MaterialTheme.typography.titleLarge)
            FilterSelectionMenu(
                label = "Combustible",
                selectedLabel = selectedFuel.displayName,
                options = StationFuelType.entries.map { it.displayName to it.name },
                onSelected = { selectedFuel = StationFuelType.valueOf(it) }
            )
            FilterSelectionMenu(
                label = "Provincia",
                selectedLabel = selectedProvince ?: "Todas las provincias",
                options = listOf("Todas las provincias" to "") + provinces.map { it to it },
                onSelected = { selectedProvince = it.ifBlank { null }; selectedMunicipality = null }
            )
            FilterSelectionMenu(
                label = "Municipio",
                selectedLabel = selectedMunicipality ?: "Todos los municipios",
                options = listOf("Todos los municipios" to "") + municipalities.map { it to it },
                onSelected = { selectedMunicipality = it.ifBlank { null } }
            )
            SortMenu(
                sortOrder = selectedSort,
                canSortByDistance = canSortByDistance,
                onSelected = { selectedSort = it }
            )
            if (!canSortByDistance) {
                TextButton(onClick = onRequestLocation) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Text("Usar mi ubicación", modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    "Kilonom usa tu ubicación solo para calcular distancias y mostrar estaciones cercanas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Restablecer") }
                Button(
                    onClick = {
                        onApply(
                            StationFilter(
                                fuelType = selectedFuel,
                                province = selectedProvince,
                                municipality = selectedMunicipality,
                                sortOrder = selectedSort
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Aplicar") }
            }
            HorizontalDivider()
            TextButton(onClick = onClearCache, modifier = Modifier.align(Alignment.End)) { Text("Borrar caché local") }
        }
    }
}

@Composable
private fun SortMenu(
    sortOrder: StationSortOrder,
    canSortByDistance: Boolean,
    onSelected: (StationSortOrder) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded.value = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Orden: ${sortOrder.displayName}", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            DropdownMenuItem(text = { Text(StationSortOrder.PRICE.displayName) }, onClick = {
                expanded.value = false
                onSelected(StationSortOrder.PRICE)
            })
            DropdownMenuItem(
                text = { Text(StationSortOrder.DISTANCE.displayName) },
                enabled = canSortByDistance,
                onClick = {
                    expanded.value = false
                    onSelected(StationSortOrder.DISTANCE)
                }
            )
        }
    }
}

@Composable
private fun FilterSelectionMenu(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded.value = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $selectedLabel", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            options.forEach { (name, value) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { expanded.value = false; onSelected(value) })
            }
        }
    }
}

@Composable
private fun StationList(stations: List<StationListItem>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = modifier
    ) {
        items(stations, key = { it.externalId }) { StationCard(it) }
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
            item.distanceMeters?.let {
                Text("Aprox. ${it.toDisplayDistance()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.sourceUpdatedAtMillis?.let {
                Text("Actualizado: ${it.toDisplayDateTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StationDetailsSheet(station: StationListItem, onNavigate: () -> Unit) {
    val presentation = station.toDetailsPresentation()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(presentation.name, style = MaterialTheme.typography.titleLarge)
        presentation.address?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(presentation.price)
        presentation.schedule?.let { Text("Horario: $it") }
        presentation.distance?.let { Text("Distancia aproximada: $it") }
        presentation.updatedAt?.let { Text("Actualizado: $it") }
        Button(onClick = onNavigate, modifier = Modifier.fillMaxWidth()) { Text("Cómo llegar") }
    }
}

internal data class StationDetailsPresentation(
    val name: String,
    val address: String?,
    val price: String,
    val schedule: String?,
    val distance: String?,
    val updatedAt: String?
)

internal fun StationListItem.toDetailsPresentation(): StationDetailsPresentation = StationDetailsPresentation(
    name = name,
    address = listOf(address, municipality, province).filter(String::isNotBlank).joinToString(" · ").ifBlank { null },
    price = "$productName: ${"%.3f".format(java.util.Locale.forLanguageTag("es-ES"), price)} €",
    schedule = schedule?.takeIf(String::isNotBlank),
    distance = distanceMeters?.let(Double::toDisplayDistance),
    updatedAt = sourceUpdatedAtMillis?.toDisplayDateTime()
)

internal fun Double.toDisplayDistance(): String = if (this < 1_000) "${toInt()} m" else String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f km", this / 1_000)

internal fun stationNavigationUnavailableMessage() =
    "No se ha encontrado una aplicación de navegación compatible."

private val STATION_MAP_LOCATION_ACTION_BOTTOM_PADDING = 16.dp

private fun Long.toDisplayDateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
    java.util.Locale.forLanguageTag("es-ES")
).format(Date(this))
