package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import java.util.Calendar
import java.util.TimeZone

fun calculatePeriodSummary(
    entries: List<FuelEntry>,
    period: StatisticsPeriod,
    timeZone: TimeZone = TimeZone.getDefault()
): PeriodSummary {
    val range = periodRange(period, timeZone)
    val entriesInPeriod = entries.filter { entry ->
        range?.let { periodRange ->
            entry.fecha in periodRange.startInclusive until periodRange.endExclusive
        } ?: true
    }
    val validEntries = validEconomicEntries(entriesInPeriod)
    val validFuelEntries = validEntries.filter { it.tipo == FuelType.GASOLINA }
    val validElectricEntries = validEntries.filter { it.tipo == FuelType.ELECTRICO }
    val totalCost = validEntries.sumFinite { it.precio }
    val fuelCost = validFuelEntries.sumFinite { it.precio }
    val electricCost = validElectricEntries.sumFinite { it.precio }
    val fuelQuantity = validFuelEntries.sumFinite { it.cantidad }
    val electricQuantity = validElectricEntries.sumFinite { it.cantidad }
    val distance = if (range == null) {
        calculateAllPeriodEconomicDistance(validEntries)
    } else {
        calculateReliablePeriodDistance(
            allEntries = entries,
            entriesInPeriod = entriesInPeriod,
            periodRange = range
        )
    }
    val costPerKilometer = distance?.let { kilometers ->
        calculateCostPerKilometer(totalCost, kilometers).takeIf { it.isFinite() }
    }

    return PeriodSummary(
        period = period,
        entryCount = entriesInPeriod.size,
        validEconomicEntryCount = validEntries.size,
        totalCost = totalCost,
        fuel = EnergyPeriodSummary(
            totalCost = fuelCost,
            quantity = fuelQuantity,
            averagePrice = (fuelCost / fuelQuantity).takeIf {
                fuelQuantity > 0.0 && it.isFinite()
            }
        ),
        electric = EnergyPeriodSummary(
            totalCost = electricCost,
            quantity = electricQuantity,
            averagePrice = (electricCost / electricQuantity).takeIf {
                electricQuantity > 0.0 && it.isFinite()
            }
        ),
        distanceKilometers = distance,
        costPerKilometer = costPerKilometer,
        costPerHundredKilometers = calculateCostPerHundredKilometers(costPerKilometer),
        monthlyExpenses = calculateMonthlyExpenses(entriesInPeriod, timeZone)
    )
}

fun calculatePeriodComparison(
    entries: List<FuelEntry>,
    period: StatisticsPeriod,
    timeZone: TimeZone = TimeZone.getDefault()
): PeriodComparison? {
    val previousPeriod = previousPeriod(period) ?: return null
    val current = calculatePeriodSummary(entries, period, timeZone)
    val previous = calculatePeriodSummary(entries, previousPeriod, timeZone)

    return PeriodComparison(
        totalCost = compareMetric(
            current = current.totalCost,
            previous = previous.totalCost,
            previousHasRecords = previous.hasEconomicData
        ),
        distance = if (current.distanceKilometers != null && previous.distanceKilometers != null) {
            compareMetric(
                current = current.distanceKilometers,
                previous = previous.distanceKilometers,
                previousHasRecords = true
            )
        } else {
            null
        }
    )
}

fun periodRange(
    period: StatisticsPeriod,
    timeZone: TimeZone = TimeZone.getDefault()
): PeriodRange? = when (period) {
    is StatisticsPeriod.Month -> calendarAtStartOf(period.year, period.month, timeZone).let { start ->
        val end = start.clone() as Calendar
        end.add(Calendar.MONTH, 1)
        PeriodRange(start.timeInMillis, end.timeInMillis)
    }

    is StatisticsPeriod.Year -> calendarAtStartOf(period.year, Calendar.JANUARY, timeZone).let { start ->
        val end = start.clone() as Calendar
        end.add(Calendar.YEAR, 1)
        PeriodRange(start.timeInMillis, end.timeInMillis)
    }

    StatisticsPeriod.All -> null
}

fun previousPeriod(period: StatisticsPeriod): StatisticsPeriod? = when (period) {
    is StatisticsPeriod.Month -> {
        if (period.month == Calendar.JANUARY) {
            StatisticsPeriod.Month(period.year - 1, Calendar.DECEMBER)
        } else {
            StatisticsPeriod.Month(period.year, period.month - 1)
        }
    }

    is StatisticsPeriod.Year -> StatisticsPeriod.Year(period.year - 1)
    StatisticsPeriod.All -> null
}

fun nextPeriod(period: StatisticsPeriod): StatisticsPeriod? = when (period) {
    is StatisticsPeriod.Month -> {
        if (period.month == Calendar.DECEMBER) {
            StatisticsPeriod.Month(period.year + 1, Calendar.JANUARY)
        } else {
            StatisticsPeriod.Month(period.year, period.month + 1)
        }
    }

    is StatisticsPeriod.Year -> StatisticsPeriod.Year(period.year + 1)
    StatisticsPeriod.All -> null
}

fun currentPeriod(
    mode: StatisticsPeriodMode,
    timeZone: TimeZone = TimeZone.getDefault()
): StatisticsPeriod {
    val calendar = Calendar.getInstance(timeZone)
    return when (mode) {
        StatisticsPeriodMode.MONTH -> StatisticsPeriod.Month(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH)
        )

        StatisticsPeriodMode.YEAR -> StatisticsPeriod.Year(calendar.get(Calendar.YEAR))
        StatisticsPeriodMode.ALL -> StatisticsPeriod.All
    }
}

/**
 * Measures one odometer range instead of adding pairwise deltas, so mixed fuel/electric records
 * cannot count the same kilometres twice. A limited period needs an actual prior reading: without
 * it, its first in-period consumption cannot be tied to a distance without inventing kilometres.
 */
private fun calculateReliablePeriodDistance(
    allEntries: List<FuelEntry>,
    entriesInPeriod: List<FuelEntry>,
    periodRange: PeriodRange?
): Double? {
    val endingKilometers = entriesInPeriod
        .asSequence()
        .map { it.km }
        .filter { it.isFinite() && it >= 0.0 }
        .maxOrNull()
        ?: return null

    val startingKilometers = allEntries
        .asSequence()
        .filter { it.fecha < requireNotNull(periodRange).startInclusive }
        .filter { it.km.isFinite() && it.km >= 0.0 }
        .sortedWith(compareBy<FuelEntry> { it.fecha }.thenBy { it.id })
        .lastOrNull()
        ?.km
        ?: return null

    return (endingKilometers - startingKilometers)
        .takeIf { it.isFinite() && it > 0.0 }
}

/**
 * For the complete history, cost and distance must cover the same economic interval. The vehicle
 * creation odometer is not an economic reference because no registered cost is tied to it.
 */
private fun calculateAllPeriodEconomicDistance(validEconomicEntries: List<FuelEntry>): Double? {
    val odometers = validEconomicEntries
        .asSequence()
        .map { it.km }
        .filter { it.isFinite() && it >= 0.0 }
        .toList()

    if (odometers.size < 2) return null

    return (requireNotNull(odometers.maxOrNull()) - requireNotNull(odometers.minOrNull()))
        .takeIf { it.isFinite() && it > 0.0 }
}

private fun calculateMonthlyExpenses(
    entries: List<FuelEntry>,
    timeZone: TimeZone
): List<MonthlyExpensePoint> {
    val grouped = validEconomicEntries(entries)
        .groupBy { entry ->
            Calendar.getInstance(timeZone).apply { timeInMillis = entry.fecha }.let { calendar ->
                calendar.get(Calendar.YEAR) to calendar.get(Calendar.MONTH)
            }
        }

    return grouped
        .map { (yearMonth, monthlyEntries) ->
            MonthlyExpensePoint(
                year = yearMonth.first,
                month = yearMonth.second,
                totalCost = monthlyEntries.sumFinite { it.precio }
            )
        }
        .sortedWith(compareBy(MonthlyExpensePoint::year, MonthlyExpensePoint::month))
}

private fun compareMetric(
    current: Double,
    previous: Double,
    previousHasRecords: Boolean
): PeriodMetricComparison {
    if (!previousHasRecords) {
        return PeriodMetricComparison(PeriodComparisonStatus.NO_PREVIOUS_DATA)
    }
    if (previous == 0.0) {
        return PeriodMetricComparison(PeriodComparisonStatus.NO_COMPARABLE_BASE)
    }

    val difference = current - previous
    val percentage = (difference / previous * 100.0).takeIf { it.isFinite() }
    return PeriodMetricComparison(
        status = PeriodComparisonStatus.VALUE,
        difference = difference,
        percentage = percentage
    )
}

private fun calendarAtStartOf(year: Int, month: Int, timeZone: TimeZone): Calendar =
    Calendar.getInstance(timeZone).apply {
        clear()
        set(year, month, 1, 0, 0, 0)
    }

private fun List<FuelEntry>.sumFinite(value: (FuelEntry) -> Double): Double = fold(0.0) { total, entry ->
    (total + value(entry)).takeIf { it.isFinite() } ?: total
}
