package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
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
    fun physicalRecordRequiresDate_butKilometresAreOptional() {
        assertEquals("Introduce la fecha del mantenimiento", validate(includeRecord = true))
        assertNull(validate(includeRecord = true, recordDate = 1L))
        assertNull(validate(includeRecord = true, recordDate = 1L, recordKm = "42000"))
        assertNull(validate(type = MaintenanceType.ITV, document = true, includeRecord = true, recordDate = 1L))
    }

    @Test
    fun optionalKilometres_keepEmptyValuesNull_andRejectInvalidValues() {
        assertNull(optionalOdometerKm(""))
        assertNull(optionalOdometerKm("  "))
        assertEquals(42_000L, optionalOdometerKm("42000"))
        assertEquals("El kilometraje no es válido", validate(includeRecord = true, recordDate = 1L, recordKm = "-1"))
        assertEquals("El kilometraje no es válido", validate(includeRecord = true, recordDate = 1L, recordKm = "999999999999999999999999"))
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
    fun nextDueKilometresAreValidWhenThePerformedOdometerIsMissing() {
        assertNull(validate(includeRecord = true, recordDate = 1L, hasDueKm = true, dueKm = "50000"))
    }

    @Test
    fun historyDetailsOmitKilometresWhenTheRecordHasNoOdometer() {
        val details = MaintenanceRecordEntity(
            itemId = 1,
            performedDate = 1L,
            odometerKm = null,
            cost = 95.0,
            createdAt = 1L,
            updatedAt = 1L
        ).detailsText().orEmpty()

        assertFalse(details.contains("km"))
        assertFalse(details.contains("null"))
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
