package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.FuelRepository
import com.bgr3108.kilonom.data.VehicleRepository
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.domain.PeriodComparison
import com.bgr3108.kilonom.domain.PeriodSummary
import com.bgr3108.kilonom.domain.StatisticsPeriod
import com.bgr3108.kilonom.domain.StatisticsPeriodMode
import com.bgr3108.kilonom.domain.calculatePeriodComparison
import com.bgr3108.kilonom.domain.calculatePeriodSummary
import com.bgr3108.kilonom.domain.currentPeriod
import com.bgr3108.kilonom.domain.nextPeriod
import com.bgr3108.kilonom.domain.previousPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PeriodSummaryUiState(
    val summary: PeriodSummary,
    val comparison: PeriodComparison?,
    val vehicleType: VehicleType?
)

class PeriodSummaryViewModel(
    fuelRepository: FuelRepository,
    vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _period = MutableStateFlow<StatisticsPeriod>(
        currentPeriod(StatisticsPeriodMode.MONTH)
    )
    private val timeRefresh = MutableStateFlow(0)
    private var followsCurrentPeriod = true

    val period: StateFlow<StatisticsPeriod> = _period

    val uiState: StateFlow<PeriodSummaryUiState> = combine(
        fuelRepository.getAllEntries(),
        vehicleRepository.vehicle,
        _period,
        timeRefresh
    ) { entries, vehicle, selectedPeriod, _ ->
        PeriodSummaryUiState(
            summary = calculatePeriodSummary(entries, vehicle, selectedPeriod),
            comparison = calculatePeriodComparison(entries, vehicle, selectedPeriod),
            vehicleType = vehicle.type
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeriodSummaryUiState(
            summary = calculatePeriodSummary(
                entries = emptyList(),
                vehicle = vehicleRepository.vehicle.value,
                period = _period.value
            ),
            comparison = null,
            vehicleType = vehicleRepository.vehicle.value.type
        )
    )

    fun selectMode(mode: StatisticsPeriodMode) {
        _period.value = currentPeriod(mode)
        followsCurrentPeriod = true
    }

    fun showPreviousPeriod() {
        _period.value = previousPeriod(_period.value) ?: return
        followsCurrentPeriod = false
    }

    fun showNextPeriod() {
        val candidate = nextPeriod(_period.value) ?: return
        val current = currentPeriod(candidate.mode)
        if (candidate.isAfter(current)) return

        _period.value = candidate
        followsCurrentPeriod = candidate == current
    }

    fun refreshCurrentPeriodOnResume() {
        if (followsCurrentPeriod) {
            _period.value = currentPeriod(_period.value.mode)
        }
        timeRefresh.update { it + 1 }
    }
}

private fun StatisticsPeriod.isAfter(other: StatisticsPeriod): Boolean = when {
    this is StatisticsPeriod.Month && other is StatisticsPeriod.Month ->
        year > other.year || (year == other.year && month > other.month)

    this is StatisticsPeriod.Year && other is StatisticsPeriod.Year -> year > other.year
    else -> false
}
