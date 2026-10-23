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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedSelectionsTest {
    @Test fun fuelFilterRoundTrip() {
        val filter = StationFilter(StationFuelType.DIESEL, "MADRID", "Madrid", StationSortOrder.DISTANCE)
        assertEquals(filter, restoredStationFilter(filter.savedValues()))
    }

    @Test fun chargerFilterRoundTrip() {
        val filter = ChargerFilter(ChargerConnectorType.CCS_COMBO_2, 100.0, "Operador", "Madrid", "Madrid", ChargerSortOrder.NAME)
        assertEquals(filter, restoredChargerFilter(filter.savedValues()))
    }

    @Test fun missingSelectionsUseDefaults() {
        assertEquals(StationFilter(), restoredStationFilter(null))
        assertEquals(ChargerFilter(), restoredChargerFilter(null))
        assertEquals(currentPeriod(StatisticsPeriodMode.MONTH), restoredStatisticsPeriod(null))
    }

    @Test fun municipalityWithoutProvinceIsDiscarded() {
        assertNull(restoredStationFilter(StationFilter(municipality = "Madrid").savedValues()).municipality)
        assertNull(restoredChargerFilter(ChargerFilter(municipality = "Madrid").savedValues()).municipality)
    }

    @Test fun invalidEnumsUseSafeDefaults() {
        assertEquals(StationFilter(), restoredStationFilter(listOf("removed", "", "", "removed")))
        assertEquals(ChargerFilter(), restoredChargerFilter(listOf("removed", "", "", "", "", "removed")))
    }

    @Test fun invalidPowerIsDiscarded() {
        listOf("NaN", "Infinity", "-1", "invalid").forEach { power ->
            assertNull(restoredChargerFilter(listOf("", power, "", "Madrid", "", "POWER")).minimumPowerKw)
        }
    }

    @Test fun incompatibleSavedShapeUsesDefaults() {
        assertEquals(StationFilter(), restoredStationFilter(listOf("old")))
        assertEquals(ChargerFilter(), restoredChargerFilter(emptyList()))
    }

    @Test fun monthAndYearRoundTrip() {
        listOf(StatisticsPeriod.Month(2020, 11), StatisticsPeriod.Month(2021, 0), StatisticsPeriod.Year(2019)).forEach {
            assertEquals(it, restoredStatisticsPeriod(it.savedValues()))
        }
    }

    @Test fun allTimeRoundTrip() {
        assertEquals(StatisticsPeriod.All, restoredStatisticsPeriod(StatisticsPeriod.All.savedValues()))
    }

    @Test fun malformedAndFuturePeriodsFallBackToCurrentMonth() {
        val current = currentPeriod(StatisticsPeriodMode.MONTH)
        listOf(listOf("MONTH", "2020", "12"), listOf("YEAR", "2147483647"), listOf("MONTH", "bad", "0"), listOf("unknown")).forEach {
            assertEquals(current, restoredStatisticsPeriod(it))
        }
    }
}
