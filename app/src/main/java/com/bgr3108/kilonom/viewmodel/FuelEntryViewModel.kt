package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

class FuelEntryViewModel(
    private val repository: FuelRepository
) : ViewModel() {

    val entries: StateFlow<List<FuelEntry>> =
        repository.getAllEntries()
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
        entry: FuelEntry,
        onSaved: () -> Unit,
        onError: () -> Unit
    ) {
        if (!saveMutex.tryLock()) return

        _isSaving.value = true

        viewModelScope.launch {
            try {
                repository.addEntry(entry)
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
            repository.delete(entry)
        }
    }
}
