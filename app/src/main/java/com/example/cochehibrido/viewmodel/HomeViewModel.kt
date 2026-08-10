package com.example.cochehibrido.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelRepository
import com.example.cochehibrido.data.CarRepository
import com.example.cochehibrido.data.FuelType
import com.example.cochehibrido.data.MonthlyCost
import com.example.cochehibrido.data.MonthlyPrice
import com.example.cochehibrido.data.Vehicle
import com.example.cochehibrido.data.VehicleCategory
import com.example.cochehibrido.data.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
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
import com.example.cochehibrido.domain.calculateCostPerKilometer
import com.example.cochehibrido.domain.calculateTotalCost
import com.example.cochehibrido.domain.calculateTravelledKilometers
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
import com.example.cochehibrido.domain.isValidEconomicEntry
import com.example.cochehibrido.domain.sumValidEconomicValues
import com.example.cochehibrido.ui.components.charts.ChartPoint

enum class ResetState {
    IDLE,
    LOADING,
    ERROR
}

internal suspend fun resetApplicationData(
    fuelRepository: FuelRepository,
    carRepository: CarRepository,
    vehicleRepository: VehicleRepository
) {
    fuelRepository.deleteAll()
    carRepository.deleteAll()
    vehicleRepository.clearVehicle()
}

class HomeViewModel(
    private val fuelRepository: FuelRepository,
    private val carRepository: CarRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    // ============================================================
    // Vehículo
    // ============================================================

    val vehicle = vehicleRepository.vehicle

    val isVehicleLoading =
        vehicleRepository.isLoading

    private val vehicleSaveMutex = Mutex()
    private val _isSavingVehicle = MutableStateFlow(false)
    private val _vehicleSaveFailed = MutableStateFlow(false)

    val isSavingVehicle: StateFlow<Boolean> = _isSavingVehicle
    val vehicleSaveFailed: StateFlow<Boolean> = _vehicleSaveFailed

    private val resetMutex = Mutex()
    private val _resetState = MutableStateFlow(ResetState.IDLE)

    val resetState: StateFlow<ResetState> = _resetState

    private val _setupVehicleCategory = MutableStateFlow(VehicleCategory.COCHE)
    val setupVehicleCategory: StateFlow<VehicleCategory> = _setupVehicleCategory

    val availableVehicles = MutableStateFlow(
        vehicleRepository.vehicleDataSource.loadVehicles(VehicleCategory.COCHE)
    )

    fun selectSetupVehicleCategory(category: VehicleCategory) {
        if (_setupVehicleCategory.value == category) return

        _setupVehicleCategory.value = category
        availableVehicles.value = vehicleRepository.vehicleDataSource.loadVehicles(category)
    }

    fun saveInitialVehicle(
        vehicle: Vehicle,
        onSaved: () -> Unit
    ) {
        if (!vehicleSaveMutex.tryLock()) return

        _isSavingVehicle.value = true
        _vehicleSaveFailed.value = false

        viewModelScope.launch {
            try {
                vehicleRepository.saveVehicle(vehicle)
                onSaved()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                _vehicleSaveFailed.value = true
            } finally {
                _isSavingVehicle.value = false
                vehicleSaveMutex.unlock()
            }
        }
    }

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
        sumValidEconomicValues(list, FuelType.GASOLINA) { it.cantidad }
    }

    val gastoGasolinaTotal = entries.map { list ->
        calculateTotalFuelCost(list)
    }

    val kwhTotales = entries.map { list ->
        sumValidEconomicValues(list, FuelType.ELECTRICO) { it.cantidad }
    }

    val gastoElectricoTotal = entries.map { list ->
        calculateTotalElectricCost(list)
    }

    val gastoTotalVehiculo = entries
        .map(::calculateTotalCost)
        .stateIn(

        viewModelScope,

        SharingStarted.WhileSubscribed(5000),

        0.0

    )

    val totalKm = combine(entries, vehicle) { list, currentVehicle ->
        calculateTravelledKilometers(
            entries = list,
            initialKilometers = currentVehicle.currentKm
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val totalCost = entries
        .map(::calculateTotalCost)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val costPerKm =
        combine(totalCost, totalKm, ::calculateCostPerKilometer)
            .stateIn(
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

    private val _monthlyGasolineCosts =
        MutableStateFlow<List<MonthlyCost>>(emptyList())

    val monthlyGasolineCosts:
            StateFlow<List<MonthlyCost>> =
        _monthlyGasolineCosts

    private val _monthlyElectricCosts =
        MutableStateFlow<List<MonthlyCost>>(emptyList())

    val monthlyElectricCosts:
            StateFlow<List<MonthlyCost>> =
        _monthlyElectricCosts

    val historialPreciosGasolina = monthlyGasolinePrices
        .map { prices ->
            createChartPoints(prices.map { it.averagePrice })
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val historialPreciosElectricos = monthlyElectricPrices
        .map { prices ->
            createChartPoints(prices.map { it.averagePrice })
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val historialCostesGasolina = monthlyGasolineCosts
        .map { costs ->
            createChartPoints(costs.map { it.totalCost })
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val historialCostesElectricos = monthlyElectricCosts
        .map { costs ->
            createChartPoints(costs.map { it.totalCost })
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

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

        return groupEntriesByMonth(entries.filter(::isValidEconomicEntry))
            .map { (month, entriesMes) ->
                MonthlyPrice(
                    month = month,
                    averagePrice = when (entriesMes.first().tipo) {
                        FuelType.GASOLINA -> calculateAverageFuelPrice(entriesMes)
                        FuelType.ELECTRICO -> calculateAverageElectricPrice(entriesMes)
                    }
                )
            }
    }

    private fun calculateMonthlyCosts(
        entries: List<FuelEntry>
    ): List<MonthlyCost> {

        return groupEntriesByMonth(entries.filter(::isValidEconomicEntry))
            .map { (month, entriesMes) ->
                MonthlyCost(
                    month = month,
                    totalCost = calculateTotalCost(entriesMes)
                )
            }
    }

    private fun groupEntriesByMonth(
        entries: List<FuelEntry>
    ): List<Pair<String, List<FuelEntry>>> {

        val monthFormatter = java.text.SimpleDateFormat(
            "MM/yyyy",
            java.util.Locale.getDefault()
        )

        return entries
            .groupBy {
                monthFormatter.format(java.util.Date(it.fecha))
            }
            .entries
            .sortedBy { (_, entriesMes) ->
                entriesMes.minOf { it.fecha }
            }
            .map { it.key to it.value }
    }

    private fun createChartPoints(
        values: List<Double>
    ): List<ChartPoint> {

        return values.mapIndexed { index, value ->
            ChartPoint(
                x = index.toDouble(),
                y = value.toFloat()
            )
        }
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

                        _monthlyGasolineCosts.value =
                            emptyList()

                        _monthlyElectricCosts.value =
                            emptyList()

                        return@collect
                    }

                    val validEntries = lista.filter(::isValidEconomicEntry)

                    val gasolinaEntries =
                        validEntries.filter {
                            it.tipo == FuelType.GASOLINA
                        }

                    val electricEntries =
                        validEntries.filter {
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

                    _monthlyGasolineCosts.value =
                        calculateMonthlyCosts(
                            gasolinaEntries
                        )

                    _monthlyElectricCosts.value =
                        calculateMonthlyCosts(
                            electricEntries
                        )
                }
        }
    }

    fun resetApplication() {
        if (!resetMutex.tryLock()) return

        _resetState.value = ResetState.LOADING

        viewModelScope.launch {
            try {
                resetApplicationData(fuelRepository, carRepository, vehicleRepository)
                _resetState.value = ResetState.IDLE
            } catch (error: Throwable) {
                if (error is CancellationException) throw error

                _resetState.value = ResetState.ERROR
            } finally {
                resetMutex.unlock()
            }
        }
    }

    fun dismissResetError() {
        if (_resetState.value == ResetState.ERROR) {
            _resetState.value = ResetState.IDLE
        }
    }
}
