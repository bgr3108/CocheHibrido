package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.Trip
import com.example.cochehibrido.data.TripRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed

class TripViewModel(
    private val repository: TripRepository
) : ViewModel() {

    // 📊 Lista de viajes
    val trips: StateFlow<List<Trip>> =
        repository.getAllTrips()
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    // ➕ Guardar / Editar (Room REPLACE)
    fun saveTrip(trip: Trip, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.insertTrip(trip)
            onSaved()
        }
    }

    // 🗑 Eliminar
    fun deleteTrip(trip: Trip) {
        viewModelScope.launch {
            repository.deleteTrip(trip)
        }
    }
}