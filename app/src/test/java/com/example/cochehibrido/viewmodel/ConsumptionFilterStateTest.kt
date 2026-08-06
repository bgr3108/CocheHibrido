package com.example.cochehibrido.viewmodel

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ConsumptionFilterStateTest {

    private val referenceCalendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.AUGUST, 15, 12, 0)
    }

    @Test
    fun allFilters_returnsTheOriginalHistory() {
        val entries = listOf(entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 1))

        assertSame(entries, filterConsumptionEntries(entries, ConsumptionFilterState(), referenceCalendar))
    }

    @Test
    fun gasolineFilter_returnsOnlyGasolineEntries() {
        val entries = listOf(
            entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 1),
            entry(FuelType.ELECTRICO, 2026, Calendar.AUGUST, 2)
        )

        assertEquals(
            listOf(FuelType.GASOLINA),
            filterConsumptionEntries(
                entries,
                ConsumptionFilterState(energyFilter = EnergyFilter.GASOLINE),
                referenceCalendar
            ).map { it.tipo }
        )
    }

    @Test
    fun electricFilter_returnsOnlyElectricEntries() {
        val entries = listOf(
            entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 1),
            entry(FuelType.ELECTRICO, 2026, Calendar.AUGUST, 2)
        )

        assertEquals(
            listOf(FuelType.ELECTRICO),
            filterConsumptionEntries(
                entries,
                ConsumptionFilterState(energyFilter = EnergyFilter.ELECTRIC),
                referenceCalendar
            ).map { it.tipo }
        )
    }

    @Test
    fun thisMonthFilter_includesOnlyCurrentMonth() {
        val entries = listOf(
            entry(FuelType.GASOLINA, 2026, Calendar.JULY, 31),
            entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 1),
            entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 31)
        )

        assertEquals(
            2,
            filterConsumptionEntries(
                entries,
                ConsumptionFilterState(dateFilter = DateFilter.THIS_MONTH),
                referenceCalendar
            ).size
        )
    }

    @Test
    fun lastMonthFilter_includesOnlyPreviousMonth() {
        val entries = listOf(
            entry(FuelType.GASOLINA, 2026, Calendar.JUNE, 30),
            entry(FuelType.GASOLINA, 2026, Calendar.JULY, 1),
            entry(FuelType.GASOLINA, 2026, Calendar.AUGUST, 1)
        )

        assertEquals(
            1,
            filterConsumptionEntries(
                entries,
                ConsumptionFilterState(dateFilter = DateFilter.LAST_MONTH),
                referenceCalendar
            ).size
        )
    }

    @Test
    fun thisYearFilter_excludesPreviousYears() {
        val entries = listOf(
            entry(FuelType.GASOLINA, 2025, Calendar.DECEMBER, 31),
            entry(FuelType.GASOLINA, 2026, Calendar.JANUARY, 1)
        )

        assertEquals(
            1,
            filterConsumptionEntries(
                entries,
                ConsumptionFilterState(dateFilter = DateFilter.THIS_YEAR),
                referenceCalendar
            ).size
        )
    }

    private fun entry(type: FuelType, year: Int, month: Int, day: Int) = FuelEntry(
        fecha = Calendar.getInstance().apply {
            clear()
            set(year, month, day, 12, 0)
        }.timeInMillis,
        cantidad = 1.0,
        precio = 1.0,
        tipo = type,
        km = 0.0
    )
}
