package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectricChargePercentageTest {

    @Test
    fun validChargePercentages_haveACompactPresentation() {
        assertEquals("20 % → 80 %", electricChargePercentageRangeText(20.0, 80.0))
    }

    @Test
    fun historicalChargeWithoutPercentages_hasNoPresentation() {
        assertNull(electricChargePercentageRangeText(null, null))
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
    }
}
