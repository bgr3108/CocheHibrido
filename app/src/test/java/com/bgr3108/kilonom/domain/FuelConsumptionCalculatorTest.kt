package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun oneFullFuelTank_returnsNoSegments() {
        assertEquals(0, calculateFuelSegmentCount(listOf(fuelEntry(100.0, 10.0))))
    }

    @Test
    fun twoFullFuelTanks_calculateTheSecondTankConsumption() {
        assertEquals(
            10.0,
            calculateFuelSegments(listOf(fuelEntry(100.0, 10.0), fuelEntry(200.0, 10.0)))
                .single()
                .consumption,
            0.0
        )
    }

    @Test
    fun equalMileageFullTank_becomesTheNewReferenceWithoutContaminatingTheNextSegment() {
        val entries = listOf(
            fuelEntry(100.0, 10.0, date = 1L, id = 1),
            fuelEntry(100.0, 10.0, date = 2L, id = 2),
            fuelEntry(200.0, 10.0, date = 3L, id = 3)
        )
        val segments = calculateFuelSegments(entries)

        assertEquals(10.0, calculateAverageFuelConsumption(entries), 0.0)
        assertEquals(10.0, calculateBestFuelConsumption(entries), 0.0)
        assertEquals(10.0, calculateWorstFuelConsumption(entries), 0.0)
        assertEquals(1, calculateFuelSegmentCount(entries))
        assertEquals(listOf(10.0), segments.map { it.consumption })
    }

    @Test
    fun partialFuelEntry_isAccumulatedUntilTheNextFullTank() {
        assertEquals(
            15.0,
            calculateAverageFuelConsumption(
                listOf(
                    fuelEntry(100.0, 10.0),
                    fuelEntry(150.0, 5.0, fullTank = false),
                    fuelEntry(200.0, 10.0)
                )
            ),
            0.0
        )
    }

    @Test
    fun multiplePartialFuelEntries_areAccumulatedInTheSameSegment() {
        assertEquals(
            15.0,
            calculateFuelSegments(
                listOf(
                    fuelEntry(100.0, 10.0),
                    fuelEntry(120.0, 2.0, fullTank = false),
                    fuelEntry(150.0, 3.0, fullTank = false),
                    fuelEntry(200.0, 10.0)
                )
            ).single().consumption,
            0.0
        )
    }

    @Test
    fun unorderedFuelEntries_areSortedByMileageDateAndId() {
        assertEquals(
            15.0,
            calculateAverageFuelConsumption(
                listOf(
                    fuelEntry(200.0, 10.0, date = 3L, id = 3),
                    fuelEntry(100.0, 10.0, date = 1L, id = 1),
                    fuelEntry(150.0, 5.0, fullTank = false, date = 2L, id = 2)
                )
            ),
            0.0
        )
    }

    @Test
    fun equalMileageFuelEntries_useDateAndIdAsStableOrder() {
        assertEquals(
            15.0,
            calculateAverageFuelConsumption(
                listOf(
                    fuelEntry(200.0, 10.0, date = 4L, id = 4),
                    fuelEntry(100.0, 5.0, fullTank = false, date = 3L, id = 3),
                    fuelEntry(100.0, 10.0, date = 2L, id = 2),
                    fuelEntry(100.0, 10.0, date = 1L, id = 1)
                )
            ),
            0.0
        )
    }

    @Test
    fun invalidFuelQuantities_doNotContaminateMetrics() {
        val entries = listOf(
            fuelEntry(100.0, 10.0),
            fuelEntry(120.0, 0.0, fullTank = false),
            fuelEntry(130.0, -1.0, fullTank = false),
            fuelEntry(140.0, Double.NaN, fullTank = false),
            fuelEntry(150.0, Double.POSITIVE_INFINITY, fullTank = false),
            fuelEntry(200.0, 10.0)
        )

        assertEquals(10.0, calculateAverageFuelConsumption(entries), 0.0)
    }

    @Test
    fun invalidFuelKilometers_doNotCreateNonFiniteMetrics() {
        val entries = listOf(
            fuelEntry(Double.NaN, 10.0),
            fuelEntry(-1.0, 10.0),
            fuelEntry(100.0, 10.0),
            fuelEntry(Double.POSITIVE_INFINITY, 10.0),
            fuelEntry(200.0, 10.0)
        )

        assertTrue(listOf(
            calculateAverageFuelConsumption(entries),
            calculateBestFuelConsumption(entries),
            calculateWorstFuelConsumption(entries)
        ).all { it.isFinite() })
    }

    @Test
    fun fuelMetrics_areDerivedFromTheSameValidSegments() {
        val entries = listOf(
            fuelEntry(100.0, 10.0),
            fuelEntry(200.0, 10.0),
            fuelEntry(300.0, 15.0)
        )
        val segments = calculateFuelSegments(entries)

        assertEquals(12.5, calculateAverageFuelConsumption(entries), 0.0)
        assertEquals(10.0, calculateBestFuelConsumption(entries), 0.0)
        assertEquals(15.0, calculateWorstFuelConsumption(entries), 0.0)
        assertEquals(segments.size, calculateFuelSegmentCount(entries))
        assertEquals(listOf(10.0, 15.0), segments.map { it.consumption })
    }

    @Test
    fun fullTankThenPartial_createsACurrentEstimatedConsumption() {
        val estimate = calculateCurrentEstimatedFuelConsumption(
            entries = listOf(
                fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                fuelEntry(200.0, 5.0, fullTank = false, fuelLevelAfter = 0.75)
            ),
            tankCapacity = 40.0
        )

        assertEquals(15.0, requireNotNull(estimate).consumption, 0.0)
        assertTrue(calculateFuelSegments(listOf(
            fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
            fuelEntry(200.0, 5.0, fullTank = false, fuelLevelAfter = 0.75)
        )).isEmpty())
    }

    @Test
    fun severalPartials_createOneAccumulatedCurrentEstimate() {
        val estimate = calculateCurrentEstimatedFuelConsumption(
            listOf(
                fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                fuelEntry(150.0, 5.0, fullTank = false, fuelLevelAfter = 0.875),
                fuelEntry(200.0, 7.0, fullTank = false, fuelLevelAfter = 0.75)
            ),
            40.0
        )

        assertEquals(22.0, requireNotNull(estimate).consumption, 0.0)
    }

    @Test
    fun nextFullTank_closesTheRealSegmentAndRemovesTheCurrentEstimate() {
        val entries = listOf(
            fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
            fuelEntry(150.0, 5.0, fullTank = false, fuelLevelAfter = 0.75),
            fuelEntry(200.0, 10.0, fuelLevelAfter = 1.0)
        )

        assertEquals(15.0, calculateFuelSegments(entries).single().consumption, 0.0)
        assertEquals(null, calculateCurrentEstimatedFuelConsumption(entries, 40.0))
    }

    @Test
    fun currentEstimate_doesNotChangeHistoricalFuelStatistics() {
        val entries = listOf(
            fuelEntry(0.0, 10.0, fuelLevelAfter = 1.0),
            fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
            fuelEntry(200.0, 5.0, fullTank = false, fuelLevelAfter = 0.5)
        )

        assertEquals(10.0, calculateAverageFuelConsumption(entries), 0.0)
        assertEquals(10.0, calculateBestFuelConsumption(entries), 0.0)
        assertEquals(10.0, calculateWorstFuelConsumption(entries), 0.0)
        assertEquals(1, calculateFuelSegmentCount(entries))
        assertEquals(25.0, requireNotNull(calculateCurrentEstimatedFuelConsumption(entries, 40.0)).consumption, 0.0)
    }

    @Test
    fun historicalPartialWithoutTankLevel_doesNotCreateCurrentEstimate() {
        assertEquals(
            null,
            calculateCurrentEstimatedFuelConsumption(
                listOf(
                    fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                    fuelEntry(200.0, 5.0, fullTank = false, fuelLevelAfter = null)
                ),
                40.0
            )
        )
    }

    @Test
    fun incoherentTankLevel_doesNotCreateCurrentEstimate() {
        assertEquals(
            null,
            calculateCurrentEstimatedFuelConsumption(
                listOf(
                    fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                    fuelEntry(200.0, 10.0, fullTank = false, fuelLevelAfter = 0.125)
                ),
                40.0
            )
        )
    }

    @Test
    fun unsupportedTankLevel_doesNotCreateCurrentEstimate() {
        assertEquals(
            null,
            calculateCurrentEstimatedFuelConsumption(
                listOf(
                    fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                    fuelEntry(200.0, 10.0, fullTank = false, fuelLevelAfter = 0.6)
                ),
                40.0
            )
        )
    }

    @Test
    fun missingTankCapacity_doesNotCreateCurrentEstimate() {
        assertEquals(
            null,
            calculateCurrentEstimatedFuelConsumption(
                listOf(
                    fuelEntry(100.0, 10.0, fuelLevelAfter = 1.0),
                    fuelEntry(200.0, 5.0, fullTank = false, fuelLevelAfter = 0.75)
                ),
                0.0
            )
        )
    }

    private fun electricEntry(km: Double, quantity: Double) = FuelEntry(
        fecha = km.toLong(),
        cantidad = quantity,
        precio = 0.0,
        tipo = FuelType.ELECTRICO,
        km = km
    )

    private fun fuelEntry(
        km: Double,
        quantity: Double,
        fullTank: Boolean = true,
        fuelLevelAfter: Double? = null,
        date: Long = km.toLong(),
        id: Int = 0
    ) = FuelEntry(
        id = id,
        fecha = date,
        cantidad = quantity,
        precio = 0.0,
        tipo = FuelType.GASOLINA,
        km = km,
        fullTank = fullTank,
        fuelLevelAfter = fuelLevelAfter
    )
}
