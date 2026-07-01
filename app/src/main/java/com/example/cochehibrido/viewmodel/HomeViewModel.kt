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

            if (kmRecorridos > 0)
                (litrosConsumidos / kmRecorridos) * 100
            else
                0.0
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

            if (electricEntries.size < 2)
                return@map 0.0

            val kmRecorridos =
                electricEntries.last().km -
                        electricEntries.first().km

            val kwhConsumidos =
                electricEntries.sumOf { it.cantidad }

            if (kmRecorridos > 0)
                (kwhConsumidos / kmRecorridos) * 100
            else
                0.0
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
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

    // ============================================================
    // Estadísticas mensuales
    // ============================================================

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

                    // Precio medio gasolina

                    val litros =
                        gasolinaEntries.sumOf {
                            it.cantidad
                        }

                    val gastoGasolina =
                        gasolinaEntries.sumOf {
                            it.precio
                        }

                    _precioGasolina.value =
                        if (litros > 0)
                            gastoGasolina / litros
                        else
                            0.0

                    // Precio medio electricidad

                    val kwh =
                        electricEntries.sumOf {
                            it.cantidad
                        }

                    val gastoElectrico =
                        electricEntries.sumOf {
                            it.precio
                        }

                    _precioElectrico.value =
                        if (kwh > 0)
                            gastoElectrico / kwh
                        else
                            0.0

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