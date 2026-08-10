package com.bgr3108.kilonom.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtensionsTest {

    @Test
    fun storedWholeKilometersAreShownWithoutDecimalPartWhenEditing() {
        assertEquals("25060", 25060.0.toKilometersInput())
    }

    @Test
    fun validWholeKilometersAreAcceptedForSaving() {
        assertEquals(25060.0, "25060".toKilometersOrNull())
    }

    @Test
    fun decimalAndSignedKilometerInputIsRejected() {
        assertNull("25060,0".toKilometersOrNull())
        assertNull("25060.0".toKilometersOrNull())
        assertNull("-1".toKilometersOrNull())
    }

    @Test
    fun historicalFractionalKilometersAreNotDisplayedAsDecimal() {
        assertEquals("", 25060.5.toKilometersInput())
        assertEquals("—", 25060.5.toKilometersDisplay())
    }
}
