package com.bgr3108.kilonom.domain

/** The period selected in the statistics summary. Months use Calendar's zero-based month values. */
sealed interface StatisticsPeriod {
    val mode: StatisticsPeriodMode

    data class Month(val year: Int, val month: Int) : StatisticsPeriod {
        override val mode = StatisticsPeriodMode.MONTH
    }

    data class Year(val year: Int) : StatisticsPeriod {
        override val mode = StatisticsPeriodMode.YEAR
    }

    data object All : StatisticsPeriod {
        override val mode = StatisticsPeriodMode.ALL
    }
}

enum class StatisticsPeriodMode {
    MONTH,
    YEAR,
    ALL
}

data class PeriodRange(
    val startInclusive: Long,
    val endExclusive: Long
)

data class EnergyPeriodSummary(
    val totalCost: Double,
    val quantity: Double,
    val averagePrice: Double?
)

data class MonthlyExpensePoint(
    val year: Int,
    val month: Int,
    val totalCost: Double
)

data class PeriodSummary(
    val period: StatisticsPeriod,
    val entryCount: Int,
    val validEconomicEntryCount: Int,
    val totalCost: Double,
    val fuel: EnergyPeriodSummary,
    val electric: EnergyPeriodSummary,
    val distanceKilometers: Double?,
    val costPerKilometer: Double?,
    val monthlyExpenses: List<MonthlyExpensePoint>
) {
    val hasRecords: Boolean
        get() = entryCount > 0

    val hasEconomicData: Boolean
        get() = validEconomicEntryCount > 0
}

enum class PeriodComparisonStatus {
    NO_PREVIOUS_DATA,
    NO_COMPARABLE_BASE,
    VALUE
}

data class PeriodMetricComparison(
    val status: PeriodComparisonStatus,
    val difference: Double? = null,
    val percentage: Double? = null
)

data class PeriodComparison(
    val totalCost: PeriodMetricComparison,
    val distance: PeriodMetricComparison?
)
