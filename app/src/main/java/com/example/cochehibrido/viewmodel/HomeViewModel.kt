package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.domain.ConsumptionStats
import com.example.cochehibrido.domain.calculateConsumption
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

class HomeViewModel(
    private val fuelRepository: FuelRepository
) : ViewModel() {

    val entries = fuelRepository.getAllEntries()

    private val _precioGasolina = MutableStateFlow(0.0)
    val precioGasolina: StateFlow<Double> = _precioGasolina

    private val _precioElectrico = MutableStateFlow(0.0)
    val precioElectrico: StateFlow<Double> = _precioElectrico

    // 🔥 STATS REALES
    private val _stats = MutableStateFlow(
        ConsumptionStats(0.0, 0.0, 0.0, 0.0, 0.0)
    )
    val stats: StateFlow<ConsumptionStats> = _stats

    // 🔥 COSTES (AHORA BIEN COLOCADOS)
    val costeGasolinaKm = combine(precioGasolina, stats) { precio, s ->
        if (s.totalKm > 0) {
            val consumo = (s.totalGasolina / s.totalKm) * 100
            (precio * consumo) / 100
        } else 0.0
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )

    val costeElectricoKm = combine(precioElectrico, stats) { precio, s ->
        if (s.totalKm > 0) {
            val consumo = (s.totalElectrico / s.totalKm) * 100
            (precio * consumo) / 100
        } else 0.0
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )

    init {
        observarDatos()
    }

    private fun observarDatos() {
        viewModelScope.launch {
            fuelRepository.getAllEntries().collect { lista ->

                if (lista.isEmpty()) {
                    _precioGasolina.value = 0.0
                    _precioElectrico.value = 0.0
                    _stats.value = ConsumptionStats(0.0, 0.0, 0.0, 0.0, 0.0)
                    return@collect
                }

                _stats.value = calculateConsumption(lista)

                val ultimoGasolina = lista
                    .filter { it.tipo == FuelType.GASOLINA }
                    .maxByOrNull { it.fecha }

                ultimoGasolina?.let {
                    if (it.cantidad > 0) {
                        _precioGasolina.value = it.precio / it.cantidad
                    }
                }

                val ultimoElectrico = lista
                    .filter { it.tipo == FuelType.ELECTRICO }
                    .maxByOrNull { it.fecha }

                ultimoElectrico?.let {
                    if (it.cantidad > 0) {
                        _precioElectrico.value = it.precio / it.cantidad
                    }
                }
            }
        }
    }
}