package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceFormValidationTest {

    @Test
    fun typeMustBeExplicitlySelectedForANewMaintenance() {
        assertEquals("Selecciona un tipo de mantenimiento", validateSelectedMaintenanceType(null))
        assertNull(validateSelectedMaintenanceType(MaintenanceType.OIL_AND_FILTER))
    }

    @Test
    fun upcomingItemAndHistoryRecordWithTheSameDatabaseId_haveDistinctLazyListKeys() {
        assertNotEquals(maintenanceUpcomingItemKey(1L), maintenanceHistoryRecordKey(1L))
    }

    @Test
    fun tyresRequirePosition_andOtherRequiresName() {
        assertEquals(
            "Selecciona la posición de los neumáticos",
            validate(type = MaintenanceType.TYRES)
        )
        assertEquals(
            "El nombre personalizado es obligatorio",
            validate(type = MaintenanceType.OTHER)
        )
    }

    @Test
    fun physicalRecordRequiresDateAndKilometres_butDocumentsDoNotRequireKilometres() {
        assertEquals("Introduce la fecha del mantenimiento", validate(includeRecord = true))
        assertEquals("Introduce el kilometraje del mantenimiento", validate(includeRecord = true, recordDate = 1L))
        assertNull(validate(type = MaintenanceType.ITV, document = true, includeRecord = true, recordDate = 1L))
    }

    @Test
    fun historicalRecordIsValid_andNextDueBeforeItsOdometerIsRejected() {
        assertNull(validate(includeRecord = true, recordDate = 1L, recordKm = "42000"))
        assertEquals(
            "El próximo kilometraje no puede ser anterior al mantenimiento realizado",
            validate(includeRecord = true, recordDate = 1L, recordKm = "42000", hasDueKm = true, dueKm = "41000")
        )
    }

    @Test
    fun overdueReminderBelowCurrentKmRemainsValidWhenNoRecordIsBeingRegistered() {
        assertNull(validate(hasDueKm = true, dueKm = "50000"))
    }

    private fun validate(
        type: MaintenanceType = MaintenanceType.BRAKES,
        document: Boolean = false,
        tyrePosition: String? = null,
        customName: String = "",
        includeRecord: Boolean = false,
        recordDate: Long? = null,
        recordKm: String = "",
        hasDueKm: Boolean = false,
        dueKm: String = ""
    ): String? = validateMaintenanceForm(
        type = type,
        tyrePosition = tyrePosition,
        customName = customName,
        includeRecord = includeRecord,
        documentType = document,
        recordDate = recordDate,
        recordKm = recordKm,
        cost = "",
        hasDueKm = hasDueKm,
        dueKm = dueKm,
        hasDueDate = false,
        dueDate = null
    )
}
