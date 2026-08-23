package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelRepository
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.VehicleRepository
import com.bgr3108.kilonom.data.observeActiveVehicleEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

data class FuelEntryDraft(
    val id: Int,
    val fecha: Long,
    val cantidad: Double,
    val precio: Double,
    val tipo: FuelType,
    val km: Double,
    val fullTank: Boolean,
    val fuelLevelAfter: Double?,
    val originalVehicleId: Long?
)

class FuelEntryViewModel(
    private val repository: FuelRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val activeContext = repository
        .observeActiveVehicleEntries(vehicleRepository.activeVehicle)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.bgr3108.kilonom.data.ActiveVehicleEntries(
                vehicle = com.bgr3108.kilonom.data.Vehicle(),
                entries = emptyList()
            )
        )

    val entries: StateFlow<List<FuelEntry>> =
        activeContext
            .map { it.entries }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _filterState = MutableStateFlow(ConsumptionFilterState())
    val filterState: StateFlow<ConsumptionFilterState> = _filterState.asStateFlow()

    private val _dateFilterRefresh = MutableStateFlow(0)

    private val saveMutex = Mutex()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    val filteredEntries: StateFlow<List<FuelEntry>> =
        combine(entries, filterState, _dateFilterRefresh) { entries, filters, _ ->
            filterConsumptionEntries(entries, filters)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setEnergyFilter(filter: EnergyFilter) {
        _filterState.value = _filterState.value.copy(energyFilter = filter)
    }

    fun setDateFilter(filter: DateFilter) {
        _filterState.value = _filterState.value.copy(dateFilter = filter)
    }

    fun clearFilters() {
        _filterState.value = ConsumptionFilterState()
    }

    fun refreshDateFilters() {
        _dateFilterRefresh.value += 1
    }

    // 🔥 GUARDAR (CORREGIDO)
    fun saveEntry(
        draft: FuelEntryDraft,
        onSaved: () -> Unit,
        onError: () -> Unit
    ) {
        if (!saveMutex.tryLock()) return

        _isSaving.value = true

        viewModelScope.launch {
            try {
                val activeVehicleId = vehicleRepository.activeVehicleId.value
                    ?: error("No hay un vehículo activo")
                val entry = FuelEntry(
                    id = draft.id,
                    fecha = draft.fecha,
                    cantidad = draft.cantidad,
                    precio = draft.precio,
                    tipo = draft.tipo,
                    km = draft.km,
                    fullTank = draft.fullTank,
                    fuelLevelAfter = draft.fuelLevelAfter,
                    vehicleId = if (draft.id == 0) activeVehicleId else {
                        require(draft.originalVehicleId == activeVehicleId) {
                            "La entrada ya no pertenece al vehículo activo"
                        }
                        activeVehicleId
                    }
                )
                if (draft.id == 0) {
                    repository.addEntryForVehicle(entry, activeVehicleId)
                } else {
                    repository.updateEntryForVehicle(entry, activeVehicleId)
                }
                onSaved()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                onError()
            } finally {
                _isSaving.value = false
                saveMutex.unlock()
            }
        }
    }

    // 🔥 BORRAR
    fun deleteEntry(entry: FuelEntry) {
        viewModelScope.launch {
            val activeVehicleId = vehicleRepository.activeVehicleId.value ?: return@launch
            repository.deleteEntryForVehicle(entry, activeVehicleId)
        }
    }
}
