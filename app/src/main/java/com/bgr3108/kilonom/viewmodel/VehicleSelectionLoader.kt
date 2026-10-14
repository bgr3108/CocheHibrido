package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.VehicleCatalog
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleSelectionCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface VehicleSelectionState {
    data object Loading : VehicleSelectionState
    data class Ready(val catalog: VehicleSelectionCatalog) : VehicleSelectionState
    data object Failed : VehicleSelectionState
}

/** No catalog access until a form subscribes. The shared data source owns caching and fallback. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VehicleSelectionLoader(
    scope: CoroutineScope,
    catalog: VehicleCatalog,
    category: StateFlow<VehicleCategory>
) {
    private val retries = MutableStateFlow(0L)

    val state: StateFlow<VehicleSelectionState> = combine(category, retries) { selected, _ -> selected }
        .mapLatest { selected ->
            try {
                withContext(Dispatchers.IO) {
                    VehicleSelectionState.Ready(catalog.loadSelectionCatalog(selected))
                }
            } catch (_: IOException) {
                VehicleSelectionState.Failed
            } catch (_: IllegalArgumentException) {
                VehicleSelectionState.Failed
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), VehicleSelectionState.Loading)

    fun retry() {
        retries.update { it + 1 }
    }
}
