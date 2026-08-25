package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MaintenanceTrackingKeyTest {

    @Test
    fun predefinedAndTyreKeys_areDeterministic() {
        assertEquals(
            "OIL_AND_FILTER",
            createMaintenanceTrackingKey(MaintenanceType.OIL_AND_FILTER, null, null)
        )
        assertEquals(
            "TYRES:FRONT",
            createMaintenanceTrackingKey(MaintenanceType.TYRES, TyrePosition.FRONT, null)
        )
    }

    @Test
    fun customNames_normalizeWhitespaceCaseAndAccents() {
        val expected = "OTHER:BUJIAS"
        assertEquals(expected, createMaintenanceTrackingKey(MaintenanceType.OTHER, null, "Bujías"))
        assertEquals(expected, createMaintenanceTrackingKey(MaintenanceType.OTHER, null, " bujías "))
        assertEquals(expected, createMaintenanceTrackingKey(MaintenanceType.OTHER, null, "BUJÍAS"))
    }

    @Test
    fun other_requiresNameAndTyresRequirePosition() {
        assertThrows(IllegalArgumentException::class.java) {
            createMaintenanceTrackingKey(MaintenanceType.OTHER, null, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            createMaintenanceTrackingKey(MaintenanceType.TYRES, null, null)
        }
    }
}
