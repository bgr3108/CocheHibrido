package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import java.util.Calendar

enum class EnergyFilter {
    ALL,
    GASOLINE,
    ELECTRIC
}

sealed interface DateFilter {
    data object ALL : DateFilter
    data object THIS_MONTH : DateFilter
    data object LAST_MONTH : DateFilter
    data object THIS_YEAR : DateFilter
}

val supportedDateFilters = listOf(
    DateFilter.ALL,
    DateFilter.THIS_MONTH,
    DateFilter.LAST_MONTH,
    DateFilter.THIS_YEAR
)

data class ConsumptionFilterState(
    val energyFilter: EnergyFilter = EnergyFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL
) {
    val hasActiveFilters: Boolean
        get() = energyFilter != EnergyFilter.ALL || dateFilter != DateFilter.ALL
}

internal fun filterConsumptionEntries(
    entries: List<FuelEntry>,
    filters: ConsumptionFilterState,
    referenceCalendar: Calendar = Calendar.getInstance()
): List<FuelEntry> {
    if (!filters.hasActiveFilters) {
        return entries
    }

    val dateInterval = filters.dateFilter.toDateInterval(referenceCalendar)

    return entries.filter { entry ->
        filters.energyFilter.matches(entry.tipo) && dateInterval?.contains(entry.fecha) != false
    }
}

private fun EnergyFilter.matches(type: FuelType): Boolean = when (this) {
    EnergyFilter.ALL -> true
    EnergyFilter.GASOLINE -> type == FuelType.GASOLINA
    EnergyFilter.ELECTRIC -> type == FuelType.ELECTRICO
}

private fun DateFilter.toDateInterval(referenceCalendar: Calendar): DateInterval? = when (this) {
    DateFilter.ALL -> null
    DateFilter.THIS_MONTH -> {
        val start = referenceCalendar.startOfMonth()
        DateInterval(start.timeInMillis, start.nextMonth().timeInMillis)
    }
    DateFilter.LAST_MONTH -> {
        val start = referenceCalendar.startOfMonth().apply { add(Calendar.MONTH, -1) }
        DateInterval(start.timeInMillis, start.nextMonth().timeInMillis)
    }
    DateFilter.THIS_YEAR -> {
        val start = (referenceCalendar.clone() as Calendar).apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        DateInterval(start.timeInMillis, start.apply { add(Calendar.YEAR, 1) }.timeInMillis)
    }
}

private fun Calendar.startOfMonth(): Calendar = (clone() as Calendar).apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun Calendar.nextMonth(): Calendar = (clone() as Calendar).apply {
    add(Calendar.MONTH, 1)
}

private data class DateInterval(
    val startMillis: Long,
    val endExclusiveMillis: Long
) {
    fun contains(timestamp: Long): Boolean =
        timestamp >= startMillis && timestamp < endExclusiveMillis
}
