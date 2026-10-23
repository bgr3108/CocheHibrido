package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.chargers.ChargerConnectorType
import com.bgr3108.kilonom.chargers.ChargerFilter
import com.bgr3108.kilonom.chargers.ChargerSortOrder
import com.bgr3108.kilonom.domain.StatisticsPeriod
import com.bgr3108.kilonom.domain.StatisticsPeriodMode
import com.bgr3108.kilonom.domain.currentPeriod
import com.bgr3108.kilonom.stations.StationFilter
import com.bgr3108.kilonom.stations.StationFuelType
import com.bgr3108.kilonom.stations.StationSortOrder

// Bundle-compatible selections only: never coordinates, cache contents or transient operations.
internal inline fun <reified T : Enum<T>> savedEnum(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

internal fun StationFilter.savedValues() = arrayListOf(
    fuelType.name, province.orEmpty(), municipality.orEmpty(), sortOrder.name
)

internal fun restoredStationFilter(values: List<String>?): StationFilter {
    if (values?.size != 4) return StationFilter()
    val province = values[1].takeIf { it.isNotBlank() }
    return StationFilter(
        fuelType = savedEnum(values[0], StationFuelType.GASOLINE_95),
        province = province,
        municipality = values[2].takeIf { province != null && it.isNotBlank() },
        sortOrder = savedEnum(values[3], StationSortOrder.PRICE)
    )
}

internal fun ChargerFilter.savedValues() = arrayListOf(
    connectorType?.name.orEmpty(), minimumPowerKw?.toString().orEmpty(), operatorName.orEmpty(),
    province.orEmpty(), municipality.orEmpty(), sortOrder.name
)

internal fun restoredChargerFilter(values: List<String>?): ChargerFilter {
    if (values?.size != 6) return ChargerFilter()
    val province = values[3].takeIf { it.isNotBlank() }
    return ChargerFilter(
        connectorType = ChargerConnectorType.entries.firstOrNull { it.name == values[0] },
        minimumPowerKw = values[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 },
        operatorName = values[2].takeIf { it.isNotBlank() },
        province = province,
        municipality = values[4].takeIf { province != null && it.isNotBlank() },
        sortOrder = savedEnum(values[5], ChargerSortOrder.POWER)
    )
}

internal fun StatisticsPeriod.savedValues(): ArrayList<String> = when (this) {
    is StatisticsPeriod.Month -> arrayListOf(mode.name, year.toString(), month.toString())
    is StatisticsPeriod.Year -> arrayListOf(mode.name, year.toString())
    StatisticsPeriod.All -> arrayListOf(mode.name)
}

internal fun restoredStatisticsPeriod(values: List<String>?): StatisticsPeriod {
    val fallback = currentPeriod(StatisticsPeriodMode.MONTH)
    val year = values?.getOrNull(1)?.toIntOrNull()
    // Only periods reachable through the existing UI (AD years, not future periods).
    val currentYear = (currentPeriod(StatisticsPeriodMode.YEAR) as StatisticsPeriod.Year).year
    return when (values?.firstOrNull()) {
        StatisticsPeriodMode.ALL.name -> StatisticsPeriod.All
        StatisticsPeriodMode.YEAR.name -> year?.takeIf { it in 1..currentYear }
            ?.let(StatisticsPeriod::Year) ?: fallback
        StatisticsPeriodMode.MONTH.name -> {
            val month = values.getOrNull(2)?.toIntOrNull()
            if (year != null && year in 1..currentYear && month != null && month in 0..11) {
                val candidate = StatisticsPeriod.Month(year, month)
                val current = currentPeriod(StatisticsPeriodMode.MONTH) as StatisticsPeriod.Month
                if (year < current.year || month <= current.month) candidate else fallback
            } else fallback
        }
        else -> fallback
    }
}
