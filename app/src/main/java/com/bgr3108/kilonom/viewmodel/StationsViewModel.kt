package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.database.sqlite.SQLiteException
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.bgr3108.kilonom.chargers.ChargerCacheMetadataEntity
import com.bgr3108.kilonom.chargers.ChargerFilter
import com.bgr3108.kilonom.chargers.ChargerListItem
import com.bgr3108.kilonom.chargers.ChargerRepository
import com.bgr3108.kilonom.chargers.ChargerSortOrder
import com.bgr3108.kilonom.chargers.normalizeChargerFilterForScope
import com.bgr3108.kilonom.chargers.StationsContentType
import com.bgr3108.kilonom.chargers.searchScope as chargerSearchScope
import com.bgr3108.kilonom.stations.NoOpStationPreferences
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelPrice
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationMapBounds
import com.bgr3108.kilonom.stations.StationPreferencesStore
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.StationSearchScope
import com.bgr3108.kilonom.stations.StationSortOrder
import com.bgr3108.kilonom.stations.StationsViewMode
import com.bgr3108.kilonom.stations.normalizeStationFilterForScope
import com.bgr3108.kilonom.stations.searchScope as stationSearchScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import org.json.JSONException

/** Shared search scope for List and Map. There is deliberately no national default query. */
@OptIn(ExperimentalCoroutinesApi::class)
class StationsViewModel(
    private val repository: StationRepository,
    private val chargerRepository: ChargerRepository,
    private val stationPreferences: StationPreferencesStore = NoOpStationPreferences
) : ViewModel() {
    private val filter = MutableStateFlow(StationFilter())
    private val chargerFilter = MutableStateFlow(ChargerFilter())
    private val currentLocation = MutableStateFlow<StationCoordinates?>(null)
    private val viewMode = MutableStateFlow(StationsViewMode.LIST)
    private val contentType = MutableStateFlow(StationsContentType.FUEL)
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val chargerRefreshing = MutableStateFlow(false)
    private val chargerRefreshError = MutableStateFlow<String?>(null)
    private val refreshSucceeded = MutableStateFlow(false)
    private val chargerRefreshSucceeded = MutableStateFlow(false)
    private val stationMapViewport = MutableStateFlow<StationMapBounds?>(null)
    private val chargerMapViewport = MutableStateFlow<StationMapBounds?>(null)
    private val locationIntroVisible = MutableStateFlow(false)
    private val locationPermissionRequested = MutableStateFlow(false)
    private var stationEntryHandled = false
    private val preferenceMutex = Mutex()
    private val _persistenceError = MutableStateFlow<String?>(null)
    val persistenceError: StateFlow<String?> = _persistenceError
    private var persistenceRetry: (() -> Unit)? = null
    private val _fuelPricesRetry = MutableStateFlow(0L)
    val fuelPricesRetry: StateFlow<Long> = _fuelPricesRetry
    private val fuelStationsRefreshCoordinator = StationsRefreshCoordinator(
        metadataProvider = repository::getMetadata,
        refresh = ::refreshFuelStationsInternal,
        isStale = StationRepository::isStale
    )
    private val chargerRefreshCoordinator = StationsRefreshCoordinator(
        metadataProvider = chargerRepository::getMetadata,
        refresh = ::refreshChargersInternal,
        isStale = chargerRepository::isStale
    )

    private val stationSearchScope = combine(filter, currentLocation) { activeFilter, location ->
        activeFilter.stationSearchScope(location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationSearchScope.None)
    private val chargerSearchScope = combine(chargerFilter, currentLocation) { activeFilter, location ->
        activeFilter.chargerSearchScope(location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationSearchScope.None)

    val metadata: StateFlow<StationCacheMetadataEntity?> = repository.observeMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val provinces: StateFlow<List<String>> = repository.observeProvinces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observeMunicipalities(province: String?): Flow<List<String>> = repository.observeMunicipalities(province)

    /** Paging exists only for a selected province; no PagingSource is ever national by default. */
    val stationPaging: Flow<PagingData<StationListItem>> = combine(contentType, filter, stationSearchScope) { type, activeFilter, scope ->
        StationPagingInput(type, activeFilter, scope)
    }.flatMapLatest { input ->
        if (shouldLoadFuelResults(input.contentType, input.scope) && input.scope is StationSearchScope.ManualZone) {
            repository.pagingStations(input.filter)
        }
        else flowOf(PagingData.empty())
    }.cachedIn(viewModelScope)

    /** Nearby and manual distance lists keep their existing non-paged behaviour. */
    val stations: StateFlow<List<StationListItem>> = combine(contentType, filter, currentLocation, viewMode, stationSearchScope) {
            type, activeFilter, location, mode, scope -> SearchInput(type, activeFilter, location, mode, scope)
    }.flatMapLatest { input ->
        if (!shouldLoadFuelResults(input.contentType, input.scope)) return@flatMapLatest flowOf(emptyList())
        when (val scope = input.scope) {
            is StationSearchScope.Nearby -> if (input.mode == StationsViewMode.LIST) {
                repository.observeNearbyStations(input.filter, scope.origin)
            } else flowOf(emptyList())
            is StationSearchScope.ManualZone -> if (input.filter.sortOrder == StationSortOrder.DISTANCE) {
                repository.observeStations(input.filter, input.location)
            } else flowOf(emptyList())
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Map data is independent from Paging and starts only after MapLibre reports a valid viewport. */
    val mapStations: StateFlow<List<StationListItem>> = combine(
        combine(contentType, filter, currentLocation, viewMode, stationSearchScope) { type, activeFilter, location, mode, scope ->
            SearchInput(type, activeFilter, location, mode, scope)
        },
        stationMapViewport
    ) { input, viewport ->
        StationMapSearchInput(input.contentType, input.filter, input.location, input.mode, input.scope, viewport)
    }.flatMapLatest { input ->
        if (!shouldLoadFuelMapResults(input.contentType, input.scope, input.mode, input.viewport)) {
            return@flatMapLatest flowOf(emptyList())
        }
        val viewport = requireNotNull(input.viewport)
        when (val scope = input.scope) {
            is StationSearchScope.ManualZone -> repository.observeStationsForMap(input.filter, viewport, input.location)
            is StationSearchScope.Nearby -> repository.observeNearbyStationsForMap(input.filter, scope.origin, viewport)
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Manual scopes are framed from one aggregate row before MapLibre emits its first viewport. */
    val stationMapInitialBounds: StateFlow<StationMapBounds?> = combine(
        contentType, filter, viewMode, stationSearchScope
    ) { type, activeFilter, mode, scope ->
        SearchInput(type, activeFilter, null, mode, scope)
    }.flatMapLatest { input ->
        if (input.contentType == StationsContentType.FUEL && input.mode == StationsViewMode.MAP &&
            input.scope is StationSearchScope.ManualZone
        ) repository.observeStationMapBounds(input.filter) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val chargerMetadata: StateFlow<ChargerCacheMetadataEntity?> = chargerRepository.observeMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val chargerProvinces: StateFlow<List<String>> = chargerRepository.observeProvinces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chargerOperators: StateFlow<List<String>> = chargerRepository.observeOperators()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observeChargerMunicipalities(province: String?): Flow<List<String>> = chargerRepository.observeMunicipalities(province)

    val chargerPaging: Flow<PagingData<ChargerListItem>> = combine(contentType, chargerFilter, chargerSearchScope) { type, activeFilter, scope ->
        ChargerPagingInput(type, activeFilter, scope)
    }.flatMapLatest { input ->
        if (shouldLoadChargerResults(input.contentType, input.scope) && input.scope is StationSearchScope.ManualZone) {
            chargerRepository.pagingChargers(input.filter)
        }
        else flowOf(PagingData.empty())
    }.cachedIn(viewModelScope)

    val chargers: StateFlow<List<ChargerListItem>> = combine(contentType, chargerFilter, currentLocation, viewMode, chargerSearchScope) {
            type, activeFilter, location, mode, scope -> ChargerSearchInput(type, activeFilter, location, mode, scope)
    }.flatMapLatest { input ->
        if (!shouldLoadChargerResults(input.contentType, input.scope)) return@flatMapLatest flowOf(emptyList())
        when (val scope = input.scope) {
            is StationSearchScope.Nearby -> if (input.mode == StationsViewMode.LIST) {
                chargerRepository.observeNearbyChargers(input.filter, scope.origin)
            } else flowOf(emptyList())
            is StationSearchScope.ManualZone -> if (input.filter.sortOrder == ChargerSortOrder.DISTANCE) {
                chargerRepository.observeChargers(input.filter, input.location)
            } else flowOf(emptyList())
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mapChargers: StateFlow<List<ChargerListItem>> = combine(
        combine(contentType, chargerFilter, currentLocation, viewMode, chargerSearchScope) { type, activeFilter, location, mode, scope ->
            ChargerSearchInput(type, activeFilter, location, mode, scope)
        },
        chargerMapViewport
    ) { input, viewport ->
        ChargerMapSearchInput(input.contentType, input.filter, input.location, input.mode, input.scope, viewport)
    }.flatMapLatest { input ->
        if (!shouldLoadChargerMapResults(input.contentType, input.scope, input.mode, input.viewport)) {
            return@flatMapLatest flowOf(emptyList())
        }
        val viewport = requireNotNull(input.viewport)
        when (val scope = input.scope) {
            is StationSearchScope.ManualZone -> chargerRepository.observeChargersForMap(input.filter, viewport, input.location)
            is StationSearchScope.Nearby -> chargerRepository.observeNearbyChargersForMap(input.filter, scope.origin, viewport)
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chargerMapInitialBounds: StateFlow<StationMapBounds?> = combine(
        contentType, chargerFilter, viewMode, chargerSearchScope
    ) { type, activeFilter, mode, scope ->
        ChargerSearchInput(type, activeFilter, null, mode, scope)
    }.flatMapLatest { input ->
        if (input.contentType == StationsContentType.CHARGERS && input.mode == StationsViewMode.MAP &&
            input.scope is StationSearchScope.ManualZone
        ) chargerRepository.observeChargerMapBounds(input.filter) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val fuelCoreState = combine(filter, metadata, refreshing, refreshError, currentLocation) {
            activeFilter, cache, isRefreshing, error, location ->
        FuelCoreState(activeFilter, cache, isRefreshing, error, location)
    }
    private val stationNavigationState = combine(viewMode, contentType, stationSearchScope, locationIntroVisible, locationPermissionRequested) {
            mode, type, scope, intro, permissionRequested -> StationNavigationState(mode, type, scope, intro, permissionRequested)
    }
    val uiState: StateFlow<StationsUiState> = combine(fuelCoreState, stationNavigationState) { core, navigation ->
        StationsUiState(
            core.filter, core.metadata, core.refreshing, core.error, core.location,
            navigation.viewMode, navigation.contentType, navigation.searchScope, navigation.showLocationIntro,
            navigation.locationPermissionRequested
        )
    }.combine(refreshSucceeded) { state, succeeded -> state.copy(refreshSucceeded = succeeded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationsUiState())

    val chargerUiState: StateFlow<ChargerUiState> = combine(
        chargerFilter, chargerMetadata, chargerRefreshing, chargerRefreshError, chargerSearchScope
    ) { activeFilter, cache, isRefreshing, error, scope ->
        ChargerUiState(activeFilter, cache, isRefreshing, error, scope)
    }.combine(chargerRefreshSucceeded) { state, succeeded -> state.copy(refreshSucceeded = succeeded) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargerUiState())

    val activeContentRefreshing: StateFlow<Boolean> = combine(contentType, refreshing, chargerRefreshing) {
            type, fuelRefreshing, chargersRefreshing ->
        if (type == StationsContentType.CHARGERS) chargersRefreshing else fuelRefreshing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onStationsOpened() {
        if (stationEntryHandled) return
        stationEntryHandled = true
        viewModelScope.launch {
            var preferencesLoaded = false
            try {
                preferencesLoaded = runPersistenceAction(::onStationsOpened) {
                    preferenceMutex.withLock {
                        val seenIntro = stationPreferences.hasSeenLocationIntro()
                        val requestedPermission = stationPreferences.hasRequestedLocationPermission()
                        locationIntroVisible.value = !seenIntro
                        locationPermissionRequested.value = requestedPermission
                    }
                }
                if (preferencesLoaded) runPersistenceAction(::refresh) {
                    fuelStationsRefreshCoordinator.refreshIfNeededOnStationsOpened()
                }
            } finally {
                if (!preferencesLoaded) stationEntryHandled = false
            }
        }
    }
    fun dismissLocationIntro() {
        locationIntroVisible.value = false
        viewModelScope.launch {
            runPersistenceAction(::dismissLocationIntro) {
                preferenceMutex.withLock { stationPreferences.markLocationIntroSeen() }
            }
        }
    }
    fun markLocationPermissionRequested() {
        locationPermissionRequested.value = true
        viewModelScope.launch {
            runPersistenceAction(::markLocationPermissionRequested) {
                preferenceMutex.withLock { stationPreferences.markLocationPermissionRequested() }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { runPersistenceAction(::refresh) { fuelStationsRefreshCoordinator.refreshManually() } }
    }
    fun clearCache(): Job = viewModelScope.launch {
        runPersistenceAction({ clearCache() }, "No se pudo limpiar la caché. Los datos guardados se conservan. Inténtalo de nuevo.") {
            if (fuelStationsRefreshCoordinator.clearCache(repository::clearCache)) {
                refreshError.value = null
                refreshSucceeded.value = false
            }
        }
    }

    fun selectContentType(type: StationsContentType) {
        contentType.value = type
        if (type == StationsContentType.FUEL) stationMapViewport.value = null else chargerMapViewport.value = null
        if (type == StationsContentType.CHARGERS) viewModelScope.launch {
            runPersistenceAction({ refreshChargers() }) { chargerRefreshCoordinator.refreshAutomaticallyIfNeeded() }
        }
    }
    fun refreshChargers(): Job = viewModelScope.launch {
        runPersistenceAction({ refreshChargers() }) { chargerRefreshCoordinator.refreshManually() }
    }
    fun refreshActiveContent() { if (contentType.value == StationsContentType.CHARGERS) refreshChargers() else refresh() }
    private suspend fun refreshFuelStationsInternal() =
        runRefreshWithUiState(refreshing, refreshError, refreshSucceeded, repository::refresh)

    private suspend fun refreshChargersInternal() =
        runRefreshWithUiState(chargerRefreshing, chargerRefreshError, chargerRefreshSucceeded, chargerRepository::refresh)

    fun clearChargerCache(): Job = viewModelScope.launch {
        runPersistenceAction({ clearChargerCache() }, "No se pudo limpiar la caché. Los datos guardados se conservan. Inténtalo de nuevo.") {
            if (chargerRefreshCoordinator.clearCache(chargerRepository::clearCache)) {
                chargerRefreshError.value = null
                chargerRefreshSucceeded.value = false
            }
        }
    }
    fun isChargerCacheStale(metadata: ChargerCacheMetadataEntity?): Boolean = chargerRepository.isStale(metadata)
    suspend fun getVisibleFuelPrices(stationId: String): List<StationFuelPrice>? {
        var prices: List<StationFuelPrice>? = null
        runPersistenceAction({ _fuelPricesRetry.value += 1L }) {
            prices = repository.getVisibleFuelPrices(stationId)
        }
        return prices
    }

    fun retryPersistenceOperation() {
        val retry = persistenceRetry ?: return
        clearPersistenceError()
        retry()
    }

    fun clearPersistenceError() {
        _persistenceError.value = null
        persistenceRetry = null
    }

    private suspend fun runPersistenceAction(
        retry: () -> Unit,
        message: String = "No se pudo acceder a los datos guardados. Inténtalo de nuevo.",
        action: suspend () -> Unit
    ): Boolean = try {
        action()
        true
    } catch (_: IOException) {
        persistenceRetry = retry
        _persistenceError.value = message
        false
    } catch (_: SQLiteException) {
        persistenceRetry = retry
        _persistenceError.value = message
        false
    }

    fun applyFilter(newFilter: StationFilter) {
        val wasNearby = filter.value.province == null && currentLocation.value != null
        filter.value = normalizeStationFilterForScope(newFilter, wasNearby, currentLocation.value != null)
    }
    fun resetFilters() { filter.value = if (currentLocation.value != null) StationFilter(sortOrder = StationSortOrder.DISTANCE) else StationFilter() }
    fun useNearbyStations() { currentLocation.value?.let { filter.value = filter.value.copy(province = null, municipality = null, sortOrder = StationSortOrder.DISTANCE) } }

    fun applyChargerFilter(newFilter: ChargerFilter) {
        val wasNearby = chargerFilter.value.province == null && currentLocation.value != null
        chargerFilter.value = normalizeChargerFilterForScope(newFilter, wasNearby, currentLocation.value != null)
    }
    fun resetChargerFilters() { chargerFilter.value = if (currentLocation.value != null) ChargerFilter(sortOrder = ChargerSortOrder.DISTANCE) else ChargerFilter() }
    fun useNearbyChargers() { currentLocation.value?.let { chargerFilter.value = chargerFilter.value.copy(province = null, municipality = null, sortOrder = ChargerSortOrder.DISTANCE) } }
    fun setViewMode(mode: StationsViewMode) {
        if (mode == StationsViewMode.MAP && viewMode.value != StationsViewMode.MAP) {
            if (contentType.value == StationsContentType.FUEL) stationMapViewport.value = null
            else chargerMapViewport.value = null
        }
        viewMode.value = mode
    }

    fun updateStationMapViewport(bounds: StationMapBounds) {
        if (bounds.isValid()) stationMapViewport.value = bounds
    }

    fun updateChargerMapViewport(bounds: StationMapBounds) {
        if (bounds.isValid()) chargerMapViewport.value = bounds
    }

    /** Position remains in this ViewModel only. Coarse is sufficient and enables nearby mode. */
    fun updateCurrentLocation(location: StationCoordinates?) {
        currentLocation.value = location?.takeIf { it.isValid() }
        if (currentLocation.value != null) {
            if (filter.value.province == null) filter.value = filter.value.copy(sortOrder = StationSortOrder.DISTANCE)
            if (chargerFilter.value.province == null) chargerFilter.value = chargerFilter.value.copy(sortOrder = ChargerSortOrder.DISTANCE)
        }
    }
}

internal suspend fun runRefreshWithUiState(
    refreshing: MutableStateFlow<Boolean>,
    refreshError: MutableStateFlow<String?>,
    succeeded: MutableStateFlow<Boolean>? = null,
    refresh: suspend () -> Result<*>
): Result<*>? {
    if (refreshing.value) return null
    refreshing.value = true
    refreshError.value = null
    succeeded?.value = false
    try {
        return refresh().onFailure { refreshError.value = stationRefreshErrorMessage(it) }
            .onSuccess { succeeded?.value = true }
    } finally {
        refreshing.value = false
    }
}

internal fun stationRefreshErrorMessage(error: Throwable): String = when {
    error.message?.contains("HTTP ") == true -> "El servidor no está disponible. Inténtalo de nuevo más tarde."
    error is IOException -> "No se pudo conectar. Revisa tu conexión e inténtalo de nuevo."
    error is JSONException || error is IllegalArgumentException || error is IllegalStateException &&
        listOf("CSV", "RIPREE", "MITECO", "Descarga").any { error.message.orEmpty().contains(it) } ->
        "Los datos recibidos no son válidos. Inténtalo de nuevo más tarde."
    else -> "No se pudieron actualizar los datos. Inténtalo de nuevo."
}

private data class SearchInput(val contentType: StationsContentType, val filter: StationFilter, val location: StationCoordinates?, val mode: StationsViewMode, val scope: StationSearchScope)
private data class StationMapSearchInput(
    val contentType: StationsContentType,
    val filter: StationFilter,
    val location: StationCoordinates?,
    val mode: StationsViewMode,
    val scope: StationSearchScope,
    val viewport: StationMapBounds?
)
private data class StationPagingInput(val contentType: StationsContentType, val filter: StationFilter, val scope: StationSearchScope)
private data class ChargerSearchInput(val contentType: StationsContentType, val filter: ChargerFilter, val location: StationCoordinates?, val mode: StationsViewMode, val scope: StationSearchScope)
private data class ChargerMapSearchInput(
    val contentType: StationsContentType,
    val filter: ChargerFilter,
    val location: StationCoordinates?,
    val mode: StationsViewMode,
    val scope: StationSearchScope,
    val viewport: StationMapBounds?
)
private data class ChargerPagingInput(val contentType: StationsContentType, val filter: ChargerFilter, val scope: StationSearchScope)
private data class FuelCoreState(
    val filter: StationFilter, val metadata: StationCacheMetadataEntity?, val refreshing: Boolean,
    val error: String?, val location: StationCoordinates?
)
private data class StationNavigationState(
    val viewMode: StationsViewMode, val contentType: StationsContentType,
    val searchScope: StationSearchScope, val showLocationIntro: Boolean,
    val locationPermissionRequested: Boolean
)

internal fun chargerPipelineIsActive(contentType: StationsContentType): Boolean =
    contentType == StationsContentType.CHARGERS

internal fun shouldLoadFuelResults(contentType: StationsContentType, scope: StationSearchScope): Boolean =
    contentType == StationsContentType.FUEL && scope !is StationSearchScope.None

internal fun shouldLoadChargerResults(contentType: StationsContentType, scope: StationSearchScope): Boolean =
    contentType == StationsContentType.CHARGERS && scope !is StationSearchScope.None

internal fun shouldLoadFuelMapResults(
    contentType: StationsContentType,
    scope: StationSearchScope,
    mode: StationsViewMode,
    viewport: StationMapBounds?
): Boolean = shouldLoadFuelResults(contentType, scope) && mode == StationsViewMode.MAP && viewport?.isValid() == true

internal fun shouldLoadChargerMapResults(
    contentType: StationsContentType,
    scope: StationSearchScope,
    mode: StationsViewMode,
    viewport: StationMapBounds?
): Boolean = shouldLoadChargerResults(contentType, scope) && mode == StationsViewMode.MAP && viewport?.isValid() == true

data class ChargerUiState(
    val filter: ChargerFilter = ChargerFilter(), val metadata: ChargerCacheMetadataEntity? = null,
    val refreshing: Boolean = false, val error: String? = null,
    val searchScope: StationSearchScope = StationSearchScope.None,
    val refreshSucceeded: Boolean = false
) { val hasCache: Boolean get() = metadata != null }

data class StationsUiState(
    val filter: StationFilter = StationFilter(), val metadata: StationCacheMetadataEntity? = null,
    val isRefreshing: Boolean = false, val refreshError: String? = null,
    val currentLocation: StationCoordinates? = null, val viewMode: StationsViewMode = StationsViewMode.LIST,
    val contentType: StationsContentType = StationsContentType.FUEL,
    val searchScope: StationSearchScope = StationSearchScope.None,
    val showLocationIntro: Boolean = false,
    val locationPermissionRequested: Boolean = false,
    val refreshSucceeded: Boolean = false
) {
    val hasCache: Boolean get() = metadata != null
    val isStale: Boolean get() = StationRepository.isStale(metadata)
}
