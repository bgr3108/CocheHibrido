package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.data.MonthlyPrice
import com.example.cochehibrido.data.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.cochehibrido.domain.calculateAverageElectricConsumption
import com.example.cochehibrido.domain.calculateAverageFuelConsumption
import com.example.cochehibrido.domain.calculateBestElectricConsumption
import com.example.cochehibrido.domain.calculateBestFuelConsumption
import com.example.cochehibrido.domain.calculateElectricSegmentCount
import com.example.cochehibrido.domain.calculateFuelSegmentCount
import com.example.cochehibrido.domain.calculateWorstElectricConsumption
import com.example.cochehibrido.domain.calculateWorstFuelConsumption
import com.example.cochehibrido.domain.calculateAverageElectricPrice
import com.example.cochehibrido.domain.calculateAverageFuelPrice
import com.example.cochehibrido.domain.calculateElectricCharges
import com.example.cochehibrido.domain.calculateFuelRefuels
import com.example.cochehibrido.domain.calculateMaxElectricPrice
import com.example.cochehibrido.domain.calculateMaxFuelPrice
import com.example.cochehibrido.domain.calculateMinElectricPrice
import com.example.cochehibrido.domain.calculateMinFuelPrice
import com.example.cochehibrido.domain.calculateCheapestPaidCharge
import com.example.cochehibrido.domain.calculateCheapestRefuel
import com.example.cochehibrido.domain.calculateFreeCharges
import com.example.cochehibrido.domain.calculateMostExpensiveCharge
import com.example.cochehibrido.domain.calculateMostExpensiveRefuel
import com.example.cochehibrido.domain.calculateTotalElectricCost
import com.example.cochehibrido.domain.calculateTotalFuelCost
import com.example.cochehibrido.domain.calculateFuelSegments
import com.example.cochehibrido.domain.calculateElectricSegments
import com.example.cochehibrido.domain.ChartPoint

class HomeViewModel(
    private val fuelRepository: FuelRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    // ============================================================
    // Vehículo
    // ============================================================

    val vehicle = vehicleRepository.vehicle

    val isVehicleLoading =
        vehicleRepository.isLoading

    val availableVehicles =
        MutableStateFlow(
            vehicleRepository
                .vehicleDataSource
                .loadVehicles()
        )

    // ============================================================
    // Entradas
    // ============================================================

    val entries =
        fuelRepository.getAllEntries()

    // ============================================================
    // Consumos
    // ============================================================

    val consumoGasolina = entries
        .map(::calculateAverageFuelConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val consumoElectrico = entries
        .map(::calculateAverageElectricConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val mejorConsumoGasolina = entries
        .map(::calculateBestFuelConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val peorConsumoGasolina = entries
        .map(::calculateWorstFuelConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val numeroTramosGasolina = entries
        .map(::calculateFuelSegmentCount)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    val mejorConsumoElectrico = entries
        .map(::calculateBestElectricConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val peorConsumoElectrico = entries
        .map(::calculateWorstElectricConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val numeroTramosElectricos = entries
        .map(::calculateElectricSegmentCount)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    val historialConsumoGasolina = entries
        .map {
            calculateFuelSegments(it).map { segment ->
                ChartPoint(
                    x = segment.endKm,
                    y = segment.consumption.toFloat()
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val historialConsumoElectrico = entries
        .map {
            calculateElectricSegments(it).map { segment ->
                ChartPoint(
                    x = segment.endKm,
                    y = segment.consumption.toFloat()
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    // ============================================================
    // Costes
    // ============================================================

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
    val gastoTotalVehiculo = combine(

        gastoGasolinaTotal,

        gastoElectricoTotal

    ) { gasolina, electrico ->

        gasolina + electrico

    }.stateIn(

        viewModelScope,

        SharingStarted.WhileSubscribed(5000),

        0.0

    )

    val totalKm = entries
        .map { list ->
            list.maxByOrNull { it.km }?.km ?: 0.0
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val totalCost = entries
        .map { list ->
            list.sumOf { it.precio }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val costPerKm =
        combine(totalCost, totalKm) { cost, km ->

            if (km > 0)
                cost / km
            else
                0.0

        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    // ============================================================
    // Últimos registros
    // ============================================================

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

    // ============================================================
    // Precios
    // ============================================================

    private val _precioGasolina = MutableStateFlow(0.0)
    val precioGasolina: StateFlow<Double> = _precioGasolina

    private val _precioElectrico = MutableStateFlow(0.0)
    val precioElectrico: StateFlow<Double> = _precioElectrico

    val precioMinimoGasolina = entries
        .map(::calculateMinFuelPrice)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val precioMaximoGasolina = entries
        .map(::calculateMaxFuelPrice)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val numeroRepostajes = entries
        .map(::calculateFuelRefuels)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    val precioMinimoElectrico = entries
        .map(::calculateMinElectricPrice)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val precioMaximoElectrico = entries
        .map(::calculateMaxElectricPrice)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val numeroCargas = entries
        .map(::calculateElectricCharges)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )
    private val _monthlyGasolinePrices =
        MutableStateFlow<List<MonthlyPrice>>(emptyList())

    val monthlyGasolinePrices:
            StateFlow<List<MonthlyPrice>> =
        _monthlyGasolinePrices

    private val _monthlyElectricPrices =
        MutableStateFlow<List<MonthlyPrice>>(emptyList())

    val monthlyElectricPrices:
            StateFlow<List<MonthlyPrice>> =
        _monthlyElectricPrices

    init {
        observarDatos()
    }

    val costeTotalGasolina = entries
        .map(::calculateTotalFuelCost)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val repostajeMasCaro = entries
        .map(::calculateMostExpensiveRefuel)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val repostajeMasBarato = entries
        .map(::calculateCheapestRefuel)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val costeTotalElectrico = entries
        .map(::calculateTotalElectricCost)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val cargaMasCara = entries
        .map(::calculateMostExpensiveCharge)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val cargaMasBarataDePago = entries
        .map(::calculateCheapestPaidCharge)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val cargasGratuitas = entries
        .map(::calculateFreeCharges)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0
        )

    // ============================================================
    // Funciones privadas
    // ============================================================

    private fun calculateMonthlyPrices(
        entries: List<FuelEntry>
    ): List<MonthlyPrice> {

        return entries
            .groupBy {

                java.text.SimpleDateFormat(
                    "MM/yyyy",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(it.fecha))

            }
            .map { (mes, entriesMes) ->

                val cantidad =
                    entriesMes.sumOf { it.cantidad }

                val precio =
                    entriesMes.sumOf { it.precio }

                MonthlyPrice(
                    month = mes,
                    averagePrice =
                        if (cantidad > 0)
                            precio / cantidad
                        else
                            0.0
                )

            }
            .sortedBy { it.month }
    }
    private fun observarDatos() {

        viewModelScope.launch {

            fuelRepository
                .getAllEntries()
                .collect { lista ->

                    if (lista.isEmpty()) {

                        _precioGasolina.value = 0.0
                        _precioElectrico.value = 0.0

                        _monthlyGasolinePrices.value =
                            emptyList()

                        _monthlyElectricPrices.value =
                            emptyList()

                        return@collect
                    }

                    val gasolinaEntries =
                        lista.filter {
                            it.tipo == FuelType.GASOLINA
                        }

                    val electricEntries =
                        lista.filter {
                            it.tipo == FuelType.ELECTRICO
                        }

                    _precioGasolina.value =
                        calculateAverageFuelPrice(lista)

                    _precioElectrico.value =
                        calculateAverageElectricPrice(lista)

                    // Precios medios mensuales

                    _monthlyGasolinePrices.value =
                        calculateMonthlyPrices(
                            gasolinaEntries
                        )

                    _monthlyElectricPrices.value =
                        calculateMonthlyPrices(
                            electricEntries
                        )
                }
        }
    }

    fun resetApplication() {

        viewModelScope.launch {

            fuelRepository.deleteAll()

            vehicleRepository.clearVehicle()
        }
    }
}