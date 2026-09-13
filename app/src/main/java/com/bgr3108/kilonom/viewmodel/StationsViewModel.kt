package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationSortOrder
import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationRepository
import com.bgr3108.kilonom.stations.StationsViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class StationsViewModel(private val repository: StationRepository) : ViewModel() {
    private val filter = MutableStateFlow(StationFilter())
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val currentLocation = MutableStateFlow<StationCoordinates?>(null)
    private val viewMode = MutableStateFlow(StationsViewMode.LIST)

    val metadata: StateFlow<StationCacheMetadataEntity?> = repository.observeMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val provinces: StateFlow<List<String>> = repository.observeProvinces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observeMunicipalities(province: String?): Flow<List<String>> = repository.observeMunicipalities(province)
    val stations: StateFlow<List<StationListItem>> = combine(filter, currentLocation) { filterValue, location ->
        filterValue to location
    }
        .flatMapLatest { (filterValue, location) -> repository.observeStations(filterValue, location) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val baseUiState = combine(
        filter, metadata, refreshing, refreshError, currentLocation
    ) { filterValue, cache, isRefreshing, error, location ->
        StationsUiState(filterValue, cache, isRefreshing, error, location)
    }
    val uiState: StateFlow<StationsUiState> = combine(baseUiState, viewMode) { state, mode ->
        state.copy(viewMode = mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StationsUiState())

    init {
        viewModelScope.launch {
            if (!repository.hasCache()) refresh()
        }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            repository.refresh()
                .onFailure { refreshError.value = it.message }
            refreshing.value = false
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            refreshError.value = null
        }
    }

    fun applyFilter(newFilter: StationFilter) {
        filter.value = if (newFilter.sortOrder == StationSortOrder.DISTANCE && currentLocation.value == null) {
            newFilter.copy(sortOrder = StationSortOrder.PRICE)
        } else {
            newFilter
        }
    }

    fun resetFilters() {
        filter.value = StationFilter()
    }

    fun setViewMode(mode: StationsViewMode) {
        viewMode.value = mode
    }

    /** The position remains only in this ViewModel while the feature is open. */
    fun updateCurrentLocation(location: StationCoordinates?) {
        currentLocation.value = location?.takeIf { it.isValid() }
    }
}

data class StationsUiState(
    val filter: StationFilter = StationFilter(),
    val metadata: StationCacheMetadataEntity? = null,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val currentLocation: StationCoordinates? = null,
    val viewMode: StationsViewMode = StationsViewMode.LIST
) {
    val hasCache: Boolean get() = metadata != null
    val isStale: Boolean get() = StationRepository.isStale(metadata)
}
