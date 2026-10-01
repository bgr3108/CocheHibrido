package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectricChargePercentageTest {

    @Test
    fun validChargePercentages_haveACompactPresentation() {
        assertEquals("20 % → 80 %", electricChargePercentageRangeText(20.0, 80.0))
        assertEquals("20 % → 80 % (+60 %)", electricChargePercentageSummaryText(20.0, 80.0))
    }

    @Test
    fun historicalChargeWithoutPercentages_hasNoPresentation() {
        assertNull(electricChargePercentageRangeText(null, null))
        assertNull(electricChargePercentageSummaryText(null, null))
    }

    @Test
    fun savedPercentages_areFormattedForEditing() {
        assertEquals("20", 20.0.toPercentageInputText())
        assertEquals("80,5", 80.5.toPercentageInputText())
    }

    @Test
    fun invalidPercentageRanges_areNotPresented() {
        assertNull(electricChargePercentageRangeText(80.0, 20.0))
        assertNull(electricChargePercentageRangeText(20.0, null))
        assertNull(electricChargePercentageSummaryText(80.0, 20.0))
    }

    @Test
    fun percentageChangesCalculateAmount_butStoredOrManualAmountsRemainUntouched() {
        assertEquals(
            12.5,
            requireNotNull(resolveElectricChargeAmount(
                amount = 9.8,
                origin = ElectricChargeAmountOrigin.PERCENTAGES,
                batteryCapacity = 25.0,
                startPercentage = 20.0,
                endPercentage = 70.0
            )),
            0.0
        )
        assertEquals(
            9.8,
            requireNotNull(resolveElectricChargeAmount(
                amount = 9.8,
                origin = ElectricChargeAmountOrigin.STORED,
                batteryCapacity = 25.0,
                startPercentage = 20.0,
                endPercentage = 70.0
            )),
            0.0
        )
        assertEquals(
            10.0,
            requireNotNull(resolveElectricChargeAmount(
                amount = 10.0,
                origin = ElectricChargeAmountOrigin.MANUAL,
                batteryCapacity = 25.0,
                startPercentage = 20.0,
                endPercentage = 70.0
            )),
            0.0
        )
    }

    @Test
    fun incompleteOrInvalidPercentages_doNotProduceAnAmount() {
        assertNull(
            resolveElectricChargeAmount(
                amount = 9.8,
                origin = ElectricChargeAmountOrigin.PERCENTAGES,
                batteryCapacity = 25.0,
                startPercentage = null,
                endPercentage = 70.0
            )
        )
    }
}
