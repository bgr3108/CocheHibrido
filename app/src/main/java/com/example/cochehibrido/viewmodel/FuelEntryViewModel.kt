package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.CarRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FuelEntryViewModel(
    private val repository: FuelRepository,
    private val carRepository: CarRepository
) : ViewModel() {

    val entries: StateFlow<List<FuelEntry>> =
        repository.getAllEntries()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    // 🔥 GUARDAR (CORREGIDO)
    fun saveEntry(entry: FuelEntry, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.addEntry(entry)
            onSaved()
        }
    }

    // 🔥 BORRAR
    fun deleteEntry(entry: FuelEntry) {
        viewModelScope.launch {
            repository.delete(entry)
        }
    }
}