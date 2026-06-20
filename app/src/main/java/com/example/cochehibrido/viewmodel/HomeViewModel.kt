package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.data.TripRepository
import com.example.cochehibrido.data.VehicleRepository
import com.example.cochehibrido.domain.ConsumptionStats
import com.example.cochehibrido.domain.calculateConsumption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.cochehibrido.data.FuelEntry

class HomeViewModel(
    private val fuelRepository: FuelRepository,
    tripRepository: TripRepository,
    vehicleRepository: VehicleRepository
) : ViewModel() {

    val entries = fuelRepository.getAllEntries()
    val trips = tripRepository.getAllTrips()
    val vehicle = vehicleRepository.vehicle
    val isVehicleLoading =
        vehicleRepository.isLoading
    val availableVehicles = MutableStateFlow(
        vehicleRepository
            .vehicleDataSource
            .loadVehicles()
    )
    val consumoGasolina = entries
        .map { list ->

            val fuelEntries = list
                .filter { it.tipo == FuelType.GASOLINA }
                .sortedBy { it.km }

            var litrosConsumidos = 0.0
            var kmRecorridos = 0.0

            var ultimoLleno: FuelEntry? = null
            var litrosAcumulados = 0.0

            fuelEntries.forEach { entry ->

                if (ultimoLleno == null) {

                    if (entry.fullTank) {
                        ultimoLleno = entry
                    }

                } else {

                    litrosAcumulados += entry.cantidad

                    if (entry.fullTank) {

                        val llenoAnterior = requireNotNull(ultimoLleno)

                        kmRecorridos +=
                            entry.km - llenoAnterior.km

                        litrosConsumidos +=
                            litrosAcumulados

                        ultimoLleno = entry
                        litrosAcumulados = 0.0
                    }
                }
            }

            if (kmRecorridos > 0) {
                (litrosConsumidos / kmRecorridos) * 100
            } else {
                0.0
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val consumoElectrico = entries
        .map { list ->

            val electricEntries = list
                .filter { it.tipo == FuelType.ELECTRICO }
                .sortedBy { it.km }

            if (electricEntries.size < 2) {
                return@map 0.0
            }

            val kmRecorridos =
                electricEntries.last().km -
                        electricEntries.first().km

            val kwhConsumidos =
                electricEntries.sumOf { it.cantidad }

            if (kmRecorridos > 0) {
                (kwhConsumidos / kmRecorridos) * 100
            } else {
                0.0
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val litrosTotales = entries.map { list ->
        list
            .filter { it.tipo == FuelType.GASOLINA }
            .sumOf { it.cantidad }
    }
    val gastoGasolinaTotal = entries.map { list ->
        list
            .filter { it.tipo == FuelType.GASOLINA }
            .sumOf { it.precio }
    }
    val kwhTotales = entries.map { list ->
        list
            .filter { it.tipo == FuelType.ELECTRICO }
            .sumOf { it.cantidad }
    }
    val gastoElectricoTotal = entries.map { list ->
        list
            .filter { it.tipo == FuelType.ELECTRICO }
            .sumOf { it.precio }
    }
    val totalKm = entries
        .map { list ->

            list.maxByOrNull { it.km }?.km ?: 0.0

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )
    val totalCost = entries
        .map { list -> list.sumOf { it.precio } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val kmEsteMes = entries
        .map { entryList ->

            val calendar = java.util.Calendar.getInstance()

            val mesActual = calendar.get(java.util.Calendar.MONTH)
            val anioActual = calendar.get(java.util.Calendar.YEAR)

            val entradasMes = entryList
                .filter {

                    val entryCalendar = java.util.Calendar.getInstance()
                    entryCalendar.timeInMillis = it.fecha

                    entryCalendar.get(java.util.Calendar.MONTH) == mesActual &&
                            entryCalendar.get(java.util.Calendar.YEAR) == anioActual
                }
                .sortedBy { it.km }

            if (entradasMes.size >= 2) {
                entradasMes.last().km - entradasMes.first().km
            } else {
                0.0
            }

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
                            entryCalendar.get(java.util.Calendar.YEAR) == anioActual
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

    val flow = precioElectrico
    val costeElectricoKm = combine(flow, consumoElectrico) { precio, consumo ->
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

                val gasolinaEntries = lista
                    .filter { it.tipo == FuelType.GASOLINA }

                val totalLitrosGasolina = gasolinaEntries.sumOf { it.cantidad }
                val gastoTotal = gasolinaEntries.sumOf { it.precio }

                _precioGasolina.value =
                    if (totalLitrosGasolina > 0)
                        gastoTotal / totalLitrosGasolina
                    else
                        0.0

                val electricEntries = lista
                    .filter { it.tipo == FuelType.ELECTRICO }

                val totalKwhElectricos = electricEntries.sumOf { it.cantidad }
                val gastoElectrico = electricEntries.sumOf { it.precio }

                _precioElectrico.value =
                    if (totalKwhElectricos > 0)
                        gastoElectrico / totalKwhElectricos
                    else
                        0.0
            }
        }
    }
}
