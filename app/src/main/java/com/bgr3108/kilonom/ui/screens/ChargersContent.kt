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
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
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
import com.bgr3108.kilonom.chargers.ChargerConnectorType
import com.bgr3108.kilonom.chargers.ChargerFilter
import com.bgr3108.kilonom.chargers.ChargerListItem
import com.bgr3108.kilonom.chargers.ChargerSortOrder
import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.chargers.formatPower
import com.bgr3108.kilonom.chargers.isDefault
import com.bgr3108.kilonom.chargers.summary
import com.bgr3108.kilonom.chargers.toPresentationConnectorNames
import com.bgr3108.kilonom.stations.StationLocationProvider
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.stations.StationsViewMode
import com.bgr3108.kilonom.stations.canSortStationsByDistance
import com.bgr3108.kilonom.stations.createChargerNavigationIntent
import com.bgr3108.kilonom.stations.locationPermissionGranted
import com.bgr3108.kilonom.util.openExternalUrl
import com.bgr3108.kilonom.util.ExternalLinks
import com.bgr3108.kilonom.viewmodel.StationsViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChargersContent(innerPadding: PaddingValues, viewModel: StationsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chargerState by viewModel.chargerUiState.collectAsStateWithLifecycle()
    val mapChargers by viewModel.chargers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locationPermissionPermanentlyDenied = StationLocationProvider.isPermissionPermanentlyDenied(
        context, state.locationPermissionRequested
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    var selectedCharger by remember { mutableStateOf<ChargerListItem?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var useLocationAsSearchScope by remember { mutableStateOf(false) }
    LaunchedEffect(mapChargers) {
        selectedCharger = selectedCharger?.let { selected -> mapChargers.firstOrNull { it.externalId == selected.externalId } }
    }
    fun obtainLocation() {
        if (isLocating) return
        scope.launch {
            isLocating = true
            try {
                val location = StationLocationProvider.requestCurrentLocation(context)
                viewModel.updateCurrentLocation(location)
                if (location != null && useLocationAsSearchScope) viewModel.useNearbyChargers()
                if (location == null) snackbarHostState.showSnackbar("No se ha podido obtener tu ubicación.")
            } finally { isLocating = false }
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (locationPermissionGranted(permissions)) obtainLocation()
    }
    fun requestLocation(useNearby: Boolean = false) {
        useLocationAsSearchScope = useNearby
        if (StationLocationProvider.hasPermission(context)) obtainLocation()
        else {
            viewModel.markLocationPermissionRequested()
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StationsContentSelector(StationsContentType.CHARGERS, viewModel::selectContentType)
        ChargerCacheStatus(
            hasCache = chargerState.hasCache,
            stale = chargerState.metadata?.let { viewModel.isChargerCacheStale(it) } ?: true,
            refreshing = chargerState.refreshing,
            error = chargerState.error,
            downloadedAt = chargerState.metadata?.downloadedAtMillis
        )
        if (chargerState.hasCache && chargerState.searchScope !is StationSearchScope.None) {
            ChargerToolbar(
                viewMode = state.viewMode,
                activeFilters = !chargerState.filter.isDefault(),
                summary = if (chargerState.searchScope is StationSearchScope.Nearby) {
                    "Cerca de mí · ${chargerState.filter.connectorType?.displayName ?: "Todos los conectores"}"
                } else chargerState.filter.summary(),
                onViewMode = viewModel::setViewMode,
                onFilters = { showFilters = true }
            )
            when {
                state.viewMode == StationsViewMode.LIST && chargerState.filter.sortOrder != ChargerSortOrder.DISTANCE -> {
                    val pagedChargers = viewModel.chargerPaging.collectAsLazyPagingItems()
                    when {
                        pagedChargers.loadState.refresh is LoadState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                        )
                        pagedChargers.itemCount == 0 -> EmptyChargersMessage()
                        else -> PagedChargerList(pagedChargers, Modifier.weight(1f))
                    }
                }
                state.viewMode == StationsViewMode.LIST -> {
                    if (mapChargers.isEmpty()) EmptyChargersMessage() else ChargerList(mapChargers, Modifier.weight(1f))
                }
                mapChargers.isEmpty() -> EmptyChargersMessage()
                else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    ChargerMap(
                        mapChargers,
                        state.currentLocation.takeIf { chargerState.searchScope is StationSearchScope.Nearby },
                        { selectedCharger = it },
                        Modifier.fillMaxSize()
                    )
                    FloatingActionButton(
                        onClick = { if (!isLocating) requestLocation() },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        if (isLocating) CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.MyLocation, contentDescription = "Usar mi ubicación")
                    }
                }
            }
        } else if (chargerState.hasCache) {
            SearchStartPanel(
                onRequestLocation = { requestLocation(useNearby = true) },
                onChooseZone = { showFilters = true },
                locationPermissionPermanentlyDenied = locationPermissionPermanentlyDenied,
                onOpenLocationSettings = {
                    context.startActivity(StationLocationProvider.createAppLocationSettingsIntent(context))
                }
            )
        } else if (!chargerState.refreshing) {
            Text("Necesitas conexión para cargar los puntos de recarga por primera vez.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        }
        SnackbarHost(snackbarHostState)
    }
    if (showFilters) {
        val provinces by viewModel.chargerProvinces.collectAsStateWithLifecycle()
        val operators by viewModel.chargerOperators.collectAsStateWithLifecycle()
        ChargerFiltersSheet(
            filter = chargerState.filter,
            provinces = provinces,
            operators = operators,
            canSortByDistance = canSortStationsByDistance(state.currentLocation),
            municipalities = { province -> viewModel.observeChargerMunicipalities(province) },
            onApply = { viewModel.applyChargerFilter(it); showFilters = false },
            onReset = { viewModel.resetChargerFilters(); showFilters = false },
            onRequestLocation = { requestLocation(useNearby = true) },
            onUseNearby = viewModel::useNearbyChargers,
            onClearCache = viewModel::clearChargerCache,
            onDismiss = { showFilters = false }
        )
    }
    selectedCharger?.let { charger ->
        ModalBottomSheet(onDismissRequest = { selectedCharger = null }) {
            ChargerDetailsSheet(charger) {
                val intent = createChargerNavigationIntent(charger)
                if (intent?.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                else scope.launch { snackbarHostState.showSnackbar(stationNavigationUnavailableMessage()) }
            }
        }
    }
}

@Composable
private fun ChargerCacheStatus(hasCache: Boolean, stale: Boolean, refreshing: Boolean, error: String?, downloadedAt: Long?) {
    val text = when {
        refreshing && !hasCache -> "Cargando puntos de recarga…"
        error != null && hasCache -> "No se pudieron actualizar los datos. Mostrando última información disponible."
        error != null -> "Necesitas conexión para cargar los puntos de recarga por primera vez."
        stale -> "Hay una actualización de puntos de recarga disponible."
        hasCache -> "Datos guardados: ${downloadedAt?.toChargerDisplayDateTime().orEmpty()}"
        else -> ""
    }
    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChargerToolbar(viewMode: StationsViewMode, activeFilters: Boolean, summary: String, onViewMode: (StationsViewMode) -> Unit, onFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ChargerViewModeSelector(viewMode, onViewMode, Modifier.weight(1f))
        IconButton(onClick = onFilters) {
            Icon(Icons.Default.FilterList, contentDescription = "Filtros de cargadores", tint = if (activeFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
    Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChargerViewModeSelector(selected: StationsViewMode, onSelected: (StationsViewMode) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected == StationsViewMode.LIST) Button({ onSelected(StationsViewMode.LIST) }, Modifier.weight(1f)) { Text("Lista") }
        else OutlinedButton({ onSelected(StationsViewMode.LIST) }, Modifier.weight(1f)) { Text("Lista") }
        if (selected == StationsViewMode.MAP) Button({ onSelected(StationsViewMode.MAP) }, Modifier.weight(1f)) { Text("Mapa") }
        else OutlinedButton({ onSelected(StationsViewMode.MAP) }, Modifier.weight(1f)) { Text("Mapa") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargerFiltersSheet(
    filter: ChargerFilter,
    provinces: List<String>,
    operators: List<String>,
    canSortByDistance: Boolean,
    municipalities: (String?) -> kotlinx.coroutines.flow.Flow<List<String>>,
    onApply: (ChargerFilter) -> Unit,
    onReset: () -> Unit,
    onRequestLocation: () -> Unit,
    onUseNearby: () -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    var connector by remember(filter) { mutableStateOf(filter.connectorType) }
    var power by remember(filter) { mutableStateOf(filter.minimumPowerKw) }
    var operator by remember(filter) { mutableStateOf(filter.operatorName) }
    var province by remember(filter) { mutableStateOf(filter.province) }
    var municipality by remember(filter) { mutableStateOf(filter.municipality) }
    var sort by remember(filter) { mutableStateOf(filter.sortOrder) }
    var zoneError by remember { mutableStateOf(false) }
    val municipalityOptions by remember(province) { municipalities(province) }.collectAsStateWithLifecycle(emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Filtros de cargadores", style = MaterialTheme.typography.titleLarge)
            ChargerChoiceMenu("Conector", connector?.displayName ?: "Todos", listOf("Todos" to "") + ChargerConnectorType.entries.map { it.displayName to it.name }) { connector = it.ifBlank { null }?.let(ChargerConnectorType::valueOf) }
            ChargerChoiceMenu("Potencia mínima", power?.let { "≥ ${it.formatPower()} kW" } ?: "Todas", listOf("Todas" to "") + listOf(11.0, 22.0, 50.0, 100.0, 150.0).map { "≥ ${it.formatPower()} kW" to it.toString() }) { power = it.toDoubleOrNull() }
            ChargerChoiceMenu("Operador", operator ?: "Todos los operadores", listOf("Todos los operadores" to "") + operators.map { it to it }) { operator = it.ifBlank { null } }
            ChargerChoiceMenu("Provincia", province ?: "Todas las provincias", listOf("Todas las provincias" to "") + provinces.map { it to it }) { province = it.ifBlank { null }; municipality = null }
            if (zoneError) Text(
                "Elige una provincia o usa tu ubicación para empezar a buscar.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            ChargerChoiceMenu("Municipio", municipality ?: "Todos los municipios", listOf("Todos los municipios" to "") + municipalityOptions.map { it to it }) { municipality = it.ifBlank { null } }
            ChargerSortMenu(sort, canSortByDistance) { sort = it }
            if (!canSortByDistance) TextButton(onClick = onRequestLocation) { Icon(Icons.Default.MyLocation, null); Text("Usar mi ubicación", Modifier.padding(start = 8.dp)) }
            else TextButton(onClick = { onUseNearby(); onDismiss() }) { Icon(Icons.Default.MyLocation, null); Text("Usar mi ubicación", Modifier.padding(start = 8.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onReset, Modifier.weight(1f)) { Text("Restablecer") }
                Button(onClick = {
                    if (province == null && !canSortByDistance) {
                        zoneError = true
                    } else {
                        onApply(ChargerFilter(connector, power, operator, province, municipality, sort))
                    }
                }, modifier = Modifier.weight(1f)) { Text("Aplicar") }
            }
            HorizontalDivider()
            TextButton(onClick = onClearCache, modifier = Modifier.align(Alignment.End)) { Text("Borrar caché local") }
        }
    }
}

@Composable
private fun ChargerChoiceMenu(label: String, selected: String, options: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("$label: $selected", Modifier.weight(1f)) }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { (title, value) -> DropdownMenuItem({ Text(title) }, { expanded = false; onSelected(value) }) } }
    }
}

@Composable
private fun ChargerSortMenu(sort: ChargerSortOrder, canSortByDistance: Boolean, onSelected: (ChargerSortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("Orden: ${sort.displayName}", Modifier.weight(1f)) }
        DropdownMenu(expanded, { expanded = false }) { ChargerSortOrder.entries.forEach { option ->
            DropdownMenuItem({ Text(option.displayName) }, { expanded = false; onSelected(option) }, enabled = option != ChargerSortOrder.DISTANCE || canSortByDistance)
        } }
    }
}

@Composable
private fun ChargerList(chargers: List<ChargerListItem>, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(chargers, key = { it.externalId }) { ChargerCard(it) }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalDivider()
                Text("Datos de recarga: MITECO / RIPREE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { context.openExternalUrl(ExternalLinks.MITECO_CHARGERS_URL) }) { Text("Ver fuente de datos") }
            }
        }
    }
}

@Composable
private fun PagedChargerList(chargers: LazyPagingItems<ChargerListItem>, modifier: Modifier) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(count = chargers.itemCount, key = chargers.itemKey { it.externalId }) { index ->
            chargers[index]?.let { ChargerCard(it) }
        }
        chargerSourceFooter(context)
    }
}

@Composable
private fun EmptyChargersMessage() {
    Text(
        "No hay puntos de recarga que coincidan con los filtros seleccionados.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.chargerSourceFooter(context: android.content.Context) {
    item {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider()
            Text("Datos de recarga: MITECO / RIPREE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { context.openExternalUrl(ExternalLinks.MITECO_CHARGERS_URL) }) { Text("Ver fuente de datos") }
        }
    }
}

@Composable
private fun ChargerCard(item: ChargerListItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            item.operatorName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(listOf(item.address, item.municipality, item.province).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.maxPowerKw?.let { Text("Hasta ${it.formatPower()} kW", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
            Text(item.connectorTypes.toPresentationConnectorNames().joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.distanceMeters?.let { Text("Aprox. ${it.toDisplayDistance()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChargerDetailsSheet(charger: ChargerListItem, onNavigate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(charger.name, style = MaterialTheme.typography.titleLarge)
        charger.operatorName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        listOf(charger.address, charger.municipality, charger.province).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        charger.maxPowerKw?.let { Text("Potencia máxima: ${it.formatPower()} kW") }
        charger.connectorTypes.toPresentationConnectorNames().takeIf(List<String>::isNotEmpty)?.let { Text("Conectores: ${it.joinToString()}") }
        charger.distanceMeters?.let { Text("Distancia aproximada: ${it.toDisplayDistance()}") }
        charger.sourceUpdatedAtMillis?.let { Text("Actualizado: ${it.toChargerDisplayDateTime()}", style = MaterialTheme.typography.bodySmall) }
        Text("Consulta la app o web del operador para disponibilidad, tarifas o iniciar la recarga.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onNavigate, Modifier.fillMaxWidth()) { Text("Cómo llegar") }
        // RIPREE's inspected export contains no official operator URL, so no speculative link is shown.
    }
}

private fun Long.toChargerDisplayDateTime(): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, java.util.Locale.forLanguageTag("es-ES")).format(Date(this))
