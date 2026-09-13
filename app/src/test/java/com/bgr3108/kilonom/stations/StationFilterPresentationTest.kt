package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationFilterPresentationTest {
    @Test
    fun defaultFilters_haveACompactSummaryAndAreNotMarkedAsActive() {
        val filter = StationFilter()

        assertTrue(filter.isDefault())
        assertEquals("Gasolina 95 · Todas las provincias", filter.summary())
    }

    @Test
    fun changedFilters_areMarkedAsActiveAndSummarizeTheChosenArea() {
        val filter = StationFilter(
            fuelType = StationFuelType.DIESEL,
            province = "LAS PALMAS",
            municipality = "TELDE",
            sortOrder = StationSortOrder.DISTANCE
        )

        assertFalse(filter.isDefault())
        assertEquals("Diésel · LAS PALMAS · TELDE · Más cercanas", filter.summary())
    }

    @Test
    fun resetRestoresTheOriginalPresentationDefaults() {
        val reset = StationFilter()

        assertEquals(StationFuelType.GASOLINE_95, reset.fuelType)
        assertEquals(StationSortOrder.PRICE, reset.sortOrder)
        assertEquals(null, reset.province)
        assertEquals(null, reset.municipality)
    }
}
