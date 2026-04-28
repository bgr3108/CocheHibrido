package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val fuelRepository: FuelRepository
) : ViewModel() {

    private val _precioGasolina = MutableStateFlow(1.44)
    val precioGasolina: StateFlow<Double> = _precioGasolina

    private val _precioElectrico = MutableStateFlow(0.25)
    val precioElectrico: StateFlow<Double> = _precioElectrico

    val consumoGasolina = MutableStateFlow(5.5)
    val consumoElectrico = MutableStateFlow(15.0)

    val costeGasolinaKm = combine(precioGasolina, consumoGasolina) { precio, consumo ->
        (precio * consumo) / 100
    }

    val costeElectricoKm = combine(precioElectrico, consumoElectrico) { precio, consumo ->
        (precio * consumo) / 100
    }

    init {
        observarDatos()
    }

    private fun observarDatos() {
        viewModelScope.launch {
            fuelRepository.getAllEntries().collect { lista ->

                if (lista.isEmpty()) return@collect

                // ⛽ GASOLINA
                val ultimoGasolina = lista
                    .filter { it.tipo == FuelType.GASOLINA }
                    .maxByOrNull { it.fecha }

                ultimoGasolina?.let {
                    if (it.cantidad > 0) {
                        val precioUnitario = it.precio / it.cantidad
                        _precioGasolina.value = precioUnitario
                    }
                }

                // 🔋 ELECTRICO
                val ultimoElectrico = lista
                    .filter { it.tipo == FuelType.ELECTRICO }
                    .maxByOrNull { it.fecha }

                ultimoElectrico?.let {
                    if (it.cantidad > 0) {
                        val precioUnitario = it.precio / it.cantidad
                        _precioElectrico.value = precioUnitario
                    }
                }
            }
        }
    }
}