package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed

class HomeViewModel(
    private val fuelRepository: FuelRepository
) : ViewModel() {

    val entries = fuelRepository.getAllEntries()
        .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    // ⛽ GASOLINA (ignora gratis)
    val precioGasolina: StateFlow<Double> =
        entries.map { list ->
            val gas = list.filter {
                it.tipo == FuelType.GASOLINA && it.precio > 0
            }

            val litros = gas.sumOf { it.cantidad }
            val euros = gas.sumOf { it.precio }

            if (litros > 0) euros / litros else 0.0
        }.stateIn(viewModelScope, WhileSubscribed(5000), 0.0)

    // 🔋 ELECTRICIDAD (ignora gratis)
    val precioElectrico: StateFlow<Double> =
        entries.map { list ->
            val elec = list.filter {
                it.tipo == FuelType.ELECTRICO && it.precio > 0
            }

            val kwh = elec.sumOf { it.cantidad }
            val euros = elec.sumOf { it.precio }

            if (kwh > 0) euros / kwh else 0.0
        }.stateIn(viewModelScope, WhileSubscribed(5000), 0.0)
}