package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelRepository
import com.bgr3108.kilonom.data.CarRepository
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.MonthlyCost
import com.bgr3108.kilonom.data.MonthlyPrice
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import com.bgr3108.kilonom.domain.calculateAverageElectricConsumption
import com.bgr3108.kilonom.domain.calculateBestElectricConsumption
import com.bgr3108.kilonom.domain.calculateElectricSegmentCount
import com.bgr3108.kilonom.domain.calculateWorstElectricConsumption
import com.bgr3108.kilonom.domain.calculateAverageElectricPrice
import com.bgr3108.kilonom.domain.calculateAverageFuelPrice
import com.bgr3108.kilonom.domain.calculateCostPerKilometer
import com.bgr3108.kilonom.domain.calculateTotalCost
import com.bgr3108.kilonom.domain.calculateTravelledKilometers
import com.bgr3108.kilonom.domain.calculateElectricCharges
import com.bgr3108.kilonom.domain.calculateFuelRefuels
import com.bgr3108.kilonom.domain.calculateMaxElectricPrice
import com.bgr3108.kilonom.domain.calculateMaxFuelPrice
import com.bgr3108.kilonom.domain.calculateMinElectricPrice
import com.bgr3108.kilonom.domain.calculateMinFuelPrice
import com.bgr3108.kilonom.domain.calculateCheapestPaidCharge
import com.bgr3108.kilonom.domain.calculateCheapestRefuel
import com.bgr3108.kilonom.domain.calculateFreeCharges
import com.bgr3108.kilonom.domain.calculateMostExpensiveCharge
import com.bgr3108.kilonom.domain.calculateMostExpensiveRefuel
import com.bgr3108.kilonom.domain.calculateTotalElectricCost
import com.bgr3108.kilonom.domain.calculateTotalFuelCost
import com.bgr3108.kilonom.domain.calculateFuelSegments
import com.bgr3108.kilonom.domain.calculateCurrentEstimatedFuelConsumption
import com.bgr3108.kilonom.domain.calculateAverageFuelConsumptionFromSegments
import com.bgr3108.kilonom.domain.calculateBestFuelConsumptionFromSegments
import com.bgr3108.kilonom.domain.calculateWorstFuelConsumptionFromSegments
import com.bgr3108.kilonom.domain.calculateFuelSegmentCountFromSegments
import com.bgr3108.kilonom.domain.calculateElectricSegments
import com.bgr3108.kilonom.domain.isValidEconomicEntry
import com.bgr3108.kilonom.domain.sumValidEconomicValues
import com.bgr3108.kilonom.ui.components.charts.ChartPoint

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

    val showReleaseNotes = vehicleRepository.showReleaseNotes

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

    fun dismissReleaseNotes() {
        viewModelScope.launch {
            vehicleRepository.dismissReleaseNotes()
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

    /** Confirmed full-to-full intervals used by all historical fuel statistics and charts. */
    val fuelConsumptionSegments = entries
        .map(::calculateFuelSegments)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val consumoGasolina = fuelConsumptionSegments
        .map(::calculateAverageFuelConsumptionFromSegments)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val consumoElectrico = entries
        .map(::calculateAverageElectricConsumption)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            0.0
        )

    val mejorConsumoGasolina = fuelConsumptionSegments
        .map(::calculateBestFuelConsumptionFromSegments)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val peorConsumoGasolina = fuelConsumptionSegments
        .map(::calculateWorstFuelConsumptionFromSegments)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val numeroTramosGasolina = fuelConsumptionSegments
        .map(::calculateFuelSegmentCountFromSegments)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * A provisional value from the most recent confirmed full tank to the latest partial
     * refuel. It never contributes to the historical statistics above.
     */
    val currentEstimatedFuelConsumption = combine(entries, vehicle) { list, currentVehicle ->
        calculateCurrentEstimatedFuelConsumption(list, currentVehicle.fuelTankCapacity)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    val historialConsumoGasolina = fuelConsumptionSegments
        .map { segments ->
            segments.map { segment ->
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
