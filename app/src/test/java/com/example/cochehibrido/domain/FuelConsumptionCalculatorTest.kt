package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelConsumptionCalculatorTest {

    @Test
    fun oneElectricCharge_returnsNoAverageConsumption() {
        assertEquals(0.0, calculateAverageElectricConsumption(listOf(electricEntry(100.0, 10.0))), 0.0)
    }

    @Test
    fun twoElectricCharges_createOneSegment() {
        assertEquals(
            20.0,
            calculateElectricSegments(listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0)))
                .single()
                .consumption,
            0.0
        )
    }

    @Test
    fun threeElectricCharges_calculateWeightedAverageFromSegments() {
        assertEquals(
            17.5,
            calculateAverageElectricConsumption(
                listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0), electricEntry(300.0, 15.0))
            ),
            0.0
        )
    }

    @Test
    fun equalKilometers_doNotCreateASegment() {
        assertEquals(
            0,
            calculateElectricSegmentCount(listOf(electricEntry(100.0, 10.0), electricEntry(100.0, 20.0)))
        )
    }

    @Test
    fun decreasingInputOrder_isSortedByKilometers() {
        assertEquals(
            20.0,
            calculateAverageElectricConsumption(listOf(electricEntry(200.0, 20.0), electricEntry(100.0, 10.0))),
            0.0
        )
    }

    @Test
    fun zeroEnergyEntry_isIgnored() {
        assertEquals(
            10.0,
            calculateAverageElectricConsumption(
                listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 0.0), electricEntry(300.0, 20.0))
            ),
            0.0
        )
    }

    @Test
    fun negativeEnergyEntry_isIgnored() {
        assertEquals(
            10.0,
            calculateAverageElectricConsumption(
                listOf(electricEntry(100.0, 10.0), electricEntry(200.0, -1.0), electricEntry(300.0, 20.0))
            ),
            0.0
        )
    }

    @Test
    fun nanEnergyEntry_isIgnored() {
        assertEquals(
            10.0,
            calculateAverageElectricConsumption(
                listOf(electricEntry(100.0, 10.0), electricEntry(200.0, Double.NaN), electricEntry(300.0, 20.0))
            ),
            0.0
        )
    }

    @Test
    fun infiniteEnergyEntry_isIgnored() {
        assertEquals(
            10.0,
            calculateAverageElectricConsumption(
                listOf(electricEntry(100.0, 10.0), electricEntry(200.0, Double.POSITIVE_INFINITY), electricEntry(300.0, 20.0))
            ),
            0.0
        )
    }

    @Test
    fun averageUsesTheSameSegmentsAsTheSegmentList() {
        val entries = listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0), electricEntry(300.0, 15.0))

        assertEquals(17.5, calculateAverageElectricConsumption(entries), 0.0)
    }

    @Test
    fun bestConsumptionUsesTheSameSegmentsAsTheSegmentList() {
        val entries = listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0), electricEntry(300.0, 15.0))

        assertEquals(15.0, calculateBestElectricConsumption(entries), 0.0)
    }

    @Test
    fun worstConsumptionUsesTheSameSegmentsAsTheSegmentList() {
        val entries = listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0), electricEntry(300.0, 15.0))

        assertEquals(20.0, calculateWorstElectricConsumption(entries), 0.0)
    }

    @Test
    fun segmentCountUsesTheSameSegmentsAsTheSegmentList() {
        val entries = listOf(electricEntry(100.0, 10.0), electricEntry(200.0, 20.0), electricEntry(300.0, 15.0))

        assertEquals(2, calculateElectricSegmentCount(entries))
    }

    private fun electricEntry(km: Double, quantity: Double) = FuelEntry(
        fecha = km.toLong(),
        cantidad = quantity,
        precio = 0.0,
        tipo = FuelType.ELECTRICO,
        km = km
    )
}
