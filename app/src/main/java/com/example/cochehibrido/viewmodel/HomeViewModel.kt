package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.BaselineRepository
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.data.TripRepository
import com.example.cochehibrido.domain.ConsumptionStats
import com.example.cochehibrido.domain.calculateConsumption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val fuelRepository: FuelRepository,
    tripRepository: TripRepository,
    baselineRepository: BaselineRepository
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
    val porcentajeElectrico = combine(trips, baseline) { tripList, base ->

        val kmViajes = tripList.sumOf { it.km }

        val kmElectricos =
            tripList.sumOf {
                if (it.consumoElectrico > 0) it.km else 0.0
            }

        val totalKm = base.kmInicial + kmViajes

        if (totalKm > 0) {
            (kmElectricos / totalKm) * 100
        } else {
            0.0
        }

    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0.0
    )
    val totalCost = entries
        .map { list -> list.sumOf { it.precio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val kmEsteMes = trips
        .map { tripList ->

            val calendar = java.util.Calendar.getInstance()

            val mesActual = calendar.get(java.util.Calendar.MONTH)
            val anioActual = calendar.get(java.util.Calendar.YEAR)

            tripList
                .filter {

                    val tripCalendar = java.util.Calendar.getInstance()
                    tripCalendar.timeInMillis = it.fecha

                    tripCalendar.get(java.util.Calendar.MONTH) == mesActual &&
                            tripCalendar.get(java.util.Calendar.YEAR) == anioActual
                }
                .sumOf { it.km }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
    val gastoEsteMes = entries
        .map { entryList ->

            val calendar = java.util.Calendar.getInstance()

            val mesActual = calendar.get(java.util.Calendar.MONTH)
            val anioActual = calendar.get(java.util.Calendar.YEAR)

            entryList
                .filter {

                    val entryCalendar = java.util.Calendar.getInstance()
                    entryCalendar.timeInMillis = it.fecha

                    entryCalendar.get(java.util.Calendar.MONTH) == mesActual &&
                            entryCalendar.get(java.util.Calendar.YEAR) ==anioActual
                }
                .sumOf { it.precio }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
    val totalLitrosGasolina = entries
        .map { list ->

            list
                .filter { it.tipo == FuelType.GASOLINA }
                .sumOf { it.cantidad }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
    val totalKwhElectricos = entries
        .map { list ->

            list
                .filter { it.tipo == FuelType.ELECTRICO }
                .sumOf { it.cantidad }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
    val ultimoGasolina = entries
        .map { list ->

            list
                .filter { it.tipo == FuelType.GASOLINA }
                .maxByOrNull { it.fecha }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
    val ultimoElectrico = entries
        .map { list ->

            list
                .filter { it.tipo == FuelType.ELECTRICO }
                .maxByOrNull { it.fecha }

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )
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
    //val stats: StateFlow<ConsumptionStats> = _stats

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