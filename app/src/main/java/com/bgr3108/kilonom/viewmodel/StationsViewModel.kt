package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.stations.StationCacheMetadataEntity
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.stations.StationListItem
import com.bgr3108.kilonom.stations.StationRepository
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

    val metadata: StateFlow<StationCacheMetadataEntity?> = repository.observeMetadata()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val provinces: StateFlow<List<String>> = repository.observeProvinces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val municipalities: StateFlow<List<String>> = filter
        .flatMapLatest { repository.observeMunicipalities(it.province) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stations: StateFlow<List<StationListItem>> = filter
        .flatMapLatest(repository::observeStations)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val uiState: StateFlow<StationsUiState> = combine(
        filter, metadata, refreshing, refreshError
    ) { filterValue, cache, isRefreshing, error ->
        StationsUiState(filterValue, cache, isRefreshing, error)
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

    fun selectFuelType(fuelType: StationFuelType) {
        filter.value = filter.value.copy(fuelType = fuelType)
    }

    fun selectProvince(province: String?) {
        filter.value = filter.value.copy(province = province, municipality = null)
    }

    fun selectMunicipality(municipality: String?) {
        filter.value = filter.value.copy(municipality = municipality)
    }
}

data class StationsUiState(
    val filter: StationFilter = StationFilter(),
    val metadata: StationCacheMetadataEntity? = null,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null
) {
    val hasCache: Boolean get() = metadata != null
    val isStale: Boolean get() = StationRepository.isStale(metadata)
}
