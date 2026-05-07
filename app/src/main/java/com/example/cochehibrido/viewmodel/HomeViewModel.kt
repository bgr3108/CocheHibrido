package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.data.TripRepository
import com.example.cochehibrido.domain.ConsumptionStats
import com.example.cochehibrido.domain.calculateConsumption
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import com.example.cochehibrido.data.BaselineRepository
import com.example.cochehibrido.data.VehicleBaseline

class HomeViewModel(
    private val fuelRepository: FuelRepository,
    private val tripRepository: TripRepository,
    private val baselineRepository: BaselineRepository
) : ViewModel() {

    val entries = fuelRepository.getAllEntries()
    val trips = tripRepository.getAllTrips()
    val baseline = baselineRepository.baseline
    val hasBaseline = baseline
        .map { it.kmInicial > 0 }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false
        )
    // 🔥 CONSUMOS (AQUÍ)
    val consumoGasolina = combine(trips, baseline) { tripList, base ->

        val kmViajes = tripList.sumOf { it.km }
        val gasolinaViajes = tripList.sumOf { it.consumoGasolina * it.km / 100 }

        val gasolinaBase = base.kmInicial * base.consumoGasolinaInicial / 100

        val totalKm = base.kmInicial + kmViajes
        val totalGas = gasolinaBase + gasolinaViajes

        if (totalKm > 0) (totalGas / totalKm) * 100 else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val consumoElectrico = combine(trips, baseline) { tripList, base ->

        val kmViajes = tripList.sumOf { it.km }
        val elecViajes = tripList.sumOf { it.consumoElectrico * it.km / 100 }

        val elecBase = base.kmInicial * base.consumoElectricoInicial / 100

        val totalKm = base.kmInicial + kmViajes
        val totalElec = elecBase + elecViajes

        if (totalKm > 0) (totalElec / totalKm) * 100 else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val totalKm = combine(trips, baseline) { tripList, base ->
        base.kmInicial + tripList.sumOf { it.km }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )
    val totalCost = entries
        .map { list -> list.sumOf { it.precio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val costPerKm = combine(totalCost, totalKm) { cost, km ->
        if (km > 0) cost / km else 0.0
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )

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
    val costeGasolinaKm = combine(precioGasolina, consumoGasolina) { precio, consumo ->
        (precio * consumo) / 100
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )

    val costeElectricoKm = combine(precioElectrico, consumoElectrico) { precio, consumo ->
        (precio * consumo) / 100
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