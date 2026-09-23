package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.bgr3108.kilonom.stations.StationListItem
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
    private val locationIntroVisible = MutableStateFlow(false)
    private val locationPermissionRequested = MutableStateFlow(false)
    private var stationEntryHandled = false

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

    /** Nearby and map results are built from the same scope and current filters. */
    val stations: StateFlow<List<StationListItem>> = combine(contentType, filter, currentLocation, viewMode, stationSearchScope) {
            type, activeFilter, location, mode, scope -> SearchInput(type, activeFilter, location, mode, scope)
    }.flatMapLatest { input ->
        if (!shouldLoadFuelResults(input.contentType, input.scope)) return@flatMapLatest flowOf(emptyList())
        when (val scope = input.scope) {
            is StationSearchScope.Nearby -> repository.observeNearbyStations(input.filter, scope.origin)
            is StationSearchScope.ManualZone -> if (input.mode == StationsViewMode.MAP || input.filter.sortOrder == StationSortOrder.DISTANCE) {
                repository.observeStations(input.filter, input.location)
            } else flowOf(emptyList())
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            is StationSearchScope.Nearby -> chargerRepository.observeNearbyChargers(input.filter, scope.origin)
            is StationSearchScope.ManualZone -> if (input.mode == StationsViewMode.MAP || input.filter.sortOrder == ChargerSortOrder.DISTANCE) {
                chargerRepository.observeChargers(input.filter, input.location)
            } else flowOf(emptyList())
            StationSearchScope.None -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationsUiState())

    val chargerUiState: StateFlow<ChargerUiState> = combine(
        chargerFilter, chargerMetadata, chargerRefreshing, chargerRefreshError, chargerSearchScope
    ) { activeFilter, cache, isRefreshing, error, scope ->
        ChargerUiState(activeFilter, cache, isRefreshing, error, scope)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargerUiState())

    val activeContentRefreshing: StateFlow<Boolean> = combine(contentType, refreshing, chargerRefreshing) {
            type, fuelRefreshing, chargersRefreshing ->
        if (type == StationsContentType.CHARGERS) chargersRefreshing else fuelRefreshing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { viewModelScope.launch { if (!repository.hasCache()) refresh() } }

    fun onStationsOpened() {
        if (stationEntryHandled) return
        stationEntryHandled = true
        viewModelScope.launch {
            locationIntroVisible.value = !stationPreferences.hasSeenLocationIntro()
            locationPermissionRequested.value = stationPreferences.hasRequestedLocationPermission()
        }
    }
    fun dismissLocationIntro() {
        locationIntroVisible.value = false
        viewModelScope.launch { stationPreferences.markLocationIntroSeen() }
    }
    fun markLocationPermissionRequested() {
        locationPermissionRequested.value = true
        viewModelScope.launch { stationPreferences.markLocationPermissionRequested() }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            repository.refresh().onFailure { refreshError.value = it.message }
            refreshing.value = false
        }
    }
    fun clearCache() = viewModelScope.launch { repository.clearCache(); refreshError.value = null }

    fun selectContentType(type: StationsContentType) {
        contentType.value = type
        if (type == StationsContentType.CHARGERS) viewModelScope.launch {
            val cache = chargerRepository.getMetadata()
            val stale = cache == null || chargerRepository.isStale(cache)
            if (stale) refreshChargersInternal()
        }
    }
    fun refreshChargers() = viewModelScope.launch { refreshChargersInternal() }
    fun refreshActiveContent() { if (contentType.value == StationsContentType.CHARGERS) refreshChargers() else refresh() }
    private suspend fun refreshChargersInternal() {
        if (chargerRefreshing.value) return
        chargerRefreshing.value = true
        chargerRefreshError.value = null
        chargerRepository.refresh().onFailure { chargerRefreshError.value = it.message }
        chargerRefreshing.value = false
    }
    fun clearChargerCache() = viewModelScope.launch { chargerRepository.clearCache(); chargerRefreshError.value = null }
    fun isChargerCacheStale(metadata: ChargerCacheMetadataEntity?): Boolean = chargerRepository.isStale(metadata)

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
    fun setViewMode(mode: StationsViewMode) { viewMode.value = mode }

    /** Position remains in this ViewModel only. Coarse is sufficient and enables nearby mode. */
    fun updateCurrentLocation(location: StationCoordinates?) {
        currentLocation.value = location?.takeIf { it.isValid() }
        if (currentLocation.value != null) {
            if (filter.value.province == null) filter.value = filter.value.copy(sortOrder = StationSortOrder.DISTANCE)
            if (chargerFilter.value.province == null) chargerFilter.value = chargerFilter.value.copy(sortOrder = ChargerSortOrder.DISTANCE)
        }
    }
}

private data class SearchInput(val contentType: StationsContentType, val filter: StationFilter, val location: StationCoordinates?, val mode: StationsViewMode, val scope: StationSearchScope)
private data class StationPagingInput(val contentType: StationsContentType, val filter: StationFilter, val scope: StationSearchScope)
private data class ChargerSearchInput(val contentType: StationsContentType, val filter: ChargerFilter, val location: StationCoordinates?, val mode: StationsViewMode, val scope: StationSearchScope)
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

data class ChargerUiState(
    val filter: ChargerFilter = ChargerFilter(), val metadata: ChargerCacheMetadataEntity? = null,
    val refreshing: Boolean = false, val error: String? = null,
    val searchScope: StationSearchScope = StationSearchScope.None
) { val hasCache: Boolean get() = metadata != null }

data class StationsUiState(
    val filter: StationFilter = StationFilter(), val metadata: StationCacheMetadataEntity? = null,
    val isRefreshing: Boolean = false, val refreshError: String? = null,
    val currentLocation: StationCoordinates? = null, val viewMode: StationsViewMode = StationsViewMode.LIST,
    val contentType: StationsContentType = StationsContentType.FUEL,
    val searchScope: StationSearchScope = StationSearchScope.None,
    val showLocationIntro: Boolean = false,
    val locationPermissionRequested: Boolean = false
) {
    val hasCache: Boolean get() = metadata != null
    val isStale: Boolean get() = StationRepository.isStale(metadata)
}
